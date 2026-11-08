package com.barebones.downloader

import android.app.Application
import com.barebones.downloader.data.repository.FakeDownloadRepository
import com.barebones.downloader.data.settings.*

class DownloaderApplication : Application() {
    val settings by lazy { DataStoreSettingsRepository(downloaderSettings) }
    val downloads by lazy { FakeDownloadRepository() }
}
