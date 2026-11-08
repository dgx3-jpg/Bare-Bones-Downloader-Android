package com.barebonesdownloader.android.data.repository
import com.barebonesdownloader.android.domain.model.*
import kotlinx.coroutines.flow.*
import java.util.UUID
class FakeDownloadRepository : DownloadRepository {
    private val state = MutableStateFlow(emptyList<DownloadItem>())
    override val items = state.asStateFlow()
    override suspend fun enqueue(url: String, kind: DownloadKind, settings: AppSettings) { state.update { it + DownloadItem(sourceUrl = url, kind = kind, destinationUri = settings.folder(kind), settingsSnapshot = settings, replayGainEnabledSnapshot = kind == DownloadKind.AUDIO && settings.replayGainEnabled) } }
    override suspend fun clearWaiting() { state.update { list -> list.map { it.copy(status = DownloadStatus.CANCELLED) } } }
    override suspend fun cancelCurrent() {}
    override suspend fun retry(id: UUID) {}
    override suspend fun replaceDestination(kind: DownloadKind, old: String?, new: String) {}
}
