package com.barebones.downloader.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import com.barebones.downloader.R
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.barebones.downloader.BuildConfig
import com.barebones.downloader.domain.model.*

@Composable fun SettingsScreen(settings: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit, pickFolder: () -> Unit) {
    var preview by rememberSaveable { mutableStateOf(false) }
    var licenses by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val licenseText = remember { context.resources.openRawResource(R.raw.apache_license).bufferedReader().use { it.readText() } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("settings_scroll"),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Section("Downloads")
        Text("Save folder", style = MaterialTheme.typography.titleMedium)
        Text(settings.saveFolderUri ?: "Choose a folder before the first download.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = pickFolder) { Text("Choose folder") }
        Text("Output type: Audio only")
        Choice("Audio format", settings.audioFormat, AudioFormat.entries, { it.label }) { value -> update { it.copy(audioFormat = value) } }
        if (settings.audioFormat in setOf(AudioFormat.FLAC, AudioFormat.WAV)) {
            Text("Audio quality: Source-limited")
            Text("FLAC/WAV do not improve lossy source quality.")
        } else {
            Choice("Audio quality", settings.audioQuality, AudioQuality.entries, { it.label }) { value -> update { it.copy(audioQuality = value) } }
        }
        if (settings.audioFormat == AudioFormat.WAV) Text("WAV artwork and metadata compatibility varies between players.")
        Text("Filename template", style = MaterialTheme.typography.titleMedium)
        Text("{artist} - {title}.{ext}")
        Text("When artist/title are unreliable: {source} - {title_or_id}.{ext}", style = MaterialTheme.typography.bodySmall)
        Section("Metadata")
        MetadataField.entries.forEach { field ->
            SettingSwitch(field.label, field in settings.metadata.enabled, Modifier.testTag("metadata_${field.name}")) { checked ->
                update { current -> current.copy(metadata = MetadataSettings(if (checked) current.metadata.enabled + field else current.metadata.enabled - field)) }
            }
        }
        OutlinedButton(onClick = { preview = true }, modifier = Modifier.testTag("preview_tags")) { Text("Preview tags") }
        Section("Audio processing")
        SettingSwitch("ReplayGain", settings.replayGainEnabled, Modifier.testTag("replay_gain")) { value -> update { it.copy(replayGainEnabled = value) } }
        Text("ReplayGain target: -18 LUFS integrated loudness (tag-only)")
        Text("Writes compatible loudness metadata where supported; playback depends on your music player.")
        Section("About")
        Text("App version: ${BuildConfig.VERSION_NAME}")
        Text("yt-dlp version: Not integrated (milestone 2)")
        Text("FFmpeg version/license: Not integrated (milestone 2)")
        OutlinedButton(onClick = {}, enabled = false) { Text("Check extractor updates") }
        Text("Extractor updates become available with the download engine.", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { licenses = true }) { Text("Open-source licenses") }
        Text("Only download content you have rights or permission to save. Respect site restrictions and applicable terms.")
    }
    if (preview) AlertDialog(onDismissRequest = { preview = false }, title = { Text("Illustrative tag preview") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Static example — Come Together — The Beatles. No link was fetched.")
                previewTags(settings.metadata).forEach { (label, value) -> Text("$label: $value") }
            }
        }, confirmButton = { TextButton(onClick = { preview = false }) { Text("Close") } })
    if (licenses) AlertDialog(onDismissRequest = { licenses = false }, title = { Text("Open-source licenses") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("AndroidX / Jetpack Compose / Material 3 / Room / DataStore: Apache License 2.0")
            Text("Kotlin / kotlinx.coroutines / kotlinx.serialization: Apache License 2.0")
            Text("Dependency inventory: see THIRD_PARTY_NOTICES.md in the repository. No yt-dlp or FFmpeg binary is bundled.")
            Text(licenseText)
        } }, confirmButton = { TextButton(onClick = { licenses = false }) { Text("Close") } })
}

@Composable private fun Section(label: String) { HorizontalDivider(); Text(label, style = MaterialTheme.typography.titleLarge) }

@Composable private fun SettingSwitch(label: String, checked: Boolean, modifier: Modifier = Modifier, change: (Boolean) -> Unit) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = checked, role = Role.Switch, onValueChange = change),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(1f)); Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable private fun <T> Choice(label: String, selected: T, choices: List<T>, text: (T) -> String, choose: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.testTag(label)) { Text(text(selected)) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                choices.forEach { value -> DropdownMenuItem(text = { Text(text(value)) }, onClick = { choose(value); expanded = false }) }
            }
        }
    }
}
