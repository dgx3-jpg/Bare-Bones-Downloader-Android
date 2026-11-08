package com.barebonesdownloader.android.media

import com.barebonesdownloader.android.domain.model.*
import com.barebonesdownloader.android.download.NativeRuntime
import com.barebonesdownloader.android.download.DownloadLog
import kotlinx.serialization.json.*
import java.io.File

class YtDlpGateway(private val runtime: NativeRuntime, private val log: DownloadLog) : MediaGateway {
    private val json = Json { ignoreUnknownKeys = true }
    private fun base(): List<String> = listOf(runtime.ytdlp.absolutePath, "--ignore-config", "--no-cache-dir", "--no-playlist", "--no-warnings",
        "--socket-timeout", "20", "--retries", "1", "--fragment-retries", "1", "--js-runtimes", "quickjs:${runtime.quickJs.absolutePath}")
    private fun validateInfo(info: JsonObject) {
        require(info["_type"]?.jsonPrimitive?.contentOrNull !in setOf("playlist", "multi_video") && info["entries"] == null) { "Only one accessible media item is supported; collections are rejected." }
        require(info["is_live"]?.jsonPrimitive?.booleanOrNull != true && info["live_status"]?.jsonPrimitive?.contentOrNull !in setOf("is_live", "is_upcoming", "post_live")) { "Live streams are not supported." }
        require(info["has_drm"]?.jsonPrimitive?.booleanOrNull != true) { "DRM-protected media is unsupported." }
        val availability = info["availability"]?.jsonPrimitive?.contentOrNull
        require(availability !in setOf("private", "premium_only", "subscriber_only", "needs_auth")) { "This source requires authentication; authenticated downloads are unsupported." }
    }
    suspend fun resolveSource(raw: String): com.barebonesdownloader.android.download.SourceReference = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val url = com.barebonesdownloader.android.download.SourceUrls.normalize(raw)
        if (com.barebonesdownloader.android.download.SourceUrls.isYoutube(java.net.URI(url).host)) return@withContext com.barebonesdownloader.android.download.SourceUrls.youtube(url)
        val id = java.util.UUID.randomUUID()
        val info = json.parseToJsonElement(runtime.execute(id, runtime.python, base() + listOf("--dump-single-json", "--skip-download", url), timeoutSeconds = 90).out).jsonObject
        validateInfo(info)
        val extractor = info["extractor_key"]?.jsonPrimitive?.contentOrNull ?: info["extractor"]?.jsonPrimitive?.contentOrNull ?: error("Missing extractor identity.")
        val mediaId = info["id"]?.jsonPrimitive?.contentOrNull ?: error("Missing media identity.")
        require(extractor.matches(Regex("[A-Za-z0-9_.:-]{1,100}")) && mediaId.isNotBlank() && mediaId.length <= 256 && mediaId.none(Char::isISOControl)) { "Invalid extracted identity." }
        val canonical = com.barebonesdownloader.android.download.SourceUrls.normalize(info["webpage_url"]?.jsonPrimitive?.contentOrNull ?: url)
        com.barebonesdownloader.android.download.SourceReference(canonical, extractor.lowercase(), mediaId)
    }
    override fun cancel(item: DownloadItem) = runtime.cancel(item.id)
    override suspend fun download(item: DownloadItem, directory: File, progress: (MediaProgress) -> Unit): StagedMedia {
        runtime.initialize()
        val inspect = runtime.execute(item.id, runtime.python, base() + listOf("--dump-single-json", "--skip-download", item.sourceUrl), timeoutSeconds = 90)
        val info = json.parseToJsonElement(inspect.out).jsonObject
        validateInfo(info)
        require(info["id"]?.jsonPrimitive?.content == item.mediaId) { "The extractor returned a different source identity." }
        val extractor = info["extractor_key"]?.jsonPrimitive?.contentOrNull ?: info["extractor"]?.jsonPrimitive?.contentOrNull
        require(item.extractorKey == null || extractor.equals(item.extractorKey, true)) { "The extractor returned a different source namespace." }
        val title = info["title"]?.jsonPrimitive?.contentOrNull ?: item.mediaId.orEmpty()
        directory.mkdirs()
        val selector = if (item.kind == DownloadKind.AUDIO) "bestaudio/best" else videoSelector(item.settingsSnapshot.videoMaxHeight, item.extractorKey.equals("youtube", true))
        val args = base() + listOf("-f", selector, "--newline", "--progress-template", "download:BBD_PROGRESS:%(progress._percent_str)s",
            "--fixup", "never", "--no-simulate", "--progress", "--continue", "--paths", directory.absolutePath, "-o", "source.%(ext)s",
            "--ffmpeg-location", runtime.ffmpeg.absolutePath, "--merge-output-format", "mp4", "--print", "after_move:filepath", item.sourceUrl)
        val result = runtime.execute(item.id, runtime.python, args) { line ->
            if (line.startsWith("BBD_PROGRESS:")) progress(MediaProgress(DownloadStatus.DOWNLOADING, line.substringAfter(':').trim().removeSuffix("%").toFloatOrNull()?.toInt()?.coerceIn(0, 100)))
        }
        runtime.check(item.id)
        val path = result.out.lineSequence().map(String::trim).filter { it.isNotEmpty() && !it.startsWith("BBD_PROGRESS:") }.lastOrNull()
            ?: error("The extractor did not report an output file.")
        val source = File(path).canonicalFile
        require(source.path.startsWith(directory.canonicalPath + File.separator) && source.isFile && source.length() > 0) { "The extractor output was not a valid staged file." }
        progress(MediaProgress(DownloadStatus.PROCESSING, null))
        val encoded = if (item.kind == DownloadKind.AUDIO) {
            val audio = extractAudio(item, source, directory)
            AudioConverter(runtime).convert(item, audio, File(directory, "encoding"))
        } else source
        if (item.kind == DownloadKind.VIDEO) validate(item, encoded)
        var warnings: List<String> = emptyList()
        val output = MediaSizeLimiter(runtime, log).apply(item, encoded, File(directory, "size")) { candidate ->
            if (item.kind == DownloadKind.AUDIO) progress(MediaProgress(DownloadStatus.TAGGING, null))
            val tagged = com.barebonesdownloader.android.metadata.MetadataProcessor(runtime, log).apply(item, candidate, info, File(directory, "tags"))
            warnings = tagged.warnings
            tagged.file
        }
        val cap = SizeBudget.bytes(if (item.kind == DownloadKind.AUDIO) item.settingsSnapshot.audioMaxSizeMb else item.settingsSnapshot.videoMaxSizeMb)
        if (!SizeBudget.fits(output.length(), cap)) throw SizeLimitImpossible()
        validate(item, output)
        log.add("Validated ${item.kind.label} output (${output.length()} bytes).")
        return StagedMedia(output, title, if (item.kind == DownloadKind.VIDEO) "video/mp4" else audioMime(output.extension),
            warnings.takeIf { it.isNotEmpty() }?.joinToString(" "))
    }
    private suspend fun extractAudio(item: DownloadItem, source: File, directory: File): File {
        val probe = probe(item, source)
        val audio = probe["streams"]?.jsonArray?.firstOrNull { it.jsonObject["codec_type"]?.jsonPrimitive?.content == "audio" }?.jsonObject
            ?: error("The source has no readable audio stream.")
        val hasVideo = probe["streams"]?.jsonArray?.any { it.jsonObject["codec_type"]?.jsonPrimitive?.content == "video" } == true
        if (!hasVideo) return source
        val codec = audio["codec_name"]?.jsonPrimitive?.content.orEmpty()
        val ext = when (codec) { "aac", "alac" -> "m4a"; "opus" -> "opus"; "vorbis" -> "ogg"; "mp3" -> "mp3"; "flac" -> "flac"; else -> "mka" }
        val output = File(directory, "audio.$ext")
        runtime.execute(item.id, runtime.ffmpeg, listOf("-hide_banner", "-loglevel", "error", "-nostdin", "-y", "-i", source.path, "-map", "0:a:0", "-vn", "-c:a", "copy", output.path))
        return output
    }
    suspend fun probe(item: DownloadItem, file: File): JsonObject = json.parseToJsonElement(runtime.execute(item.id, runtime.ffprobe,
        listOf("-v", "error", "-show_streams", "-show_format", "-of", "json", file.path)).out).jsonObject
    suspend fun validate(item: DownloadItem, file: File) {
        require(file.isFile && file.length() > 0) { "The staged output is empty or missing." }
        val details = probe(item, file)
        val streams = details["streams"]?.jsonArray?.map { it.jsonObject }.orEmpty()
        require(streams.any { it["codec_type"]?.jsonPrimitive?.content == "audio" }) { "The output has no readable audio stream." }
        if (item.kind == DownloadKind.VIDEO) {
            require(details["format"]?.jsonObject?.get("format_name")?.jsonPrimitive?.content.orEmpty().split(',').contains("mp4")) { "The video container is not MP4." }
            val limit = item.settingsSnapshot.videoMaxHeight
            require(limit == 0 || streams.filter { it["codec_type"]?.jsonPrimitive?.content == "video" && it["disposition"]?.jsonObject?.get("attached_pic")?.jsonPrimitive?.intOrNull != 1 }.all { (it["height"]?.jsonPrimitive?.intOrNull ?: Int.MAX_VALUE) <= limit }) { "The video exceeds the selected resolution ceiling." }
            require(file.extension.equals("mp4", true) && streams.any { it["codec_name"]?.jsonPrimitive?.content == "h264" &&
                it["codec_type"]?.jsonPrimitive?.content == "video" && it["disposition"]?.jsonObject?.get("attached_pic")?.jsonPrimitive?.intOrNull != 1 } &&
                streams.any { it["codec_type"]?.jsonPrimitive?.content == "audio" && it["codec_name"]?.jsonPrimitive?.content == "aac" }) {
                "The output must be MP4 with real H.264 video and AAC audio; cover art is not video."
            }
        }
    }
}
fun videoSelector(height: Int, youtube: Boolean = true): String {
    if (!youtube) {
        val ceiling = if (height > 0) "[height<=?$height]" else ""
        return "bestvideo[vcodec~=\"^(avc1|h264)\"][ext=mp4]$ceiling+bestaudio[acodec~=\"^(mp4a|aac)\"]/best[vcodec~=\"^(avc1|h264)\"][acodec~=\"^(mp4a|aac)\"][ext=mp4]$ceiling/best[ext=mp4]$ceiling"
    }
    val ceiling = if (height > 0) "[height<=$height]" else ""
    return "bestvideo[vcodec^=avc1][ext=mp4]$ceiling+bestaudio[acodec^=mp4a][ext=m4a]/best[vcodec^=avc1][acodec^=mp4a][ext=mp4]$ceiling"
}
fun audioMime(ext: String): String = when(ext.lowercase()) { "mp3" -> "audio/mpeg"; "m4a" -> "audio/mp4"; "aac" -> "audio/aac"; "flac" -> "audio/flac"; "wav" -> "audio/wav"; "opus", "ogg" -> "audio/ogg"; "webm" -> "audio/webm"; "mka" -> "audio/x-matroska"; else -> "application/octet-stream" }
