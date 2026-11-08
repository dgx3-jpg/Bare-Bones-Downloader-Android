package com.barebonesdownloader.android.media

import com.barebonesdownloader.android.domain.model.*
import com.barebonesdownloader.android.download.NativeRuntime
import kotlinx.serialization.json.*
import java.io.File

class AudioConverter(private val runtime: NativeRuntime) {
    private var encoders: Set<String>? = null
    suspend fun probe(item: DownloadItem, file: File): JsonObject = Json.parseToJsonElement(runtime.execute(item.id, runtime.ffprobe,
        listOf("-v", "error", "-show_streams", "-show_format", "-of", "json", file.path)).out).jsonObject
    suspend fun convert(item: DownloadItem, input: File, directory: File): File {
        val metadata = probe(item, input)
        val streams = metadata["streams"]?.jsonArray?.map { it.jsonObject }.orEmpty()
        val stream = streams.firstOrNull { it["codec_type"]?.jsonPrimitive?.content == "audio" } ?: error("No readable audio stream.")
        require(streams.none { it["codec_type"]?.jsonPrimitive?.content == "video" }) { "Audio conversion requires a staged audio-only file." }
        val codec = stream["codec_name"]?.jsonPrimitive?.content.orEmpty()
        val plan = encodingPlan(item.settingsSnapshot, codec, stream["channels"]?.jsonPrimitive?.intOrNull ?: 2) ?: return input
        if (plan.copy && input.extension.equals(plan.output.extension, true)) {
            validate(item, input, plan.output); return input
        }
        if (!plan.copy) {
            val available = encoders ?: runtime.execute(item.id, runtime.ffmpeg, listOf("-hide_banner", "-encoders")).out
                .lineSequence().mapNotNull { it.trim().split(Regex("\\s+"), limit = 3).getOrNull(1) }.toSet().also { encoders = it }
            require(plan.output.encoder in available) { "Missing FFmpeg encoder ${plan.output.encoder}. This format is blocked; choose another format or update the app." }
        }
        directory.mkdirs()
        val output = File(directory, "converted.${plan.output.extension}")
        require(output.canonicalFile != input.canonicalFile) { "Refusing to overwrite staged input." }
        runtime.execute(item.id, runtime.ffmpeg, listOf("-hide_banner", "-loglevel", "error", "-nostdin", "-y", "-i", input.path,
            "-map", "0:a:0", "-vn", "-map_metadata", "0") + plan.arguments + output.path)
        validate(item, output, plan.output)
        return output
    }
    private suspend fun validate(item: DownloadItem, file: File, expected: AudioOutput) {
        require(file.isFile && file.length() > 0) { "Encoded audio is empty or missing." }
        val probe = probe(item, file)
        val streams = probe["streams"]?.jsonArray?.map { it.jsonObject }.orEmpty()
        require(streams.any { it["codec_type"]?.jsonPrimitive?.content == "audio" && it["codec_name"]?.jsonPrimitive?.content == expected.codec } &&
            streams.none { it["codec_type"]?.jsonPrimitive?.content == "video" }) { "Output codec did not match the selected audio format." }
        val containers = probe["format"]?.jsonObject?.get("format_name")?.jsonPrimitive?.content.orEmpty().split(',')
        require(containers.any { it in expected.containers } && file.extension == expected.extension) { "Output container/extension did not match the selected format." }
        if (expected.codec == "pcm_s16le") require(streams.first { it["codec_type"]?.jsonPrimitive?.content == "audio" }["sample_fmt"]?.jsonPrimitive?.content == "s16") { "WAV must contain 16-bit PCM." }
    }
}
