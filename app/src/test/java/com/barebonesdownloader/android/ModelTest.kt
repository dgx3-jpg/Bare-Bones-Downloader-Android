package com.barebonesdownloader.android

import com.barebonesdownloader.android.domain.model.*
import com.barebonesdownloader.android.domain.usecase.validMediaUrl
import com.barebonesdownloader.android.data.settings.decodeSettings
import com.barebonesdownloader.android.data.repository.FakeDownloadRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ModelTest {
    @Test fun v2DefaultsMatchDesktop() {
        val s = AppSettings()
        assertNull(s.audioFolderUri); assertNull(s.videoFolderUri)
        assertEquals(AppThemeMode.LIGHT, s.theme); assertEquals(AudioFormat.ORIGINAL, s.audioFormat)
        assertEquals(QualityMode.DEFAULT, s.qualityMode); assertEquals(5, s.variableQuality); assertEquals(192, s.bitrateKbps)
        assertEquals(0, s.audioMaxSizeMb); assertEquals(0, s.videoMaxSizeMb); assertEquals(0, s.videoMaxHeight)
        assertFalse(s.appendSourceUrl); assertTrue(s.replayGainEnabled)
        assertEquals(11, s.metadata.enabled.size); assertEquals(9, AudioFormat.entries.size)
    }
    @Test fun unsupportedModesAreResetAndFormatsMatchDesktopCapabilities() {
        assertFalse(AudioFormat.OPUS.variable); assertTrue(AudioFormat.OPUS.bitrate)
        assertFalse(AudioFormat.ALAC.bitrate); assertTrue(AudioFormat.VORBIS.variable)
        assertEquals(QualityMode.DEFAULT, AppSettings(audioFormat = AudioFormat.OPUS, qualityMode = QualityMode.VARIABLE).normalized().qualityMode)
        assertEquals(QualityMode.BITRATE, AppSettings(audioFormat = AudioFormat.OPUS, qualityMode = QualityMode.BITRATE).normalized().qualityMode)
    }
    @Test fun corruptedAndInvalidSettingsFallBackWithoutCrashing() {
        val problems = mutableListOf<String>()
        assertEquals(AppSettings(), decodeSettings("broken", problems::add)); assertTrue(problems.isNotEmpty())
        assertEquals(AppSettings(), decodeSettings("""{"theme":"unknown","audioFormat":"unknown","variableQuality":99,"bitrateKbps":2,"videoMaxHeight":999,"audioMaxSizeMb":-1}"""))
    }
    @Test fun staticPreviewUsesCorrectSampleAndCompatibilityMatrix() {
        val s = AppSettings()
        assertEquals("Here Comes the Sun", previewTags(s).toMap()["Title"])
        assertEquals("George Harrison", previewTags(s).toMap()["Composer"])
        assertEquals("", previewTags(s.copy(metadata = MetadataSettings(emptySet()))).toMap()["Title"])
        assertEquals("Unavailable", previewTags(s.copy(audioFormat = AudioFormat.WAV)).toMap()["Composer"])
        assertEquals("Unavailable", previewTags(s.copy(audioFormat = AudioFormat.M4A)).toMap()["Source URL"])
    }
    @Test fun httpValidationIsStillOnlyShellValidation() {
        assertEquals("https://example.org/a", validMediaUrl(" https://example.org/a "))
        listOf("", "hello", "file:///a", "https://", "https://a b.com").forEach { assertNull(validMediaUrl(it)) }
    }
    @Test fun audioAndVideoPlaceholdersCaptureCompleteIndependentSnapshots() = runTest {
        val repo = FakeDownloadRepository()
        val settings = AppSettings(audioFolderUri = "content://audio", videoFolderUri = "content://video",
            audioFormat = AudioFormat.VORBIS, qualityMode = QualityMode.VARIABLE, variableQuality = 8,
            audioMaxSizeMb = 12, videoMaxSizeMb = 99, videoMaxHeight = 720, appendSourceUrl = true)
        repo.enqueue("https://example.org/a", DownloadKind.AUDIO, settings)
        repo.enqueue("https://example.org/a", DownloadKind.VIDEO, settings)
        val jobs = repo.items.value.takeLast(2)
        assertEquals(settings, jobs[0].settingsSnapshot); assertEquals(settings, jobs[1].settingsSnapshot)
        assertEquals("content://audio", jobs[0].destinationUri); assertEquals("content://video", jobs[1].destinationUri)
        assertTrue(jobs[0].replayGainEnabledSnapshot); assertFalse(jobs[1].replayGainEnabledSnapshot)
        assertNull(jobs[0].outputUri); assertEquals(DownloadStatus.QUEUED, jobs[0].status)
        repo.clearWaiting(); assertTrue(repo.items.value.none { it.status == DownloadStatus.QUEUED })
    }
}
