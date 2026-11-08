package com.barebones.downloader.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.barebones.downloader.domain.model.*
import java.text.DateFormat
import java.util.Date

@Composable fun DownloadCard(item: DownloadItem, history: Boolean = false,
    onRemove: (() -> Unit)? = null, onRetry: (() -> Unit)? = null, onCancel: (() -> Unit)? = null) {
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "No thumbnail available", modifier = Modifier.padding(16.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(item.title ?: "Untitled media", maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium)
                    Text(item.sourceHost.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    Text(item.status.label + (item.progressPercent?.let { " — $it%" } ?: ""))
                }
            }
            if (item.status in activeStatuses || item.status == DownloadStatus.QUEUED) {
                item.progressPercent?.let { LinearProgressIndicator(progress = { it.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth()) }
                    ?: LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            item.errorMessage?.let { Text(it) }
            if (history) {
                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.completedAt ?: item.updatedAt)),
                    style = MaterialTheme.typography.bodySmall)
                Text(item.outputFormat.label)
                Text(item.outputFilename ?: "No output file")
                if (item.status == DownloadStatus.COMPLETED) {
                    Row {
                        TextButton(onClick = {}, enabled = false) { Text("Open") }
                        TextButton(onClick = {}, enabled = false) { Text("Share") }
                    }
                    Text("Demo only — no file to open or share.", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onCancel?.let { TextButton(onClick = it) { Text("Cancel") } }
                onRetry?.let { TextButton(onClick = it) { Text("Retry") } }
                onRemove?.let { TextButton(onClick = it) { Text(if (history) "Remove from history" else "Remove") } }
            }
        }
    }
}
