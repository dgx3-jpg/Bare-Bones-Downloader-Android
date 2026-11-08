package com.barebonesdownloader.android

import android.app.Application
import com.barebonesdownloader.android.data.repository.FakeDownloadRepository
import com.barebonesdownloader.android.data.settings.*

class DownloaderApplication : Application() {
    val settings by lazy { DataStoreSettingsRepository(downloaderSettings) }
    val downloads by lazy { FakeDownloadRepository() }
}
