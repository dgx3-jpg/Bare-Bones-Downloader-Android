package com.barebonesdownloader.android
import com.barebonesdownloader.android.domain.model.*
import com.barebonesdownloader.android.media.*
import org.junit.Test
import org.junit.Assert.*
import java.util.Locale
class AudioEncodingTest {
    @Test fun explicitModesReencodeEvenCompatibleSource() {
        val base = AppSettings(audioFormat = AudioFormat.AAC)
        assertTrue(encodingPlan(base, "aac")!!.copy)
        assertFalse(encodingPlan(base.copy(qualityMode = QualityMode.BITRATE), "aac")!!.copy)
        assertFalse(encodingPlan(base.copy(qualityMode = QualityMode.VARIABLE), "aac")!!.copy)
        assertFalse(encodingPlan(base, "opus")!!.copy)
        assertNull(encodingPlan(AppSettings(), "opus"))
    }
    @Test fun invalidEncoderCombinationsFailClearly() {
        assertTrue(runCatching { encodingPlan(AppSettings(audioFormat = AudioFormat.FLAC, qualityMode = QualityMode.BITRATE), "flac") }.isFailure)
        assertTrue(runCatching { encodingPlan(AppSettings(audioFormat = AudioFormat.MP3, qualityMode = QualityMode.BITRATE, bitrateKbps = 512), "aac") }.isFailure)
        assertTrue(runCatching { encodingPlan(AppSettings(audioFormat = AudioFormat.OPUS, qualityMode = QualityMode.BITRATE, bitrateKbps = 512), "aac", 1) }.isFailure)
    }
    @Test fun qualityIsLocaleIndependentAndAllVideoFallbacksHaveCeiling() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val args = encodingPlan(AppSettings(audioFormat = AudioFormat.AAC, qualityMode = QualityMode.VARIABLE, variableQuality = 10), "opus")!!.arguments
            assertEquals("0.1", args.last())
        } finally { Locale.setDefault(original) }
        videoHeights.forEach { height ->
            val alternatives = videoSelector(height).split('/')
            assertEquals(2, alternatives.size)
            alternatives.forEach { assertEquals(height > 0, it.contains("[height<=$height]")) }
        }
    }
}
