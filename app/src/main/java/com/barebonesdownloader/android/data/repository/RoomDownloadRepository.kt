package com.barebonesdownloader.android.data.repository

import androidx.room.withTransaction
import com.barebonesdownloader.android.data.db.DownloadDatabase
import com.barebonesdownloader.android.domain.model.*
import com.barebonesdownloader.android.download.SourceUrls
import com.barebonesdownloader.android.download.SourceReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import java.util.UUID

class DuplicateDownload : IllegalArgumentException("This source and type are already waiting or active.")
class RoomDownloadRepository(val db: DownloadDatabase, scope: CoroutineScope) : DownloadRepository {
    private val dao = db.downloads()
    override val items = dao.observeAll().stateIn(scope, SharingStarted.Eagerly, emptyList())
    var resolveSource: suspend (String) -> SourceReference = { SourceUrls.youtube(it) }
    var cancelProcess: (UUID) -> Unit = {}
    override suspend fun enqueue(url: String, kind: DownloadKind, settings: AppSettings) {
        val source = resolveSource(SourceUrls.normalize(url))
        val key = "${source.identity}|${kind.name}"
        db.withTransaction {
            if (dao.reserved(key)) throw DuplicateDownload()
            dao.insert(DownloadItem(sourceUrl = source.url, sourceHost = source.host, extractorKey = source.extractor, mediaId = source.id,
                queueOrder = dao.nextOrder(), reservationKey = key, kind = kind, destinationUri = settings.folder(kind),
                settingsSnapshot = settings, outputFormat = settings.audioFormat, metadataSettingsSnapshot = settings.metadata,
                replayGainEnabledSnapshot = kind == DownloadKind.AUDIO && settings.replayGainEnabled))
        }
    }
    suspend fun recover() = db.withTransaction {
        dao.all().forEach { item ->
            if (item.status in activeStatuses) dao.update(item.copy(status = DownloadStatus.INTERRUPTED, reservationKey = null,
                progressPercent = null, errorMessage = "Interrupted. Tap Retry to resume safely.", updatedAt = System.currentTimeMillis()))
        }
        val waiting = dao.all().filter { it.status in setOf(DownloadStatus.QUEUED, DownloadStatus.BLOCKED) }
        waiting.forEach { dao.update(it.copy(reservationKey = null)) }
        val seen = mutableSetOf<String>()
        waiting.forEach { item ->
            val key = runCatching { "${SourceUrls.identity(item)}|${item.kind.name}" }.getOrNull()
            if (key == null || !seen.add(key)) dao.update(item.copy(status = DownloadStatus.SKIPPED, reservationKey = null,
                errorCode = "DUPLICATE_RECOVERY", errorMessage = "Invalid or duplicate waiting item recovered; no media was downloaded."))
            else {
                val legacy = if (item.extractorKey.isNullOrBlank() || item.mediaId.isNullOrBlank()) runCatching { SourceUrls.youtube(item.sourceUrl) }.getOrNull() else null
                dao.update(item.copy(reservationKey = key, sourceUrl = legacy?.url ?: item.sourceUrl,
                    extractorKey = legacy?.extractor ?: item.extractorKey, mediaId = legacy?.id ?: item.mediaId,
                    sourceHost = legacy?.host ?: item.sourceHost))
            }
        }
    }
    override suspend fun cancelCurrent() {
        val item = dao.all().firstOrNull { it.status in activeStatuses || it.status == DownloadStatus.BLOCKED } ?: return
        cancelProcess(item.id)
        change(item.id) { it.copy(status = DownloadStatus.CANCELLED, reservationKey = null, progressPercent = null) }
    }
    override suspend fun clearWaiting() = db.withTransaction {
        dao.all().filter { it.status == DownloadStatus.QUEUED }.forEach { dao.update(it.copy(status = DownloadStatus.CANCELLED, reservationKey = null)) }
    }
    override suspend fun retry(id: UUID) = db.withTransaction {
        val item = dao.get(id) ?: return@withTransaction
        require(item.status in setOf(DownloadStatus.FAILED, DownloadStatus.INTERRUPTED, DownloadStatus.CANCELLED)) { "Only stopped items can be retried." }
        val key = "${SourceUrls.identity(item)}|${item.kind.name}"
        if (dao.reserved(key)) throw DuplicateDownload()
        dao.update(item.copy(status = DownloadStatus.QUEUED, reservationKey = key, queueOrder = dao.nextOrder(), errorMessage = null, errorCode = null, progressPercent = null))
    }
    override suspend fun replaceDestination(kind: DownloadKind, old: String?, new: String) = db.withTransaction {
        dao.all().filter { it.kind == kind && it.destinationUri == old && it.status in setOf(DownloadStatus.QUEUED, DownloadStatus.BLOCKED) }.forEach {
            dao.update(it.copy(destinationUri = new, settingsSnapshot = it.settingsSnapshot.withFolder(kind, new, "Replacement folder"),
                status = DownloadStatus.QUEUED, errorCode = null, errorMessage = null))
        }
    }
    suspend fun firstWaiting(): DownloadItem? = dao.all().firstOrNull { it.status in setOf(DownloadStatus.QUEUED, DownloadStatus.BLOCKED) }
    suspend fun get(id: UUID) = dao.get(id)
    suspend fun change(id: UUID, transform: (DownloadItem) -> DownloadItem) = db.withTransaction {
        dao.get(id)?.let { dao.update(transform(it).copy(updatedAt = System.currentTimeMillis())) }
    }
}
