package com.proxypulse.data.feed

import com.proxypulse.data.network.HttpDownloadHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/** «Местный» IP — регионы, где t.me обычно недоступен; за рубежом пробуем живую ленту. */
object FeedRouteHelper {
    private const val GEO_LOOKUP_TIMEOUT_MS = 5000
    private const val TELEGRAM_PROBE_TIMEOUT_MS = 12_000L

    private val LIVE_FEED_URLS = listOf(
        "https://t.me/s/ProxyMTProto",
        "https://telegram.me/s/ProxyMTProto"
    )

    private val DOMESTIC_COUNTRY_CODES = setOf("RU", "BY", "KZ", "UZ", "TM", "IR", "CN")

    suspend fun shouldUseArchivePath(): Boolean {
        if (isDomesticIp()) return true
        return !canReachLiveTelegramFeed()
    }

    suspend fun isDomesticIp(): Boolean {
        val code = tryGetCountryCode() ?: return true
        return code.uppercase() in DOMESTIC_COUNTRY_CODES
    }

    suspend fun canReachLiveTelegramFeed(): Boolean = withContext(Dispatchers.IO) {
        for (url in LIVE_FEED_URLS) {
            coroutineContext.ensureActive()
            try {
                val html = HttpDownloadHelper.download(
                    url = url,
                    timeoutMs = TELEGRAM_PROBE_TIMEOUT_MS,
                    maxAttempts = 2
                )
                if (ProxyLinkParser.looksLikeTelegramFeed(html)) return@withContext true
            } catch (_: Exception) {
                // next mirror
            }
        }
        false
    }

    private suspend fun tryGetCountryCode(): String? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL("http://ip-api.com/json/?fields=status,countryCode").openConnection()
                as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = GEO_LOOKUP_TIMEOUT_MS
                readTimeout = GEO_LOOKUP_TIMEOUT_MS
                setRequestProperty("User-Agent", "ProxyPulse/2.9")
            }
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            if (!json.contains("\"status\":\"success\"") && !json.contains("\"status\": \"success\"")) {
                return@withContext null
            }
            val m = Pattern.compile("\"countryCode\"\\s*:\\s*\"([A-Za-z]{2})\"").matcher(json)
            if (m.find()) m.group(1)?.uppercase() else null
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
