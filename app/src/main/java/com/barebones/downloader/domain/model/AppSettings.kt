package com.barebones.downloader.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class AudioFormat(val label: String) {
    ORIGINAL("Original audio"), MP3("MP3"), M4A("M4A/AAC"), OPUS("Opus"), FLAC("FLAC"), WAV("WAV")
}
@Serializable
enum class AudioQuality(val label: String) { BEST("Best available"), HIGH("High"), MEDIUM("Medium"), LOW("Low") }
@Serializable
enum class MetadataField(val label: String, val defaultEnabled: Boolean, val example: String) {
    TITLE("Title", true, "Come Together"), ARTIST("Artist", true, "The Beatles"),
    ALBUM("Album", true, "Abbey Road"), ALBUM_ARTIST("Album artist", true, "The Beatles"),
    TRACK_NUMBER("Track number", true, "1"), RELEASE_DATE("Release date", true, "1969"),
    GENRE("Genre", true, ""), ARTWORK("Artwork", true, "Illustrative placeholder"),
    SOURCE_URL("Source URL", false, ""), UPLOADER("Uploader", false, ""), DESCRIPTION("Description", false, "")
}
@Serializable
data class MetadataSettings(val enabled: Set<MetadataField> = MetadataField.entries.filter { it.defaultEnabled }.toSet())
@Serializable
data class AppSettings(
    val saveFolderUri: String? = null,
    val audioFormat: AudioFormat = AudioFormat.ORIGINAL,
    val audioQuality: AudioQuality = AudioQuality.BEST,
    val metadata: MetadataSettings = MetadataSettings(),
    val replayGainEnabled: Boolean = false,
)
fun previewTags(settings: MetadataSettings): List<Pair<String, String>> =
    MetadataField.entries.filter { it in settings.enabled }.map { it.label to it.example }
