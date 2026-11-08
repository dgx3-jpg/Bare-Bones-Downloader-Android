package com.barebonesdownloader.android

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.barebonesdownloader.android.data.db.DownloadDatabase
import com.barebonesdownloader.android.domain.model.*
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
                metadataSettingsSnapshot = MetadataSettings(setOf(MetadataField.ARTIST)), replayGainEnabledSnapshot = false,
                kind = DownloadKind.VIDEO, destinationUri = "content://video",
                settingsSnapshot = AppSettings(videoFolderUri = "content://video", videoMaxHeight = 720, videoMaxSizeMb = 128))
            db.downloads().insert(item)
            assertEquals(item, db.downloads().observeAll().first().single())
            db.downloads().insert(item.copy(id = java.util.UUID.randomUUID(), status = DownloadStatus.FAILED))
            db.downloads().clearHistory()
            assertEquals(listOf(item), db.downloads().observeAll().first())
        } finally { db.close() }
    }
}
