package com.oxlo.gallary.data.settings

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "gallary_settings")

enum class ThemeMode { SYSTEM, DARK, LIGHT }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = false,
    val gridColumns: Int = 3,
    val autoFaceDetection: Boolean = true,
    val castEnabled: Boolean = true
)

class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.settingsStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.DARK,
            dynamicColor = prefs[DYNAMIC] ?: false,
            gridColumns = prefs[COLUMNS] ?: 3,
            autoFaceDetection = prefs[FACES] ?: true,
            castEnabled = prefs[CAST] ?: true
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[THEME] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) = edit { it[DYNAMIC] = enabled }

    suspend fun setGridColumns(columns: Int) = edit { it[COLUMNS] = columns.coerceIn(2, 6) }

    suspend fun setAutoFaceDetection(enabled: Boolean) = edit { it[FACES] = enabled }

    suspend fun setCastEnabled(enabled: Boolean) = edit { it[CAST] = enabled }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.settingsStore.edit { prefs -> block(prefs) }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val COLUMNS = intPreferencesKey("grid_columns")
        val FACES = booleanPreferencesKey("auto_face_detection")
        val CAST = booleanPreferencesKey("cast_enabled")
    }
}
