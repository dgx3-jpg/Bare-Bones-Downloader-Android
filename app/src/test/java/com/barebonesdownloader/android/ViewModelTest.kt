package com.barebonesdownloader.android

import com.barebonesdownloader.android.data.repository.FakeDownloadRepository
import com.barebonesdownloader.android.data.settings.SettingsRepository
import com.barebonesdownloader.android.domain.model.*
import com.barebonesdownloader.android.ui.DownloaderViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ViewModelTest {
    @Test fun draftsModesMixedInputAndLogBoundAreIndependentOfQueuedSnapshots() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val prefs = object : SettingsRepository {
                override val settings = MutableStateFlow(AppSettings(videoFolderUri = "content://video"))
                override suspend fun update(transform: (AppSettings) -> AppSettings) { settings.value = transform(settings.value) }
            }
            val repo = FakeDownloadRepository(); val model = DownloaderViewModel(prefs, repo); runCurrent()
            model.beginSettings(); model.edit { it.copy(theme = AppThemeMode.DARK) }; model.discard()
            assertEquals(AppThemeMode.LIGHT, prefs.settings.value.theme)
            model.beginSettings(); model.edit { it.copy(theme = AppThemeMode.DARK) }; model.save {}; runCurrent()
            assertEquals(AppThemeMode.DARK, prefs.settings.value.theme)
            model.kind.value = DownloadKind.VIDEO; model.url.value = "https://youtu.be/BaW_jenozKc broken https://youtu.be/abcdefghijk"
            assertFalse(model.add()); runCurrent()
            assertEquals("broken", model.url.value)
            assertEquals(listOf(DownloadKind.VIDEO, DownloadKind.VIDEO), repo.items.value.takeLast(2).map { it.kind })
            model.kind.value = DownloadKind.AUDIO; assertEquals("broken", model.url.value)
            model.url.value = "https://youtu.be/BaW_jenozKc"; assertTrue(model.add())
            repeat(20) { model.note("x".repeat(6_100)) }; assertEquals(60_000, model.log.value.length)
        } finally { Dispatchers.resetMain() }
    }
}
