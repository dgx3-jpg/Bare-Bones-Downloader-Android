package com.barebones.downloader.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.barebones.downloader.domain.model.*
import com.barebones.downloader.ui.components.DownloadCard
import java.util.UUID

@Composable fun HistoryScreen(items: List<DownloadItem>, remove: (UUID) -> Unit, retry: (UUID) -> Unit, clear: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    val history = items.filter { it.status in historyStatuses }.sortedByDescending { it.completedAt ?: it.updatedAt }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { TextButton(onClick = { confirm = true }, enabled = history.isNotEmpty()) { Text("Clear history") } }
        if (history.isEmpty()) item { Text("No download history") }
        items(history, key = { it.id }) { item -> DownloadCard(item, history = true, onRemove = { remove(item.id) },
            onRetry = if (item.status == DownloadStatus.FAILED) ({ retry(item.id) }) else null) }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Clear history?") },
        text = { Text("Remove history entries only. Saved media files will remain untouched.") },
        confirmButton = { TextButton(onClick = { clear(); confirm = false }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}
