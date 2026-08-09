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
    val useDarkTheme: Boolean = true,
    val pullHintDismissed: Boolean = false
)

class SettingsRepository(private val context: Context) {
    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            useDarkTheme = prefs[DARK_THEME_KEY] ?: true,
            pullHintDismissed = prefs[PULL_HINT_DISMISSED_KEY] ?: false
        )
    }

    suspend fun update(darkTheme: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[DARK_THEME_KEY] = darkTheme
        }
    }

    suspend fun setPullHintDismissed(dismissed: Boolean = true) {
        context.dataStore.edit { prefs ->
            prefs[PULL_HINT_DISMISSED_KEY] = dismissed
        }
    }

    companion object {
        private val DARK_THEME_KEY = booleanPreferencesKey("use_dark_theme")
        private val PULL_HINT_DISMISSED_KEY = booleanPreferencesKey("pull_hint_dismissed")
    }
}
