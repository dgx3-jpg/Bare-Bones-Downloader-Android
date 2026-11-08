package com.barebonesdownloader.android.data.repository

import com.barebonesdownloader.android.domain.model.*
import kotlinx.coroutines.flow.*
import java.util.UUID

interface DownloadRepository {
    val items: StateFlow<List<DownloadItem>>
    suspend fun enqueue(url: String, kind: DownloadKind, settings: AppSettings)
    suspend fun cancelCurrent()
    suspend fun clearWaiting()
}
/** Shell-only in-memory placeholders. No extractor, worker, subprocess or media file. */
class FakeDownloadRepository : DownloadRepository {
    private val state = MutableStateFlow(listOf(
        DownloadItem(sourceUrl = "https://example.com/demo-audio", title = "Demo audio — placeholder", status = DownloadStatus.DOWNLOADING, progressPercent = 42),
        DownloadItem(sourceUrl = "https://example.com/demo-video", title = "Demo video — placeholder", kind = DownloadKind.VIDEO)
    ))
    override val items = state.asStateFlow()
    override suspend fun enqueue(url: String, kind: DownloadKind, settings: AppSettings) {
        state.update { it + DownloadItem(sourceUrl = url, sourceHost = java.net.URI(url).host, kind = kind,
            title = "Demo queued item", outputFormat = settings.audioFormat, destinationUri = settings.folder(kind),
            settingsSnapshot = settings, metadataSettingsSnapshot = settings.metadata,
            replayGainEnabledSnapshot = kind == DownloadKind.AUDIO && settings.replayGainEnabled) }
    }
    override suspend fun cancelCurrent() {
        state.update { list -> val id = list.firstOrNull { it.status in activeStatuses }?.id
            list.map { if (it.id == id) it.copy(status = DownloadStatus.CANCELLED, progressPercent = null) else it } }
    }
    override suspend fun clearWaiting() { state.update { list -> list.map { if (it.status == DownloadStatus.QUEUED) it.copy(status = DownloadStatus.CANCELLED) else it } } }
}
