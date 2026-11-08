package com.barebonesdownloader.android.media

import com.barebonesdownloader.android.domain.model.*
import java.io.File

data class MediaProgress(val stage: com.barebonesdownloader.android.domain.model.DownloadStatus, val percent: Int?)
interface MediaGateway {
    suspend fun download(item: DownloadItem, directory: File, progress: (MediaProgress) -> Unit): StagedMedia
    fun cancel(item: DownloadItem)
}
data class StagedMedia(val file: File, val title: String, val mime: String, val warnings: String? = null)
