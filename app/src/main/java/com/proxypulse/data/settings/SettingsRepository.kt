package com.proxypulse.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

data class AppSettings(
    val useDarkTheme: Boolean = true
)

class SettingsRepository(private val context: Context) {
    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            useDarkTheme = prefs[DARK_THEME_KEY] ?: true
        )
    }

    suspend fun update(darkTheme: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[DARK_THEME_KEY] = darkTheme
        }
    }

    companion object {
        private val DARK_THEME_KEY = booleanPreferencesKey("use_dark_theme")
    }
}
