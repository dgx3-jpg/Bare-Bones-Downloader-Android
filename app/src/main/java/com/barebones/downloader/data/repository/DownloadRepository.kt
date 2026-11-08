package com.barebones.downloader.data.repository

import com.barebones.downloader.domain.model.*
import kotlinx.coroutines.flow.*
import java.util.UUID

interface DownloadRepository {
    val items: StateFlow<List<DownloadItem>>
    suspend fun enqueue(url: String, settings: AppSettings)
    suspend fun remove(id: UUID)
    suspend fun retry(id: UUID)
    suspend fun cancel(id: UUID)
    suspend fun clearCompleted()
    suspend fun clearHistory()
}
/** Milestone 1 fixture only. No network, processes, media files, or persistent queue. */
class FakeDownloadRepository : DownloadRepository {
    private val state = MutableStateFlow(listOf(
        DownloadItem(sourceUrl = "https://example.com/demo", sourceHost = "example.com", title = "Demo audio — fake progress",
            status = DownloadStatus.DOWNLOADING, progressPercent = 42),
        DownloadItem(sourceUrl = "https://example.com/completed", sourceHost = "example.com", title = "Demo completed item — no file",
            status = DownloadStatus.COMPLETED, outputFilename = "demo.opus (illustrative)", completedAt = System.currentTimeMillis()),
        DownloadItem(sourceUrl = "https://example.com/failed", sourceHost = "example.com", title = "Demo failed item",
            status = DownloadStatus.FAILED, errorCode = "DEMO", errorMessage = "Illustrative network failure; no request was made.")
    ))
    override val items = state.asStateFlow()
    override suspend fun enqueue(url: String, settings: AppSettings) {
        state.update { it + DownloadItem(sourceUrl = url, sourceHost = java.net.URI(url).host,
            title = "Demo queued item — no download", outputFormat = settings.audioFormat,
            audioQualitySnapshot = settings.audioQuality, saveFolderUriSnapshot = settings.saveFolderUri,
            metadataSettingsSnapshot = settings.metadata, replayGainEnabledSnapshot = settings.replayGainEnabled) }
    }
    override suspend fun remove(id: UUID) { state.update { list -> list.filterNot { it.id == id } } }
    override suspend fun retry(id: UUID) { change(id, DownloadStatus.QUEUED) }
    override suspend fun cancel(id: UUID) { change(id, DownloadStatus.CANCELLED) }
    private fun change(id: UUID, status: DownloadStatus) {
        state.update { list -> list.map { if (it.id == id) it.copy(status = status, progressPercent = null, errorMessage = null,
            errorCode = null, completedAt = null, updatedAt = System.currentTimeMillis()) else it } }
    }
    override suspend fun clearCompleted() { state.update { list -> list.filterNot { it.status == DownloadStatus.COMPLETED } } }
    override suspend fun clearHistory() { state.update { list -> list.filterNot { it.status in historyStatuses } } }
}
