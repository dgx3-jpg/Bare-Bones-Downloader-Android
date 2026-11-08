package com.barebonesdownloader.android.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.ColumnInfo
import java.util.UUID

enum class DownloadStatus(val label: String) {
    QUEUED("Waiting"), INSPECTING("Inspecting"), DOWNLOADING("Downloading"), PROCESSING("Processing"),
    TAGGING("Tagging"), SAVING("Saving"), COMPLETED("Completed"), FAILED("Failed"),
    CANCELLED("Cancelled"), SKIPPED("Skipped"), BLOCKED("Folder unavailable — choose replacement"), INTERRUPTED("Paused — retry required")
}
val activeStatuses = setOf(DownloadStatus.INSPECTING, DownloadStatus.DOWNLOADING, DownloadStatus.PROCESSING, DownloadStatus.TAGGING, DownloadStatus.SAVING)
val historyStatuses = setOf(DownloadStatus.COMPLETED, DownloadStatus.FAILED, DownloadStatus.CANCELLED)

@Entity(tableName = "downloads", indices = [Index(value = ["reservationKey"], unique = true)])
data class DownloadItem(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val sourceUrl: String,
    @ColumnInfo(defaultValue = "0") val queueOrder: Long = 0,
    val reservationKey: String? = null,
    val sourceHost: String? = null,
    val extractorKey: String? = null,
    val mediaId: String? = null,
    val title: String? = null,
    val uploader: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val thumbnailUrl: String? = null,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progressPercent: Int? = null,
    val outputFormat: AudioFormat = AudioFormat.ORIGINAL,
    val outputUri: String? = null,
    val outputFilename: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val completedAt: Long? = null,
    val metadataSettingsSnapshot: MetadataSettings = MetadataSettings(),
    val replayGainEnabledSnapshot: Boolean = false,
    val kind: DownloadKind = DownloadKind.AUDIO,
    val destinationUri: String? = null,
    val settingsSnapshot: AppSettings = AppSettings(),
)
