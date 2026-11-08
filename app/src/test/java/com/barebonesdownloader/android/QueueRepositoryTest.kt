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
            val legacy=rows.first()
            db.downloads().update(legacy.copy(extractorKey=null,mediaId=null,reservationKey=null))
            repo.recover()
            assertEquals("youtube",repo.get(legacy.id)!!.extractorKey)
            assertEquals("BaW_jenozKc",repo.get(legacy.id)!!.mediaId)
            repo.clearWaiting(); assertTrue(db.downloads().all().all { it.reservationKey == null })
            repo.enqueue("https://youtu.be/BaW_jenozKc", DownloadKind.AUDIO, prefs)
            val interrupted = db.downloads().all().last()
            repo.change(interrupted.id) { it.copy(status = DownloadStatus.DOWNLOADING) }
            repo.recover()
            assertEquals(DownloadStatus.INTERRUPTED, repo.get(interrupted.id)?.status)
            assertNull(repo.get(interrupted.id)?.reservationKey)
        } finally { scope.cancel(); db.close() }
    }
    @Test fun namespacedReservationsRebuildAfterRestartWithoutChangingSnapshots() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DownloadDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo=RoomDownloadRepository(db,scope)
            repo.resolveSource={ url -> SourceReference(url,if(url.contains("soundcloud")) "soundcloud" else "tiktok","123") }
            val settings=AppSettings(audioFolderUri="content://audio",audioMaxSizeMb=8)
            repo.enqueue("https://www.tiktok.com/@creator/video/123",DownloadKind.AUDIO,settings)
            repo.enqueue("https://soundcloud.com/artist/song",DownloadKind.AUDIO,settings)
            assertTrue(runCatching { repo.enqueue("https://www.tiktok.com/@alias/video/123",DownloadKind.AUDIO,settings) }.exceptionOrNull() is DuplicateDownload)
            val original=db.downloads().all().first()
            db.downloads().update(original.copy(reservationKey=null))
            repo.recover()
            assertEquals("tiktok:123|AUDIO",repo.get(original.id)!!.reservationKey)
            assertEquals(settings,repo.get(original.id)!!.settingsSnapshot)
            repo.change(original.id) { it.copy(status=DownloadStatus.FAILED,reservationKey=null) }
            repo.retry(original.id)
            assertEquals("tiktok:123|AUDIO",repo.get(original.id)!!.reservationKey)
        } finally { scope.cancel(); db.close() }
    }
    @Test fun cancellationWinningPublicationRaceCannotBeReportedCompleted() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DownloadDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val root=File(context.cacheDir,"publication-race").apply { mkdirs() }
        try {
            val repo=RoomDownloadRepository(db,scope)
            repo.enqueue("https://youtu.be/BaW_jenozKc",DownloadKind.AUDIO,AppSettings(audioFolderUri="content://folder"))
            val gateway=object: MediaGateway {
                override fun cancel(item: DownloadItem) {}
                override suspend fun download(item: DownloadItem,directory: File,progress: (MediaProgress)->Unit): StagedMedia {
                    directory.mkdirs(); return StagedMedia(File(directory,"owned.m4a").apply { writeText("explicit scheduling fake") },"Owned","audio/mp4")
                }
            }
            var discarded=false
            val storage=object: MediaStorage {
                override fun writable(uri: String?)=true
                override suspend fun publish(item: DownloadItem,media: StagedMedia,checkCancelled: ()->Unit): String { repo.cancelCurrent(); return "content://new-owned" }
                override fun discard(id: java.util.UUID,uri: String): Boolean { discarded=true; return true }
            }
            DownloadCoordinator(repo,gateway,storage,root,NativeRuntime(context),DownloadLog()).runQueue()
            assertEquals(DownloadStatus.CANCELLED,db.downloads().all().single().status)
            assertTrue(discarded); assertNull(db.downloads().all().single().outputUri)
        } finally { scope.cancel(); db.close(); root.deleteRecursively() }
    }
    @Test fun journalRecoversOnlyUncommittedRecords() = runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val db=Room.inMemoryDatabaseBuilder(context,DownloadDatabase::class.java).build()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        try {
            val repo=RoomDownloadRepository(db,scope); val deleted=mutableListOf<String>(); val journal=PublicationJournal(context) { deleted+=it.toString(); true }; val log=DownloadLog()
            val pending=java.util.UUID.randomUUID(); val complete=java.util.UUID.randomUUID()
            journal.record(pending,"content://fixture/pending")
            journal.record(complete,"content://fixture/completed")
            db.downloads().insert(DownloadItem(id=complete,sourceUrl="fixture",status=DownloadStatus.COMPLETED,outputUri="content://fixture/completed"))
            val root=File(context.noBackupFilesDir,"pending-publications")
            assertTrue(File(root,"$pending.uri").exists())
            journal.recover(repo,log)
            assertFalse(log.text.value,File(root,"$pending.uri").exists())
            assertFalse(log.text.value,File(root,"$complete.uri").exists())
            assertEquals(listOf("content://fixture/pending"),deleted)
            assertEquals(DownloadStatus.COMPLETED,repo.get(complete)!!.status)
            val denied=java.util.UUID.randomUUID()
            journal.record(denied,"content://fixture/denied")
            PublicationJournal(context) { false }.recover(repo,log)
            assertTrue(File(root,"$denied.uri").exists())
            journal.finished(denied)
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
                override suspend fun download(item: DownloadItem, directory: File, progress: (MediaProgress) -> Unit): StagedMedia {
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
