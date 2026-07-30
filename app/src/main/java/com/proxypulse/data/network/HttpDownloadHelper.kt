package com.proxypulse.data.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

object HttpDownloadHelper {
    const val DEFAULT_TIMEOUT_MS = 20_000L

    private const val USER_AGENT_DESKTOP =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private const val USER_AGENT_MOBILE =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val cookieJar = object : CookieJar {
        private val store = ConcurrentHashMap<String, List<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            if (cookies.isNotEmpty()) {
                store[url.host] = cookies
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            store[url.host] ?: emptyList()
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .cookieJar(cookieJar)
        .build()

    suspend fun download(
        url: String,
        referer: String? = null,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        maxAttempts: Int = 3
    ): String = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        repeat(maxAttempts.coerceAtLeast(1)) { attempt ->
            coroutineContext.ensureActive()
            for (userAgent in userAgentsForAttempt(attempt)) {
                try {
                    return@withContext downloadOnce(url, referer, timeoutMs, userAgent)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                }
            }
            if (attempt < maxAttempts - 1) {
                delay(600L * (attempt + 1))
            }
        }
        throw lastError ?: IllegalStateException("Download failed")
    }

    private fun userAgentsForAttempt(attempt: Int): List<String> =
        if (attempt == 0) {
            listOf(USER_AGENT_DESKTOP, USER_AGENT_MOBILE)
        } else {
            listOf(USER_AGENT_MOBILE, USER_AGENT_DESKTOP)
        }

    private fun downloadOnce(
        url: String,
        referer: String?,
        timeoutMs: Long,
        userAgent: String
    ): String {
        val perCallClient = client.newBuilder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()

        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")

        if (!referer.isNullOrEmpty()) {
            builder.header("Referer", referer)
        }

        perCallClient.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} for $url")
            }
            if (body.isEmpty()) {
                throw IllegalStateException("Empty response for $url")
            }
            return body
        }
    }
}
