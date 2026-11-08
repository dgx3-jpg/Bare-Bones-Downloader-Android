package com.barebonesdownloader.android.media

import com.barebonesdownloader.android.domain.model.*
import java.math.BigDecimal
import java.math.RoundingMode

data class AudioOutput(val encoder: String, val codec: String, val extension: String, val containers: Set<String>)
fun audioOutput(format: AudioFormat): AudioOutput? = when (format) {
    AudioFormat.ORIGINAL -> null
    AudioFormat.AAC, AudioFormat.M4A -> AudioOutput("aac", "aac", "m4a", setOf("mp4", "m4a", "mov"))
    AudioFormat.ALAC -> AudioOutput("alac", "alac", "m4a", setOf("mp4", "m4a", "mov"))
    AudioFormat.FLAC -> AudioOutput("flac", "flac", "flac", setOf("flac"))
    AudioFormat.MP3 -> AudioOutput("libmp3lame", "mp3", "mp3", setOf("mp3"))
    AudioFormat.OPUS -> AudioOutput("libopus", "opus", "opus", setOf("ogg"))
    AudioFormat.VORBIS -> AudioOutput("libvorbis", "vorbis", "ogg", setOf("ogg"))
    AudioFormat.WAV -> AudioOutput("pcm_s16le", "pcm_s16le", "wav", setOf("wav"))
}
data class EncodingPlan(val output: AudioOutput, val copy: Boolean, val arguments: List<String>)
fun encodingPlan(settings: AppSettings, sourceCodec: String, channels: Int = 2): EncodingPlan? {
    val format = settings.audioFormat
    val output = audioOutput(format) ?: return null
    val mode = settings.qualityMode
    require(mode != QualityMode.VARIABLE || format.variable) { "Variable quality is unsupported for ${format.label}." }
    require(mode != QualityMode.BITRATE || format.bitrate) { "Explicit bitrate is unsupported for ${format.label}." }
    require(settings.variableQuality in 0..10 && settings.bitrateKbps in 32..512) { "Invalid encoding quality/bitrate. Review Audio settings." }
    val copy = mode == QualityMode.DEFAULT && sourceCodec == output.codec
    if (copy) return EncodingPlan(output, true, listOf("-c:a", "copy"))
    val args = mutableListOf("-c:a", output.encoder)
    when (mode) {
        QualityMode.DEFAULT -> Unit
        QualityMode.BITRATE -> {
            require(format != AudioFormat.MP3 || settings.bitrateKbps <= 320) { "MP3 supports up to 320 kbps. Choose 320 kbps or lower." }
            require(format != AudioFormat.OPUS || channels != 1 || settings.bitrateKbps <= 256) { "Mono Opus supports up to 256 kbps. Choose a lower bitrate." }
            args += listOf("-b:a", "${settings.bitrateKbps}k")
        }
        QualityMode.VARIABLE -> {
            val quality = when (format) {
                AudioFormat.MP3 -> settings.variableQuality.coerceAtMost(9).toString()
                AudioFormat.AAC, AudioFormat.M4A -> BigDecimal.valueOf(4.0 - 3.9 * settings.variableQuality / 10.0)
                    .setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                AudioFormat.VORBIS -> (10 - settings.variableQuality).toString()
                else -> error("Unsupported variable-quality format")
            }
            args += listOf("-q:a", quality)
        }
    }
    return EncodingPlan(output, false, args)
}
