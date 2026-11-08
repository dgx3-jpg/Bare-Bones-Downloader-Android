package com.barebonesdownloader.android.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.barebonesdownloader.android.domain.model.*

@Composable fun MainScreen(url: String, editUrl: (String) -> Unit, kind: DownloadKind, selectKind: (DownloadKind) -> Unit,
    settings: AppSettings?, items: List<DownloadItem>, log: String, changeFolder: () -> Unit, add: () -> Unit,
    cancel: () -> Unit, clear: () -> Unit, resume: () -> Unit, retry: (DownloadItem) -> Unit, replace: (DownloadItem) -> Unit, copyLog: () -> Unit) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("main_scroll"),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Download as", style = MaterialTheme.typography.titleMedium)
        DownloadKind.entries.forEach { option ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("mode_${option.name}")
                .selectable(selected = kind == option, role = Role.RadioButton, onClick = { selectKind(option) }), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = kind == option, onClick = null); Text(option.label)
            }
        }
        Text(if (kind == DownloadKind.AUDIO) "Audio format and size from Settings." else "MP4 with sound · Video quality and size from Settings.")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (kind == DownloadKind.AUDIO) "Audio folder" else "Video folder", Modifier.weight(1f))
            TextButton(onClick = changeFolder) { Text("Change…") }
        }
        Text(settings?.folderName(kind) ?: "No folder selected", Modifier.testTag("selected_folder"))
        OutlinedTextField(value = url, onValueChange = editUrl, label = { Text("Links (one per line)") },
            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant),
            placeholder = { Text("Paste one or more links") }, minLines = 3, maxLines = 6,
            modifier = Modifier.fillMaxWidth().testTag("links_input"))
        TextButton(onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.let { editUrl(it.toString()) }
        }) { Text("Paste links") }
        Button(onClick = add, enabled = settings != null, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("queue_button")) {
            Text(if (kind == DownloadKind.AUDIO) "Queue audio" else "Queue video")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = cancel, enabled = items.any { it.status in activeStatuses || it.status == DownloadStatus.BLOCKED }, modifier = Modifier.weight(1f)) { Text("Cancel current") }
            OutlinedButton(onClick = clear, enabled = items.any { it.status == DownloadStatus.QUEUED }, modifier = Modifier.weight(1f)) { Text("Clear waiting") }
        }
        if (items.any { it.status == DownloadStatus.QUEUED } && items.none { it.status in activeStatuses }) OutlinedButton(onClick = resume) { Text("Resume queue") }
        Text("Queue", style = MaterialTheme.typography.titleMedium)
        if (items.isEmpty()) Text("No downloads queued")
        items.forEach { item ->
            Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.title ?: "Untitled item")
                    Text(item.sourceUrl, style = MaterialTheme.typography.bodySmall)
                    Text("Type: ${item.kind.label}")
                    Text("Status: ${item.status.label}", Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    item.errorMessage?.let { Text(it) }
                    if (item.status in setOf(DownloadStatus.FAILED, DownloadStatus.INTERRUPTED, DownloadStatus.CANCELLED)) TextButton(onClick = { retry(item) }) { Text("Retry") }
                    if (item.status == DownloadStatus.BLOCKED) TextButton(onClick = { replace(item) }) { Text("Choose replacement…") }
                    item.progressPercent?.let { LinearProgressIndicator(progress = { it / 100f }, modifier = Modifier.fillMaxWidth()) }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.weight(1f)) { Text(if (expanded) "Log — collapse" else "Log — expand") }
            TextButton(onClick = copyLog) { Text("Copy log") }
        }
        if (expanded) Text(log, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("diagnostics_log"))
    }
}
