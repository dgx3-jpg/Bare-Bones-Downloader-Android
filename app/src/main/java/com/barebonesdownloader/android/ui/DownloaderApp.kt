package com.barebonesdownloader.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.barebonesdownloader.android.domain.model.*
import com.barebonesdownloader.android.storage.SafFolders
import com.barebonesdownloader.android.ui.settings.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DownloaderApp(model: DownloaderViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val editing = entry?.destination?.route == "settings"
    val settings by model.settings.collectAsStateWithLifecycle()
    val draft by model.draft.collectAsStateWithLifecycle()
    val items by model.items.collectAsStateWithLifecycle()
    val url by model.url.collectAsStateWithLifecycle()
    val kind by model.kind.collectAsStateWithLifecycle()
    val log by model.log.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var prompt by rememberSaveable { mutableStateOf(false) }
    var pickerKind by rememberSaveable { mutableStateOf(DownloadKind.AUDIO) }
    var pickerIsDraft by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                scope.launch {
                    val info = withContext(Dispatchers.IO) { SafFolders.writable(context, uri.toString()) to SafFolders.name(context, uri) }
                    if (info.first) model.folderSelected(pickerKind, uri.toString(), info.second, pickerIsDraft)
                    else model.message.value = "Folder is not writable. Choose another folder."
                }
            } catch (_: SecurityException) { model.message.value = "Folder permission could not be saved. Choose another folder." }
        }
    }
    fun pick(type: DownloadKind, isDraft: Boolean) {
        pickerKind = type; pickerIsDraft = isDraft
        folderPicker.launch((if (isDraft) draft else settings)?.folder(type)?.let(Uri::parse))
    }
    fun discard() { model.discard(); nav.popBackStack() }
    BackHandler(enabled = editing) { discard() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); model.message.value = null } }
    Scaffold(topBar = { TopAppBar(title = { Text(if (editing) "Settings" else "Bare Bones Downloader") }, actions = {
        if (!editing) IconButton(onClick = { model.beginSettings(); nav.navigate("settings") }, enabled = settings != null,
            modifier = Modifier.testTag("open_settings")) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
    }) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text("Milestone 1 shell — placeholder data; downloads are not implemented.", style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            NavHost(nav, startDestination = "main", modifier = Modifier.weight(1f)) {
                composable("main") { MainScreen(url, { model.url.value = it }, kind, { model.kind.value = it }, settings,
                    items, log, { pick(kind, false) }, { prompt = model.add() }, model::cancelCurrent, model::clearWaiting,
                    { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Downloader log", log)) }) }
                composable("settings") {
                    draft?.let { current -> SettingsScreen(current, model::edit, { pick(it, true) }, ::discard, {
                        scope.launch {
                            val original = settings
                            val invalid = withContext(Dispatchers.IO) {
                                DownloadKind.entries.firstOrNull { current.folder(it) != original?.folder(it) &&
                                    current.folder(it)?.let { uri -> !SafFolders.writable(context, uri) } == true }
                            }
                            if (invalid != null) model.message.value = "${invalid.label} folder is unavailable. Choose another folder."
                            else model.save { nav.popBackStack() }
                        }
                    }) } ?: Text("Loading settings…", Modifier.padding(16.dp))
                }
            }
        }
    }
    if (prompt) AlertDialog(onDismissRequest = { prompt = false }, title = { Text("Choose ${if (kind == DownloadKind.AUDIO) "audio" else "video"} folder") },
        text = { Text("Choose a destination for this type, then tap Queue again. Cancelling preserves your links. No media is saved by this shell.") },
        confirmButton = { TextButton(onClick = { prompt = false; pick(kind, false) }) { Text("Choose folder") } },
        dismissButton = { TextButton(onClick = { prompt = false }) { Text("Cancel") } })
}
