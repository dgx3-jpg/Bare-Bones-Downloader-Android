package com.barebonesdownloader.android

import android.app.Application
import androidx.room.Room
import com.barebonesdownloader.android.data.db.DownloadDatabase
import com.barebonesdownloader.android.data.repository.RoomDownloadRepository
import com.barebonesdownloader.android.data.settings.*
import com.barebonesdownloader.android.download.*
import com.barebonesdownloader.android.media.YtDlpGateway
import com.barebonesdownloader.android.storage.SafMediaStorage
import kotlinx.coroutines.*

class DownloaderApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val settings by lazy { DataStoreSettingsRepository(downloaderSettings) }
    val database by lazy { Room.databaseBuilder(this, DownloadDatabase::class.java, "downloads.db").addMigrations(DownloadDatabase.MIGRATION_1_2).build() }
    val downloads by lazy { RoomDownloadRepository(database, scope) }
    val diagnostics by lazy { DownloadLog(java.io.File(noBackupFilesDir, "diagnostics.log")) }
    val runtime by lazy { NativeRuntime(this) }
    val gateway by lazy { YtDlpGateway(runtime, diagnostics) }
    val coordinator by lazy { DownloadCoordinator(downloads, gateway, SafMediaStorage(this),
        java.io.File(cacheDir, "download-jobs"), runtime, diagnostics) }
    lateinit var ready: Deferred<Unit>
    override fun onCreate() {
        super.onCreate()
        downloads.resolveSource = gateway::resolveSource
        downloads.cancelProcess = coordinator::cancelNative
        ready = scope.async {
            com.barebonesdownloader.android.storage.PublicationJournal(this@DownloaderApplication).recover(downloads, diagnostics)
            downloads.recover()
        }
    }
}
