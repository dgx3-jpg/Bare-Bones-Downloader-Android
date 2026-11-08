package com.barebonesdownloader.android.download

import com.barebonesdownloader.android.data.repository.RoomDownloadRepository
import com.barebonesdownloader.android.domain.model.*
import com.barebonesdownloader.android.media.MediaGateway
import com.barebonesdownloader.android.storage.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import java.io.File
import java.util.UUID

class DownloadCoordinator(private val repository: RoomDownloadRepository, private val media: MediaGateway,
    private val storage: MediaStorage, private val root: File, private val runtime: NativeRuntime, private val log: DownloadLog) {
    private val worker = Mutex()
    @Volatile private var current: DownloadItem? = null
    fun cancelNative(id: UUID) { runtime.cancel(id) }
    fun cancelAll() { current?.let { runtime.cancel(it.id) } }
    suspend fun runQueue() {
        if (!worker.tryLock()) return
        try {
            while (currentCoroutineContext().isActive) {
                val item = repository.firstWaiting() ?: return
                if (item.status == DownloadStatus.BLOCKED) return
                runtime.reset(item.id)
                val work = File(root, item.id.toString())
                try {
                    if (!storage.writable(item.destinationUri)) throw FolderUnavailable()
                    require((if (item.kind == DownloadKind.AUDIO) item.settingsSnapshot.audioMaxSizeMb else item.settingsSnapshot.videoMaxSizeMb) == 0) { "File-size caps arrive in milestone 4. Disable caps before queueing." }
                    require(item.kind != DownloadKind.AUDIO || item.settingsSnapshot.audioFormat == AudioFormat.ORIGINAL && item.settingsSnapshot.qualityMode == QualityMode.DEFAULT) { "This build supports Best available/default audio only; encoding arrives in milestone 3." }
                    current = item
                    repository.change(item.id) { if (it.status == DownloadStatus.QUEUED) it.copy(status = DownloadStatus.INSPECTING) else it }
                    checkActive(item.id)
                    log.add("Inspecting ${item.kind.label} item ${item.mediaId}.")
                    var last = -1
                    val staged = media.download(item, work) { percent ->
                        // Native callback is synchronous. Database writes are performed by
                        // the coordinator's ordered coroutine through a bounded bridge.
                        val value = percent ?: -1
                        if (value != last) { last = value; progressUpdates[item.id] = percent ?: 0 }
                    }
                    checkActive(item.id)
                    repository.change(item.id) { if (it.status in activeStatuses) it.copy(status = DownloadStatus.SAVING, progressPercent = 100) else it }
                    val uri = storage.publish(item, staged) { runtime.check(item.id) }
                    // Publication succeeded. Do not report completed before verification.
                    repository.change(item.id) { it.copy(status = DownloadStatus.COMPLETED, reservationKey = null, outputUri = uri,
                        outputFilename = FilenamePolicy.name(item, staged.title, staged.file.extension), title = staged.title,
                        progressPercent = 100, completedAt = System.currentTimeMillis(), errorMessage = staged.warnings) }
                    log.add("Completed ${item.kind.label} item ${item.mediaId}." + (staged.warnings?.let { " Warning: $it" } ?: ""))
                    work.deleteRecursively()
                } catch (e: FolderUnavailable) {
                    repository.change(item.id) { it.copy(status = DownloadStatus.BLOCKED, errorCode = "FOLDER", errorMessage = e.message) }
                    log.add("Queue paused: selected folder unavailable."); return
                } catch (e: DestinationExists) {
                    repository.change(item.id) { it.copy(status = DownloadStatus.SKIPPED, reservationKey = null, errorCode = "EXISTS", errorMessage = e.message) }
                    log.add("Skipped existing filename; no existing file was changed.")
                } catch (e: CancellationException) {
                    withContext(NonCancellable) { repository.change(item.id) {
                        if (it.status == DownloadStatus.CANCELLED) it else it.copy(status = DownloadStatus.INTERRUPTED, reservationKey = null,
                            progressPercent = null, errorMessage = "Interrupted. Tap Retry to resume.")
                    } }
                    if (!currentCoroutineContext().isActive) throw e
                    log.add("Cancelled ${item.kind.label} item ${item.mediaId}; partial staging retained.")
                } catch (e: Exception) {
                    repository.change(item.id) { if (it.status == DownloadStatus.CANCELLED) it else it.copy(status = DownloadStatus.FAILED,
                        reservationKey = null, progressPercent = null, errorCode = "DOWNLOAD", errorMessage = sanitizeDiagnostic(e.message ?: "Download failed. Try again or update the app's extractor.")) }
                    log.add("Item failed: ${sanitizeDiagnostic(e.message.orEmpty())}")
                } finally { progressUpdates.remove(item.id); current = null }
            }
        } finally { worker.unlock() }
    }
    private suspend fun checkActive(id: UUID) {
        runtime.check(id)
        require(repository.get(id)?.status in activeStatuses) { "This job is no longer active." }
    }
    private val progressUpdates = java.util.concurrent.ConcurrentHashMap<UUID, Int>()
    suspend fun flushProgress() {
        progressUpdates.forEach { (id, percent) -> repository.change(id) {
            if (it.status in setOf(DownloadStatus.INSPECTING, DownloadStatus.DOWNLOADING)) it.copy(status = DownloadStatus.DOWNLOADING, progressPercent = percent) else it
        } }
    }
}
