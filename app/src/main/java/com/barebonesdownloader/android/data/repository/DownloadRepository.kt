package com.barebonesdownloader.android.data.repository

import com.barebonesdownloader.android.domain.model.*
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

interface DownloadRepository {
    val items: StateFlow<List<DownloadItem>>
    suspend fun enqueue(url: String, kind: DownloadKind, settings: AppSettings)
    suspend fun cancelCurrent()
    suspend fun clearWaiting()
    suspend fun retry(id: UUID)
    suspend fun replaceDestination(kind: DownloadKind, old: String?, new: String)
}
