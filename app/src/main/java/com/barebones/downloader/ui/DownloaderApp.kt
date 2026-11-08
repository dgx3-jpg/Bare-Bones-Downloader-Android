package com.barebones.downloader.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.barebones.downloader.ui.home.HomeScreen
import com.barebones.downloader.ui.queue.QueueScreen
import com.barebones.downloader.ui.history.HistoryScreen
import com.barebones.downloader.ui.settings.SettingsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DownloaderApp(model: DownloaderViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "Home"
    val settings by model.settings.collectAsStateWithLifecycle()
    val items by model.items.collectAsStateWithLifecycle()
    val url by model.url.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var folderPrompt by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                model.folderSelected(uri.toString())
            } catch (_: SecurityException) {
                model.message.value = "Folder permission could not be saved. Choose another folder."
            }
        }
    }
    fun pickFolder() { folderPicker.launch(settings?.saveFolderUri?.let(Uri::parse)) }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); model.message.value = null } }
    Scaffold(
        topBar = { TopAppBar(title = { Text(if (route == "Home") "Downloader" else route) }) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                listOf("Home" to Icons.Default.Home, "Queue" to Icons.Default.List,
                    "History" to Icons.Default.DateRange, "Settings" to Icons.Default.Settings).forEach { (name, icon) ->
                    NavigationBarItem(modifier = Modifier.testTag("nav_$name"), selected = route == name,
                        onClick = { nav.navigate(name) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true; restoreState = true
                        } }, icon = { Icon(icon, contentDescription = null) }, label = { Text(name) })
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text("Milestone 1 demo — fake data; no downloads or media files.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            NavHost(nav, startDestination = "Home", modifier = Modifier.weight(1f)) {
                composable("Home") { HomeScreen(url, { model.url.value = it }, settings != null,
                    { folderPrompt = model.add() }, items.firstOrNull { it.status in com.barebones.downloader.domain.model.activeStatuses }) }
                composable("Queue") { QueueScreen(items, model::remove, model::retry, model::cancel, model::clearCompleted) }
                composable("History") { HistoryScreen(items, model::remove, model::retry, model::clearHistory) }
                composable("Settings") {
                    settings?.let { SettingsScreen(it, model::update, ::pickFolder) } ?: Text("Loading settings…", Modifier.padding(16.dp))
                }
            }
        }
    }
    if (folderPrompt) AlertDialog(onDismissRequest = { folderPrompt = false },
        title = { Text("Choose a save folder") },
        text = { Text("Select a folder before adding an item. After choosing, tap Add to queue again. This demo does not save media.") },
        confirmButton = { TextButton(onClick = { folderPrompt = false; pickFolder() }) { Text("Choose folder") } },
        dismissButton = { TextButton(onClick = { folderPrompt = false }) { Text("Cancel") } })
}
