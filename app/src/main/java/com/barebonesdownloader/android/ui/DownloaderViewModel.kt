package com.barebonesdownloader.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.barebonesdownloader.android.data.repository.DownloadRepository
import com.barebonesdownloader.android.data.settings.SettingsRepository
import com.barebonesdownloader.android.domain.model.*
import com.barebonesdownloader.android.domain.usecase.validMediaUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DownloaderViewModel(private val preferences: SettingsRepository, private val downloads: DownloadRepository) : ViewModel() {
    val message = MutableStateFlow<String?>(null)
    val log = MutableStateFlow("Milestone 1: placeholder queue only; no download engine is installed.\n")
    val settings: StateFlow<AppSettings?> = preferences.settings.map<AppSettings, AppSettings?> { it }
        .catch { note("Could not load preferences. Please restart."); emit(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val items = downloads.items
    val url = MutableStateFlow("")
    val kind = MutableStateFlow(DownloadKind.AUDIO)
    val draft = MutableStateFlow<AppSettings?>(null)
    fun note(text: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.UK).format(Date())
        log.update { (it + "[$stamp] $text\n").takeLast(60_000) }
    }
    fun beginSettings() { draft.value = settings.value }
    fun edit(change: (AppSettings) -> AppSettings) { draft.value = draft.value?.let(change) }
    fun discard() { draft.value = null }
    fun save(saved: () -> Unit) { val value = draft.value ?: return
        viewModelScope.launch { try {
            preferences.update { value }; draft.value = null; note("Settings saved."); saved()
        } catch (e: CancellationException) { throw e } catch (_: Exception) { message.value = "Could not save settings." } }
    }
    fun folderSelected(type: DownloadKind, uri: String, name: String, inSettings: Boolean) {
        if (inSettings) edit { it.withFolder(type, uri, name) }
        else viewModelScope.launch { try { preferences.update { it.withFolder(type, uri, name) } }
            catch (e: CancellationException) { throw e } catch (_: Exception) { message.value = "Could not save folder selection." } }
    }
    fun add(): Boolean {
        val snapshot = settings.value ?: return false
        val type = kind.value
        val tokens = url.value.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val valid = tokens.mapNotNull(::validMediaUrl)
        if (valid.isEmpty()) { message.value = "Enter valid http or https links."; note("Rejected input: no valid HTTP(S) links."); return false }
        if (snapshot.folder(type) == null) return true
        val rejected = tokens.filter { validMediaUrl(it) == null }
        viewModelScope.launch {
            valid.forEach { downloads.enqueue(it, type, snapshot) }
            url.value = rejected.joinToString("\n")
            if (rejected.isNotEmpty()) note("Rejected ${rejected.size} invalid link(s); retained in input.")
            note("Added ${valid.size} ${type.label} placeholder(s); no download started.")
        }
        return false
    }
    fun cancelCurrent() { viewModelScope.launch { downloads.cancelCurrent(); note("Demo current item cancelled.") } }
    fun clearWaiting() { viewModelScope.launch { downloads.clearWaiting(); note("Demo waiting items cleared.") } }
}
