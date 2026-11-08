package com.barebonesdownloader.android

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.barebonesdownloader.android.data.db.DownloadDatabase
import com.barebonesdownloader.android.data.repository.*
import com.barebonesdownloader.android.domain.model.*
import com.barebonesdownloader.android.download.*
import com.barebonesdownloader.android.media.*
import com.barebonesdownloader.android.storage.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class QueueRepositoryTest {
    @Test fun reservationsArePerTypeReleasedAndSnapshotsSurviveDatabaseRoundTrip() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DownloadDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = RoomDownloadRepository(db, scope)
            val prefs = AppSettings(audioFolderUri = "content://audio", videoFolderUri = "content://video")
            repo.enqueue("https://youtu.be/BaW_jenozKc", DownloadKind.AUDIO, prefs)
            assertTrue(runCatching { repo.enqueue("https://youtube.com/watch?v=BaW_jenozKc&list=PL", DownloadKind.AUDIO, prefs) }.exceptionOrNull() is DuplicateDownload)
            repo.enqueue("https://youtu.be/BaW_jenozKc", DownloadKind.VIDEO, prefs)
            val rows = db.downloads().all(); assertEquals(2, rows.size); assertTrue(rows[0].queueOrder < rows[1].queueOrder)
            assertEquals(prefs, rows[0].settingsSnapshot); assertEquals("content://video", rows[1].destinationUri)
            repo.clearWaiting(); assertTrue(db.downloads().all().all { it.reservationKey == null })
            repo.enqueue("https://youtu.be/BaW_jenozKc", DownloadKind.AUDIO, prefs)
            val interrupted = db.downloads().all().last()
            repo.change(interrupted.id) { it.copy(status = DownloadStatus.DOWNLOADING) }
            repo.recover()
            assertEquals(DownloadStatus.INTERRUPTED, repo.get(interrupted.id)?.status)
            assertNull(repo.get(interrupted.id)?.reservationKey)
        } finally { scope.cancel(); db.close() }
    }
    @Test fun fifoIsSequentialFailureContinuesAndBlockedFolderPreservesOrder() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DownloadDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val root = File(context.cacheDir, "coordinator-test").apply { mkdirs() }
        try {
            val repo = RoomDownloadRepository(db, scope)
            val prefs = AppSettings(audioFolderUri = "content://old")
            listOf("BaW_jenozKc", "abcdefghijk", "12345678901").forEach { repo.enqueue("https://youtu.be/$it", DownloadKind.AUDIO, prefs) }
            val visits = mutableListOf<String>(); var writable = false
            val gateway = object : MediaGateway {
                override fun cancel(item: DownloadItem) {}
                override suspend fun download(item: DownloadItem, directory: File, progress: (Int?) -> Unit): StagedMedia {
                    visits += item.mediaId!!
                    if (visits.size == 1) error("Simulated network failure")
                    directory.mkdirs(); val file = File(directory, "fixture.m4a").apply { writeBytes(byteArrayOf(1,2,3)) }
                    return StagedMedia(file, "Test", "audio/mp4")
                }
            }
            val storage = object : MediaStorage {
                override fun writable(uri: String?) = writable
                override suspend fun publish(item: DownloadItem, media: StagedMedia, checkCancelled: () -> Unit): String = "content://saved/${item.id}"
            }
            val coordinator = DownloadCoordinator(repo, gateway, storage, root, NativeRuntime(context), DownloadLog())
            coordinator.runQueue(); assertTrue(visits.isEmpty())
            assertEquals(DownloadStatus.BLOCKED, db.downloads().all().first().status)
            repo.replaceDestination(DownloadKind.AUDIO, "content://old", "content://new"); writable = true
            coordinator.runQueue()
            assertEquals(listOf("BaW_jenozKc", "abcdefghijk", "12345678901"), visits)
            assertEquals(listOf(DownloadStatus.FAILED, DownloadStatus.COMPLETED, DownloadStatus.COMPLETED), db.downloads().all().map { it.status })
            assertTrue(db.downloads().all().all { it.destinationUri == "content://new" && it.reservationKey == null })
        } finally { scope.cancel(); db.close(); root.deleteRecursively() }
    }
}
