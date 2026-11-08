package com.barebonesdownloader.android.ui.settings

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
import com.barebonesdownloader.android.R
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.barebonesdownloader.android.domain.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SettingsScreen(draft: AppSettings, edit: ((AppSettings) -> AppSettings) -> Unit,
    pick: (DownloadKind) -> Unit, cancel: () -> Unit, save: () -> Unit) {
    var notices by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    var section by rememberSaveable { mutableIntStateOf(0) }
    val errors = remember { mutableStateMapOf<String, Boolean>() }
    val sections = listOf("General", "Audio", "Video", "Metadata", "Appearance")
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = section, edgePadding = 0.dp) {
            sections.forEachIndexed { index, title -> Tab(selected = section == index, onClick = { section = index },
                text = { Text(title) }, modifier = Modifier.testTag("section_$title")) }
        }
        key(section) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp).testTag("settings_content"),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (section) {
                    0 -> {
                        DownloadKind.entries.forEach { type ->
                            Text(if (type == DownloadKind.AUDIO) "Audio folder" else "Video folder")
                            Text(draft.folderName(type) ?: "Not selected")
                            OutlinedButton(onClick = { pick(type) }, modifier = Modifier.testTag("folder_${type.name}")) { Text("Choose folder…") }
                        }
                        Check("Append source URL to audio and video filenames", draft.appendSourceUrl) { value -> edit { it.copy(appendSourceUrl = value) } }
                        Text("Adds a sanitized, bounded URL suffix and shortens the title.")
                        Text("Only save content you have rights or permission to download.")
                        TextButton(onClick = { notices = true }) { Text("Open-source notices") }
                    }
                    1 -> {
                        Choice("Output format", draft.audioFormat, AudioFormat.entries, { it.label }) { value -> edit { it.copy(audioFormat = value).normalized() } }
                        Choice("Encoding quality", draft.qualityMode, QualityMode.entries, { it.label },
                            { it == QualityMode.DEFAULT || it == QualityMode.VARIABLE && draft.audioFormat.variable || it == QualityMode.BITRATE && draft.audioFormat.bitrate }) {
                            value -> edit { it.copy(qualityMode = value) }
                        }
                        NumberInput("Quality (0 highest, 10 lowest)", draft.variableQuality, 0..10,
                            draft.qualityMode == QualityMode.VARIABLE && draft.audioFormat.variable, { errors["quality"] = it }) { v -> edit { it.copy(variableQuality = v) } }
                        NumberInput("Bitrate (kbps)", draft.bitrateKbps, 32..512,
                            draft.qualityMode == QualityMode.BITRATE && draft.audioFormat.bitrate, { errors["bitrate"] = it }) { v -> edit { it.copy(bitrateKbps = v) } }
                        Text("Quality is limited by the source audio. Default prefers compatible stream copy; explicit quality/bitrate requests re-encode.")
                        SizeCap("Audio", draft.audioMaxSizeMb, { errors["audioCap"] = it }) { v -> edit { it.copy(audioMaxSizeMb = v) } }
                        Text("Over-cap audio may lose quality; Best available/lossless output becomes AAC-in-M4A if reduction is needed. Tiny limits may be impossible. 1 MB = 1,000,000 bytes.")
                        Check("Add ReplayGain tags", draft.replayGainEnabled, tag = "replay_gain") { v -> edit { it.copy(replayGainEnabled = v) } }
                        Text("Audio only. Per-track ReplayGain 2.0 tags do not change samples. Not implemented in this shell; processing and verification belong to milestone 5.")
                    }
                    2 -> {
                        Choice("Maximum resolution", draft.videoMaxHeight, videoHeights, { if (it == 0) "Best available" else "${it}p" }) { v -> edit { it.copy(videoMaxHeight = v) } }
                        SizeCap("Video", draft.videoMaxSizeMb, { errors["videoCap"] = it }) { v -> edit { it.copy(videoMaxSizeMb = v) } }
                        Text("MP4 with H.264 video and AAC sound. Milestone 2 supports Best available resolution. Resolution controls arrive in milestone 3; decimal-MB size caps arrive in milestone 4.")
                        Text("Music metadata and ReplayGain are not applied to video.")
                    }
                    3 -> {
                        Text("Example tags (sample data)", style = MaterialTheme.typography.titleMedium)
                        Text("Here Comes the Sun — The Beatles — Abbey Road. Illustrative static example; no network request.")
                        Text("Preview format: ${draft.audioFormat.label}")
                        MetadataField.entries.forEach { field ->
                            Check(field.label, field in draft.metadata.enabled, tag = "metadata_${field.name}") { checked ->
                                edit { it.copy(metadata = MetadataSettings(if (checked) it.metadata.enabled + field else it.metadata.enabled - field)) }
                            }
                            val value = previewTags(draft).toMap()[field.label].orEmpty()
                            Text("${field.label}: $value", modifier = Modifier.testTag("preview_${field.name}"))
                        }
                    }
                    4 -> Choice("Theme", draft.theme, AppThemeMode.entries, { it.label }) { v -> edit { it.copy(theme = v) } }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TextButton(onClick = cancel, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("cancel_settings")) { Text("Cancel") }
            Button(onClick = save, enabled = errors.values.none { it }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("save_settings")) { Text("Save") }
        }
    }
    if (notices) AlertDialog(onDismissRequest = { notices = false }, title = { Text("Open-source notices") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Includes youtubedl-android 0.18.1 (GPL-3.0), FFmpeg, Python, QuickJS and AndroidX. No warranty. Corresponding source/build references and distribution obligations: THIRD_PARTY_NOTICES.md and docs/NATIVE_RUNTIME.md in the repository.")
            Text(remember { context.resources.openRawResource(R.raw.native_gpl3).bufferedReader().use { it.readText() } })
        } }, confirmButton = { TextButton(onClick = { notices = false }) { Text("Close") } })
}
@Composable private fun Check(label: String, checked: Boolean, tag: String = label, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag).toggleable(checked, role = Role.Checkbox, onValueChange = change),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Checkbox(checked, onCheckedChange = null); Text(label, Modifier.weight(1f))
    }
}
@Composable private fun NumberInput(label: String, value: Int, range: IntRange, enabled: Boolean,
    invalid: (Boolean) -> Unit, change: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val error = enabled && text.toIntOrNull()?.let { it !in range } != false
    LaunchedEffect(error) { invalid(error) }
    OutlinedTextField(text, onValueChange = { text = it; it.toIntOrNull()?.takeIf { v -> v in range }?.let(change) },
        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant),
        label = { Text(label) }, singleLine = true, enabled = enabled, isError = error,
        supportingText = { if (error) Text("Enter an integer from ${range.first} to ${range.last}.") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag(label))
}
@Composable private fun SizeCap(type: String, value: Int, invalid: (Boolean) -> Unit, change: (Int) -> Unit) {
    Check("Limit $type file size", value > 0) { change(if (it) 8 else 0) }
    NumberInput("$type maximum size (MB)", value, 0..16384, value > 0, invalid, change)
}
@Composable private fun <T> Choice(label: String, selected: T, choices: List<T>, text: (T) -> String,
    allowed: (T) -> Boolean = { true }, choose: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.testTag(label)) { Text(text(selected)) }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                choices.forEach { value -> DropdownMenuItem(text = { Text(text(value)) }, enabled = allowed(value), onClick = { choose(value); expanded = false }) }
            }
        }
    }
}
