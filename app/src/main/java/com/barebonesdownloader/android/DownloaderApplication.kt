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
    val diagnostics = DownloadLog()
    val runtime by lazy { NativeRuntime(this) }
    val coordinator by lazy { DownloadCoordinator(downloads, YtDlpGateway(runtime, diagnostics), SafMediaStorage(this),
        java.io.File(cacheDir, "download-jobs"), runtime, diagnostics) }
    lateinit var ready: Deferred<Unit>
    override fun onCreate() {
        super.onCreate()
        downloads.cancelProcess = coordinator::cancelNative
        ready = scope.async { downloads.recover() }
    }
}
