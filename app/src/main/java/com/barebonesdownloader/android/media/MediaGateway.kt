package com.barebonesdownloader.android.media

import com.barebonesdownloader.android.domain.model.*
import java.io.File

interface MediaGateway {
    suspend fun download(item: DownloadItem, directory: File, progress: (Int?) -> Unit): StagedMedia
    fun cancel(item: DownloadItem)
}
data class StagedMedia(val file: File, val title: String, val mime: String, val warnings: String? = null)
