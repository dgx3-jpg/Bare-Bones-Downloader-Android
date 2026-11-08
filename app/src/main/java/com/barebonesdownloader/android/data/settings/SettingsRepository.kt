package com.barebonesdownloader.android.data.settings

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.barebonesdownloader.android.domain.model.AppSettings
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

val Context.downloaderSettings by preferencesDataStore(name = "downloader_settings", corruptionHandler = ReplaceFileCorruptionHandler {
    Log.w("Settings", "Corrupt preferences file replaced with defaults."); emptyPreferences()
})
private val settingsJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }
fun decodeSettings(value: String?, problem: (String) -> Unit = {}): AppSettings {
    if (value == null) return AppSettings()
    return try {
        val fields = settingsJson.parseToJsonElement(value).jsonObject
        for ((name, choices) in mapOf("theme" to com.barebonesdownloader.android.domain.model.AppThemeMode.entries.map { it.name },
            "audioFormat" to com.barebonesdownloader.android.domain.model.AudioFormat.entries.map { it.name },
            "qualityMode" to com.barebonesdownloader.android.domain.model.QualityMode.entries.map { it.name })) {
            fields[name]?.jsonPrimitive?.contentOrNull?.let { if (it !in choices) problem("Invalid $name reset to default.") }
        }
        val decoded = settingsJson.decodeFromString<AppSettings>(value)
        val normalized = decoded.normalized()
        if (decoded != normalized) problem("Invalid settings values reset to defaults.")
        normalized
    } catch (_: IllegalArgumentException) {
        problem("Saved settings could not be read; defaults restored."); AppSettings()
    }
}
interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {
    private val key = stringPreferencesKey("settings_v2")
    private fun decode(value: String?) = decodeSettings(value) { Log.w("Settings", it) }
    override val settings = store.data.map { decode(it[key]) }
    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { it[key] = settingsJson.encodeToString(transform(decode(it[key])).normalized()) }
    }
}
