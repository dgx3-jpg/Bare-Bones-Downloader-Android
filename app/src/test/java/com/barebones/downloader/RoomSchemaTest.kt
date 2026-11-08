package com.barebones.downloader

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.barebones.downloader.data.db.DownloadDatabase
import com.barebones.downloader.domain.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomSchemaTest {
    @Test fun schemaRoundTripsSnapshotsAndHistoryDeletionLeavesQueuedItems() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DownloadDatabase::class.java).build()
        try {
            val item = DownloadItem(sourceUrl = "https://example.org/media", outputFormat = AudioFormat.OPUS,
                metadataSettingsSnapshot = MetadataSettings(setOf(MetadataField.ARTIST)), replayGainEnabledSnapshot = true)
            db.downloads().insert(item)
            assertEquals(item, db.downloads().observeAll().first().single())
            db.downloads().insert(item.copy(id = java.util.UUID.randomUUID(), status = DownloadStatus.FAILED))
            db.downloads().clearHistory()
            assertEquals(listOf(item), db.downloads().observeAll().first())
        } finally { db.close() }
    }
}
