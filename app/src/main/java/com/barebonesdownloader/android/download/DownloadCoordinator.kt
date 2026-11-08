package com.barebonesdownloader.android.download

import com.barebonesdownloader.android.data.repository.RoomDownloadRepository
import com.barebonesdownloader.android.domain.model.*
import com.barebonesdownloader.android.media.MediaGateway
import com.barebonesdownloader.android.media.MediaProgress
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
                    current = item
                    repository.change(item.id) { if (it.status == DownloadStatus.QUEUED) it.copy(status = DownloadStatus.INSPECTING) else it }
                    checkActive(item.id)
                    log.add("Inspecting ${item.kind.label} item ${item.mediaId}.")
                    val staged = media.download(item, work) { event -> progressUpdates[item.id] = event }
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
                    val failure = describeDownloadFailure(e)
                    repository.change(item.id) { if (it.status == DownloadStatus.CANCELLED) it else it.copy(status = DownloadStatus.FAILED,
                        reservationKey = null, progressPercent = null, errorCode = failure.code, errorMessage = failure.message) }
                    log.add("Item failed (${failure.code}): ${failure.diagnostic}")
                } finally { progressUpdates.remove(item.id); current = null }
            }
        } finally { worker.unlock() }
    }
    private suspend fun checkActive(id: UUID) {
        runtime.check(id)
        require(repository.get(id)?.status in activeStatuses) { "This job is no longer active." }
    }
    private val progressUpdates = java.util.concurrent.ConcurrentHashMap<UUID, MediaProgress>()
    suspend fun flushProgress() {
        progressUpdates.forEach { (id, event) -> repository.change(id) {
            val order = listOf(DownloadStatus.INSPECTING, DownloadStatus.DOWNLOADING, DownloadStatus.PROCESSING, DownloadStatus.SAVING)
            if (it.status in order && order.indexOf(it.status) <= order.indexOf(event.stage)) it.copy(status = event.stage, progressPercent = event.percent) else it
        } }
    }
}
