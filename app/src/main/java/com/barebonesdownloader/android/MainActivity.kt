package com.barebonesdownloader.android

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.barebonesdownloader.android.domain.model.AppThemeMode
import com.barebonesdownloader.android.ui.*

fun sharedPlainText(intent: Intent?): String? = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain")
    runCatching { intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.take(60000)?.trim()?.takeIf(String::isNotEmpty) }.getOrNull() else null

class MainActivity : ComponentActivity() {
    private var incomingShare by mutableStateOf<String?>(null)
    private var shareVersion by mutableIntStateOf(0)
    private fun receiveShare(intent: Intent?) { sharedPlainText(intent)?.let { incomingShare = it; shareVersion++ } }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent); receiveShare(intent)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) receiveShare(intent)
        enableEdgeToEdge()
        val app = application as DownloaderApplication
        setContent {
            val model: DownloaderViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = DownloaderViewModel(app.settings, app.downloads, app.diagnostics) as T
                })
            LaunchedEffect(incomingShare) { incomingShare?.let { model.prefillShare(it); incomingShare = null } }
            val preferences by model.settings.collectAsStateWithLifecycle()
            val theme = preferences?.theme ?: AppThemeMode.LIGHT
            SideEffect {
                val transparent = android.graphics.Color.TRANSPARENT
                val style = if (theme == AppThemeMode.DARK) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            DownloaderTheme(theme) { DownloaderApp(model, shareVersion) }
        }
    }
}
