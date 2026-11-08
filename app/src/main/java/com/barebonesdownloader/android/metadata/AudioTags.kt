package com.barebonesdownloader.android.metadata

import com.barebonesdownloader.android.download.*
import com.barebonesdownloader.android.domain.model.*
import com.barebonesdownloader.android.media.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import java.io.File

val gainKeys = setOf("replaygain_track_gain","replaygain_track_peak","replaygain_album_gain","replaygain_album_peak","r128_track_gain","r128_album_gain","itunnorm")
class AudioTags(private val runtime: NativeRuntime, private val log: DownloadLog) {
    suspend fun read(item: DownloadItem, file: File): Map<String,String> {
        return if (file.extension.lowercase() in setOf("webm","mka")) {
            val probe = YtDlpGateway(runtime, log).probe(item, file)
            val containers = listOfNotNull(probe["format"] as? JsonObject) + probe["streams"]?.jsonArray.orEmpty().map { it.jsonObject }
            canonicalTags(containers.flatMap { it["tags"]?.jsonObject.orEmpty().map { (k,v) -> k to v.jsonPrimitive.content } }.toMap())
        } else parse(runtime.execute(item.id, runtime.python, listOf(runtime.tagger.path, "read", file.path)).out)
    }
    suspend fun write(item: DownloadItem, input: File, output: File, tags: Map<String,String>, gain: Map<String,String> = emptyMap()): File {
        runtime.check(item.id); output.parentFile?.mkdirs()
        require(input.canonicalFile != output.canonicalFile)
        try {
            if (input.extension.lowercase() in setOf("webm","mka")) {
                val args = mutableListOf("-v","error","-nostdin","-y","-i",input.path,"-map","0:a:0","-vn","-map_metadata","-1","-map_metadata:s:a:0","-1","-c:a","copy")
                (tags + gain).forEach { (key,value) -> args += listOf("-metadata", "$key=$value", "-metadata:s:a:0", "$key=$value") }
                runtime.execute(item.id, runtime.ffmpeg, args + output.path)
            } else {
                input.copyTo(output, overwrite = true)
                val payload = File(output.parentFile, "${output.name}.tags.json")
                try {
                    payload.writeText(buildJsonObject {
                        put("tags", buildJsonObject { tags.forEach { (k,v) -> put(k,v) } })
                        put("replaygain", buildJsonObject { gain.forEach { (k,v) -> put(k,v) } })
                    }.toString())
                    runtime.execute(item.id, runtime.python, listOf(runtime.tagger.path, "write", output.path, payload.path))
                } finally { payload.delete() }
            }
            runtime.check(item.id)
            YtDlpGateway(runtime, log).validate(item, output)
            val actual = read(item, output)
            val expected = tags + gain
            val mismatches = expected.filter { (k,v) -> actual[k]?.let { normalizeTagValue(k,it) } != normalizeTagValue(k,v) }.keys +
                actual.keys.filter { (it in tagKeys.values || it in gainKeys) && it !in expected }
            require(mismatches.isEmpty()) { "Tag read-back failed for ${mismatches.joinToString()}. Original valid audio retained." }
            return output
        } catch (t: Throwable) { output.delete(); throw t }
    }
    private fun parse(value: String) = Json.parseToJsonElement(value).jsonObject.mapValues { it.value.jsonPrimitive.content }
}

data class TaggedAudio(val file: File, val warnings: List<String>)
class MetadataProcessor(private val runtime: NativeRuntime, private val log: DownloadLog) {
    suspend fun apply(item: DownloadItem, input: File, info: JsonObject, directory: File): TaggedAudio {
        if (item.kind != DownloadKind.AUDIO) return TaggedAudio(input, emptyList())
        directory.mkdirs()
        val warnings = mutableListOf<String>()
        val writer = AudioTags(runtime, log)
        var output = input
        var selected: Map<String,String> = emptyMap()
        try {
            val actual = writer.read(item, input)
            val resolved = mergeMissing(sourceTags(info, item.extractorKey.equals("youtube",true) || SourceUrls.isYoutube(item.sourceHost.orEmpty().lowercase())), actual.filterKeys { it in tagKeys.values })
            if (item.settingsSnapshot.metadata.enabled.isNotEmpty()) {
                if (listOf("title","artist","album").any { resolved[it].isNullOrBlank() }) {
                    val duration = YtDlpGateway(runtime, log).probe(item, input)["format"]?.jsonObject?.get("duration")?.jsonPrimitive?.doubleOrNull
                    mergeMissing(resolved, AppleCatalogue(runtime, log).lookup(item, resolved, duration)).let { resolved.putAll(it) }
                }
                if (!resolved.containsKey("album_artist")) resolved["artist"]?.let { resolved["album_artist"] = it }
            }
            selected = selectedTags(resolved, item.settingsSnapshot, input.extension)
            val missing = tagKeys.filterKeys { it in item.settingsSnapshot.metadata.enabled && tagSupported(actualFormat(input.extension),it) }.values.filter { it !in selected }
            if (missing.isNotEmpty()) warnings += "Unknown metadata left empty: ${missing.joinToString()}."
            output = writer.write(item, input, File(directory, "metadata.${input.extension}"), selected)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { warnings += "Metadata warning: ${sanitizeDiagnostic(e.message.orEmpty())}" }
        if (item.settingsSnapshot.replayGainEnabled) {
            try {
                // Always measure the final encoded samples, after the cap and tag pass.
                val gain = ReplayGain(runtime).analyse(item, output, directory)
                val existing = writer.read(item, output).filterKeys { it in tagKeys.values }
                output = writer.write(item, output, File(directory, "replaygain.${input.extension}"), existing, gain.forExtension(output.extension))
                log.add("Verified per-track ReplayGain 2.0 gain/peak; encoded audio samples unchanged.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { warnings += "ReplayGain warning: ${sanitizeDiagnostic(e.message.orEmpty())}" }
        }
        warnings.forEach(log::add)
        return TaggedAudio(output, warnings)
    }
}
