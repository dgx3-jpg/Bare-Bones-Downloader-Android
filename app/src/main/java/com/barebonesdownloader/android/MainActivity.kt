package com.barebonesdownloader.android

import android.os.Bundle
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as DownloaderApplication
        setContent {
            val model: DownloaderViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = DownloaderViewModel(app.settings, app.downloads, app.diagnostics) as T
                })
            val preferences by model.settings.collectAsStateWithLifecycle()
            val theme = preferences?.theme ?: AppThemeMode.LIGHT
            SideEffect {
                val transparent = android.graphics.Color.TRANSPARENT
                val style = if (theme == AppThemeMode.DARK) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            DownloaderTheme(theme) { DownloaderApp(model) }
        }
    }
}
