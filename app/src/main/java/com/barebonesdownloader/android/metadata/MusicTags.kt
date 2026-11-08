package com.barebonesdownloader.android.metadata

import com.barebonesdownloader.android.domain.model.*
import kotlinx.serialization.json.*
import java.text.Normalizer

val tagKeys = MetadataField.entries.zip(listOf("title","artist","album","album_artist","date","track","disc","genre","composer","purl","comment")).toMap()
val tagAliases = mapOf("albumartist" to "album_artist", "tracknumber" to "track", "discnumber" to "disc", "year" to "date", "description" to "comment", "url" to "purl", "date_released" to "date")
fun canonicalTags(tags: Map<String, String>) = tags.entries.associate { (k,v) -> (tagAliases[k.lowercase()] ?: k.lowercase()) to v }
fun JsonObject.text(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
    val v = this[key]
    (when (v) { is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.distinct().joinToString(", ")
        is JsonPrimitive -> v.contentOrNull; else -> null })?.replace("\u0000", "")?.trim()?.takeIf { it.isNotEmpty() && it !in setOf("NA", "N/A") }
}
fun sourceTags(info: JsonObject, youtube: Boolean): Map<String,String> {
    val tags = mutableMapOf<String,String>()
    fun add(key: String, value: String?) { if (!value.isNullOrBlank()) tags.putIfAbsent(key, value) }
    // Explicit music fields only; ordinary uploader/title are not reliable song credits.
    add("title", info.text("track")); add("artist", info.text("artists","artist"))
    add("album", info.text("album")); add("album_artist", info.text("album_artists","album_artist"))
    add("date", info.text("release_date","release_year")); add("track", info.text("track_number")); add("disc", info.text("disc_number"))
    add("genre", info.text("genres","genre")); add("composer", info.text("composers","composer"))
    add("purl", info.text("webpage_url","original_url")); add("comment", info.text("description"))
    if (youtube) {
        val description = info.text("description").orEmpty()
        val topic = Regex("\\AProvided to YouTube by [^\\r\\n]+\\r?\\n\\s*\\r?\\n([^\\r\\n]+)\\r?\\n\\s*\\r?\\n([^\\r\\n]+)", RegexOption.IGNORE_CASE).find(description)
        if (topic != null) {
            val credits = topic.groupValues[1].split(" · ")
            if (credits.size >= 2) {
                add("title", credits.first().trim()); add("artist", credits.drop(1).distinct().joinToString(", "))
                val album = topic.groupValues[2].trim()
                if (!listOf("℗","©","Released on:","Auto-generated").any { album.startsWith(it, true) }) add("album", album)
            }
        }
        info.text("uploader")?.takeIf { it.endsWith(" - Topic", true) }?.let {
            add("artist", it.dropLast(8).trim()); add("title", info.text("title"))
        }
        Regex("(?im)^Released on: (\\d{4}-\\d{2}-\\d{2})").find(description)?.let { add("date", it.groupValues[1]) }
    }
    return tags
}
fun mergeMissing(vararg sources: Map<String,String>): MutableMap<String,String> = mutableMapOf<String,String>().also { out ->
    sources.forEach { source -> source.forEach { (key,value) -> if (value.isNotBlank()) out.putIfAbsent(key,value) } }
}
fun actualFormat(extension: String): AudioFormat = when(extension.lowercase()) {
    "m4a","mp4","aac" -> AudioFormat.M4A; "wav" -> AudioFormat.WAV; "mp3" -> AudioFormat.MP3
    "flac" -> AudioFormat.FLAC; "opus" -> AudioFormat.OPUS; "ogg" -> AudioFormat.VORBIS; else -> AudioFormat.ORIGINAL
}
fun selectedTags(available: Map<String,String>, settings: AppSettings, extension: String): Map<String,String> = tagKeys.filterKeys {
    it in settings.metadata.enabled && tagSupported(actualFormat(extension), it)
}.values.mapNotNull { key -> available[key]?.takeIf(String::isNotBlank)?.let { key to normalizeTagValue(key, it) } }.toMap()
fun normalizeTagValue(key: String, value: String): String = when {
    key == "date" && value.matches(Regex("\\d{8}")) -> "${value.take(4)}-${value.substring(4,6)}-${value.takeLast(2)}"
    key in setOf("track","disc") -> value.substringBefore('/').toIntOrNull()?.toString() ?: value
    else -> value
}
fun normalizedMusic(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase().filter(Char::isLetterOrDigit)
data class CatalogueTrack(val tags: Map<String,String>, val duration: Double?)
fun selectCatalogue(candidates: List<CatalogueTrack>, title: String, artist: String, duration: Double?): Map<String,String> {
    if (title.isBlank() || artist.isBlank() || duration == null || !duration.isFinite() || duration <= 0) return emptyMap()
    val matches = candidates.filter { normalizedMusic(it.tags["title"].orEmpty()) == normalizedMusic(title) &&
        normalizedMusic(it.tags["artist"].orEmpty()) == normalizedMusic(artist) && it.duration != null &&
        kotlin.math.abs(it.duration - duration) <= maxOf(2.0, duration * .01) }
    if (matches.isEmpty()) return emptyMap()
    fun consensus(key: String) = matches.map { it.tags[key] }.takeIf { list -> list.all { !it.isNullOrBlank() } && list.map { normalizedMusic(it!!) }.distinct().size == 1 }?.first()
    val out = mutableMapOf("title" to title, "artist" to artist)
    consensus("album")?.let { album ->
        out["album"] = album; out["album_artist"] = artist
        listOf("date","track","disc","genre").forEach { key -> consensus(key)?.let { out[key] = it } }
    }
    return out
}
