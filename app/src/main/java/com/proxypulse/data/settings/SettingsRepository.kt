package com.proxypulse.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.proxypulse.domain.FeedSourceMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

private const val DEFAULT_MAX_PROXIES = 100

data class AppSettings(
    val maxProxiesToCollect: Int = DEFAULT_MAX_PROXIES,
    val useDarkTheme: Boolean = true,
    val feedSource: FeedSourceMode = FeedSourceMode.Bypass,
    val tgStatCookieHeader: String = ""
) {
    val hasTgStatSession: Boolean get() = tgStatCookieHeader.isNotBlank()
}

class SettingsRepository(private val context: Context) {
    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            maxProxiesToCollect = snapProxyLimit(
                prefs[MAX_PROXIES_KEY] ?: DEFAULT_MAX_PROXIES
            ),
            useDarkTheme = prefs[DARK_THEME_KEY] ?: true,
            feedSource = parseFeedSource(prefs[FEED_SOURCE_KEY]),
            tgStatCookieHeader = prefs[TGSTAT_COOKIES_KEY] ?: ""
        )
    }

    suspend fun update(
        maxProxies: Int,
        darkTheme: Boolean,
        feedSource: FeedSourceMode
    ) {
        context.dataStore.edit { prefs ->
            prefs[MAX_PROXIES_KEY] = snapProxyLimit(maxProxies)
            prefs[DARK_THEME_KEY] = darkTheme
            prefs[FEED_SOURCE_KEY] = feedSource.name.lowercase()
        }
    }

    suspend fun setTgStatCookies(cookieHeader: String) {
        context.dataStore.edit { prefs ->
            if (cookieHeader.isBlank()) {
                prefs.remove(TGSTAT_COOKIES_KEY)
            } else {
                prefs[TGSTAT_COOKIES_KEY] = cookieHeader.trim()
            }
        }
    }

    suspend fun clearTgStatCookies() = setTgStatCookies("")

    companion object {
        const val DEFAULT_MAX = DEFAULT_MAX_PROXIES
        const val MIN_MAX = 10
        const val MAX_MAX = 500

        private val MAX_PROXIES_KEY = intPreferencesKey("max_proxies_to_collect")
        private val DARK_THEME_KEY = booleanPreferencesKey("use_dark_theme")
        private val FEED_SOURCE_KEY = stringPreferencesKey("feed_source")
        private val TGSTAT_COOKIES_KEY = stringPreferencesKey("tgstat_cookies")

        fun clamp(value: Int): Int = value.coerceIn(MIN_MAX, MAX_MAX)

        fun snapProxyLimit(value: Int): Int {
            val c = clamp(value)
            return ((c + 5) / 10 * 10).coerceIn(MIN_MAX, MAX_MAX)
        }

        private fun parseFeedSource(raw: String?): FeedSourceMode = when (raw?.lowercase()) {
            "direct" -> FeedSourceMode.Direct
            "mirrors", "archive", "bypass" -> FeedSourceMode.Bypass
            else -> FeedSourceMode.Bypass
        }
    }
}
