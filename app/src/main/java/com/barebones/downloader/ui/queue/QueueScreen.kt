package com.barebones.downloader.ui.queue

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.barebones.downloader.domain.model.*
import com.barebones.downloader.ui.components.DownloadCard
import java.util.UUID

@Composable fun QueueScreen(items: List<DownloadItem>, remove: (UUID) -> Unit,
    retry: (UUID) -> Unit, cancel: (UUID) -> Unit, clearCompleted: () -> Unit) {
    val queue = items.filter { it.status !in setOf(DownloadStatus.COMPLETED, DownloadStatus.CANCELLED) }
        .sortedWith(compareBy<DownloadItem> { if (it.status in activeStatuses) 0 else 1 }.thenBy { it.createdAt })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { TextButton(onClick = clearCompleted) { Text("Clear completed") } }
        if (queue.isEmpty()) item { Text("No downloads queued") }
        items(queue, key = { it.id }) { item ->
            DownloadCard(item,
                onRemove = if (item.status !in activeStatuses) ({ remove(item.id) }) else null,
                onRetry = if (item.status in setOf(DownloadStatus.FAILED, DownloadStatus.INTERRUPTED)) ({ retry(item.id) }) else null,
                onCancel = if (item.status in activeStatuses) ({ cancel(item.id) }) else null)
        }
    }
}
