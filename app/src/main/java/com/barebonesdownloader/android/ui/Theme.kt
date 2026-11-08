package com.barebonesdownloader.android.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.barebonesdownloader.android.domain.model.AppThemeMode

private val DarkPalette = darkColorScheme(
    primary = Color(0xFF9290C3), onPrimary = Color(0xFF070F2B),
    primaryContainer = Color(0xFF535C91), onPrimaryContainer = Color(0xFFF7F6FF),
    secondary = Color(0xFF9290C3), background = Color(0xFF070F2B), surface = Color(0xFF070F2B),
    surfaceVariant = Color(0xFF1B1A55), onSurfaceVariant = Color(0xFFF7F6FF),
    onBackground = Color(0xFFF7F6FF), onSurface = Color(0xFFF7F6FF), outline = Color(0xFF9290C3))
private val LightPalette = lightColorScheme(primary = Color(0xFF535C91), background = Color(0xFFF5F5F5), surface = Color.White)
@Composable fun DownloaderTheme(theme: AppThemeMode = AppThemeMode.LIGHT, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (theme == AppThemeMode.DARK) DarkPalette else LightPalette, content = content)
}
