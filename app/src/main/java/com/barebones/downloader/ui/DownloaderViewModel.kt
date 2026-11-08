package com.barebones.downloader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.barebones.downloader.data.repository.DownloadRepository
import com.barebones.downloader.data.settings.SettingsRepository
import com.barebones.downloader.domain.model.*
import com.barebones.downloader.domain.usecase.validMediaUrl
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class DownloaderViewModel(private val preferences: SettingsRepository, private val downloads: DownloadRepository) : ViewModel() {
    val message = MutableStateFlow<String?>(null)
    val settings: StateFlow<AppSettings?> = preferences.settings
        .map<AppSettings, AppSettings?> { it }
        .catch { message.value = "Could not load settings. Restart the app to try again."; emit(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val items = downloads.items
    val url = MutableStateFlow("")
    fun update(transform: (AppSettings) -> AppSettings) { viewModelScope.launch {
        runCatching { preferences.update(transform) }.onFailure { message.value = "Could not save settings. Please try again." }
    } }
    /** Returns true when Home must show the blocking folder prompt. */
    fun add(): Boolean {
        val validated = validMediaUrl(url.value)
        if (validated == null) { message.value = "Enter a valid http or https URL."; return false }
        val snapshot = settings.value ?: return false
        if (snapshot.saveFolderUri == null) return true
        viewModelScope.launch { downloads.enqueue(validated, snapshot); url.value = ""; message.value = "Added to demo queue. No media will be downloaded." }
        return false
    }
    fun folderSelected(uri: String) = update { it.copy(saveFolderUri = uri) }
    fun remove(id: UUID) { viewModelScope.launch { downloads.remove(id) } }
    fun retry(id: UUID) { viewModelScope.launch { downloads.retry(id) } }
    fun cancel(id: UUID) { viewModelScope.launch { downloads.cancel(id) } }
    fun clearCompleted() { viewModelScope.launch { downloads.clearCompleted() } }
    fun clearHistory() { viewModelScope.launch { downloads.clearHistory() } }
}
