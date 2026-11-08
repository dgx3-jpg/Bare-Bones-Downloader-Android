package com.barebonesdownloader.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.barebonesdownloader.android.data.repository.DownloadRepository
import com.barebonesdownloader.android.data.settings.SettingsRepository
import com.barebonesdownloader.android.domain.model.*
import com.barebonesdownloader.android.download.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class DownloaderViewModel(private val preferences: SettingsRepository, private val downloads: DownloadRepository,
    private val diagnostics: DownloadLog = DownloadLog()) : ViewModel() {
    val message = MutableStateFlow<String?>(null)
    val log = diagnostics.text
    val settings: StateFlow<AppSettings?> = preferences.settings.map<AppSettings, AppSettings?> { it }
        .catch { note("Could not load preferences. Please restart."); emit(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val items = downloads.items
    val url = MutableStateFlow("")
    val kind = MutableStateFlow(DownloadKind.AUDIO)
    val draft = MutableStateFlow<AppSettings?>(null)
    fun note(text: String) = diagnostics.add(text)
    fun beginSettings() { draft.value = settings.value }
    fun edit(change: (AppSettings) -> AppSettings) { draft.value = draft.value?.let(change) }
    fun discard() { draft.value = null }
    private fun work(block: suspend () -> Unit) { viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) {
            val error = sanitizeDiagnostic(e.message ?: "Operation failed. Try again."); message.value = error; note(error)
        }
    } }
    fun save(saved: () -> Unit) { val value = draft.value ?: return
        work { preferences.update { value }; draft.value = null; note("Settings saved."); saved() }
    }
    fun folderSelected(type: DownloadKind, uri: String, name: String, inSettings: Boolean, start: () -> Unit = {}) {
        if (inSettings) edit { it.withFolder(type, uri, name) }
        else work {
            val blocked = items.value.firstOrNull { it.kind == type && it.status == DownloadStatus.BLOCKED }
            preferences.update { it.withFolder(type, uri, name) }
            if (blocked != null) { downloads.replaceDestination(type, blocked.destinationUri, uri); start() }
        }
    }
    fun add(start: () -> Unit = {}): Boolean {
        val snapshot = settings.value ?: return false
        val type = kind.value
        val tokens = url.value.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        val parsed = tokens.map { raw -> raw to runCatching { YoutubeSource.parse(raw) }.getOrNull() }
        if (parsed.none { it.second != null }) {
            message.value = "Paste valid YouTube watch, Shorts or youtu.be single-video links."
            note("Rejected input: no supported single-video YouTube links."); return false
        }
        if (snapshot.folder(type) == null) return true
        work {
            val rejected = mutableListOf<String>(); var count = 0
            parsed.forEach { (raw, source) ->
                if (source == null) { rejected += raw; note("Rejected an unsupported or malformed link.") }
                else try { downloads.enqueue(source.url, type, snapshot); count++ }
                catch (e: IllegalArgumentException) { rejected += raw; note(e.message ?: "Could not queue this item.") }
            }
            url.value = rejected.joinToString("\n")
            if (count > 0) { note("Queued $count ${type.label} item(s)."); start() }
            if (rejected.isNotEmpty()) message.value = "Some links were rejected or already queued; they remain in the input."
        }
        return false
    }
    fun cancelCurrent(start: () -> Unit = {}) = work { downloads.cancelCurrent(); start() }
    fun clearWaiting() = work { downloads.clearWaiting(); note("Waiting items cancelled.") }
    fun retry(id: UUID, start: () -> Unit) = work { downloads.retry(id); start() }
}
