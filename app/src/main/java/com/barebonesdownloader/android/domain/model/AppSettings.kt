package com.barebonesdownloader.android.domain.model

import kotlinx.serialization.Serializable

@Serializable enum class DownloadKind(val label: String) { AUDIO("Audio only"), VIDEO("Video + audio") }
@Serializable enum class AppThemeMode(val label: String) { LIGHT("Light"), DARK("Dark") }
@Serializable enum class AudioFormat(val label: String, val variable: Boolean = false, val bitrate: Boolean = false) {
    ORIGINAL("Best available (source format)"), AAC("AAC (AAC-in-M4A)", true, true), ALAC("ALAC (ALAC-in-M4A)"),
    FLAC("FLAC (.flac)"), M4A("M4A (AAC-in-M4A)", true, true), MP3("MP3 (.mp3)", true, true),
    OPUS("Opus (.opus)", false, true), VORBIS("Vorbis (.ogg)", true, true), WAV("WAV (PCM, .wav)")
}
@Serializable enum class QualityMode(val label: String) { DEFAULT("Default / compatible stream copy"), VARIABLE("Variable quality"), BITRATE("Bitrate") }
@Serializable enum class MetadataField(val label: String, val example: String) {
    TITLE("Title", "Here Comes the Sun"), ARTIST("Artist", "The Beatles"), ALBUM("Album", "Abbey Road"),
    ALBUM_ARTIST("Album artist", "The Beatles"), RELEASE_DATE("Release date/year", "26 September 1969"),
    TRACK_NUMBER("Track number", "7"), DISC_NUMBER("Disc number", ""), GENRE("Genre", ""),
    COMPOSER("Composer", "George Harrison"), SOURCE_URL("Source URL", ""), DESCRIPTION("Description/comment", "")
}
@Serializable data class MetadataSettings(val enabled: Set<MetadataField> = MetadataField.entries.toSet())
@Serializable data class AppSettings(
    val audioFolderUri: String? = null, val audioFolderName: String? = null,
    val videoFolderUri: String? = null, val videoFolderName: String? = null,
    val theme: AppThemeMode = AppThemeMode.LIGHT,
    val audioFormat: AudioFormat = AudioFormat.ORIGINAL,
    val qualityMode: QualityMode = QualityMode.DEFAULT,
    val variableQuality: Int = 5, val bitrateKbps: Int = 192,
    val audioMaxSizeMb: Int = 0, val videoMaxSizeMb: Int = 0, val videoMaxHeight: Int = 0,
    val appendSourceUrl: Boolean = false, val metadata: MetadataSettings = MetadataSettings(),
    val replayGainEnabled: Boolean = true,
) {
    fun folder(kind: DownloadKind) = if (kind == DownloadKind.AUDIO) audioFolderUri else videoFolderUri
    fun folderName(kind: DownloadKind) = if (kind == DownloadKind.AUDIO) audioFolderName else videoFolderName
    fun withFolder(kind: DownloadKind, uri: String, name: String) = if (kind == DownloadKind.AUDIO)
        copy(audioFolderUri = uri, audioFolderName = name) else copy(videoFolderUri = uri, videoFolderName = name)
    fun normalized(): AppSettings = copy(
        variableQuality = variableQuality.takeIf { it in 0..10 } ?: 5,
        bitrateKbps = bitrateKbps.takeIf { it in 32..512 } ?: 192,
        audioMaxSizeMb = audioMaxSizeMb.takeIf { it in 0..16384 } ?: 0,
        videoMaxSizeMb = videoMaxSizeMb.takeIf { it in 0..16384 } ?: 0,
        videoMaxHeight = videoMaxHeight.takeIf { it in videoHeights } ?: 0,
        qualityMode = qualityMode.takeIf { it == QualityMode.DEFAULT || it == QualityMode.VARIABLE && audioFormat.variable || it == QualityMode.BITRATE && audioFormat.bitrate } ?: QualityMode.DEFAULT,
    )
}
val videoHeights = listOf(0, 2160, 1440, 1080, 720, 480, 360)
fun tagSupported(format: AudioFormat, field: MetadataField): Boolean = when {
    format in setOf(AudioFormat.AAC, AudioFormat.ALAC, AudioFormat.M4A) && field == MetadataField.SOURCE_URL -> false
    format == AudioFormat.WAV && field in setOf(MetadataField.COMPOSER, MetadataField.SOURCE_URL, MetadataField.ALBUM_ARTIST) -> false
    else -> true
}
fun previewTags(settings: AppSettings): List<Pair<String, String>> = MetadataField.entries.map { field ->
    field.label to when {
        !tagSupported(settings.audioFormat, field) -> "Unavailable"
        field !in settings.metadata.enabled -> ""
        else -> field.example
    }
}
