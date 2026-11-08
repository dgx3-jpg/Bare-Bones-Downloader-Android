package com.barebones.downloader

import com.barebones.downloader.domain.model.*
import com.barebones.downloader.domain.usecase.validMediaUrl
import com.barebones.downloader.data.repository.FakeDownloadRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ModelTest {
    @Test fun defaultsMatchSpecification() {
        val settings = AppSettings()
        assertNull(settings.saveFolderUri)
        assertEquals(AudioFormat.ORIGINAL, settings.audioFormat)
        assertEquals(AudioQuality.BEST, settings.audioQuality)
        assertFalse(settings.replayGainEnabled)
        assertEquals(setOf(MetadataField.TITLE, MetadataField.ARTIST, MetadataField.ALBUM,
            MetadataField.ALBUM_ARTIST, MetadataField.TRACK_NUMBER, MetadataField.RELEASE_DATE,
            MetadataField.GENRE, MetadataField.ARTWORK), settings.metadata.enabled)
    }
    @Test fun previewRemovesDisabledFieldsAndKeepsMissingValuesEmpty() {
        val preview = previewTags(MetadataSettings())
        assertEquals("Come Together", preview.toMap()["Title"])
        assertEquals("", preview.toMap()["Genre"])
        assertFalse(preview.toMap().containsKey("Source URL"))
        assertFalse(previewTags(MetadataSettings(setOf(MetadataField.ARTIST))).toMap().containsKey("Title"))
    }
    @Test fun validationAcceptsAnyHttpHostButRejectsMalformedAndOtherSchemes() {
        assertEquals("https://example.org/media?a=1", validMediaUrl(" https://example.org/media?a=1 "))
        assertNotNull(validMediaUrl("http://soundcloud.com/example"))
        listOf("", "hello", "file:///tmp/a", "javascript:alert(1)", "https://", "https://a b.com", "https://user:secret@example.com").forEach { assertNull(it, validMediaUrl(it)) }
    }
    @Test fun demoQueueCapturesSettingsWithoutStartingRealWork() = runTest {
        val repo = FakeDownloadRepository()
        val snapshot = AppSettings(audioFormat = AudioFormat.OPUS, replayGainEnabled = true,
            saveFolderUri = "content://example/tree/music", metadata = MetadataSettings(setOf(MetadataField.TITLE)))
        repo.enqueue("https://example.org/one", snapshot)
        repo.enqueue("https://example.org/two", AppSettings())
        val job = repo.items.value.takeLast(2).first()
        assertEquals(AudioFormat.OPUS, job.outputFormat)
        assertEquals(snapshot.metadata, job.metadataSettingsSnapshot)
        assertEquals(snapshot.saveFolderUri, job.saveFolderUriSnapshot)
        assertTrue(job.replayGainEnabledSnapshot)
        assertEquals(DownloadStatus.QUEUED, job.status)
        assertNull(job.outputUri)
        repo.cancel(job.id)
        assertEquals(DownloadStatus.CANCELLED, repo.items.value.first { it.id == job.id }.status)
        repo.retry(job.id)
        assertEquals(DownloadStatus.QUEUED, repo.items.value.first { it.id == job.id }.status)
        repo.clearHistory()
        assertTrue(repo.items.value.none { it.status in historyStatuses })
        assertTrue(repo.items.value.any { it.id == job.id })
    }
}
