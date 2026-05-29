package com.proxypulse.data.network

import com.proxypulse.data.tgstat.TgStatCookieParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/** HTTP-клиент с отдельным cookie-jar (для TGStat-авторизации и сбора). */
class HttpSession(initialCookieHeader: String? = null) {
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    init {
        if (!initialCookieHeader.isNullOrBlank()) {
            TgStatCookieParser.parsePairs(initialCookieHeader).forEach { (name, value) ->
                addCookie("tgstat.com", name, value)
                addCookie("tgstat.ru", name, value)
            }
        }
    }

    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            if (cookies.isEmpty()) return
            val host = url.host
            val list = cookieStore.getOrPut(host) { mutableListOf() }
            for (cookie in cookies) {
                list.removeAll { it.name.equals(cookie.name, ignoreCase = true) }
                list.add(cookie)
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            cookieStore[url.host] ?: emptyList()
    }

    private val baseClient: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .cookieJar(cookieJar)
        .build()

    suspend fun download(
        url: String,
        referer: String? = null,
        timeoutMs: Long = HttpDownloadHelper.DEFAULT_TIMEOUT_MS,
        maxAttempts: Int = 3
    ): String = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        repeat(maxAttempts.coerceAtLeast(1)) { attempt ->
            coroutineContext.ensureActive()
            for (userAgent in userAgentsForAttempt(attempt)) {
                try {
                    return@withContext downloadOnce(url, referer, timeoutMs, userAgent)
                } catch (e: kotlinx.coroutines.CancellationException) {
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

    suspend fun postForm(
        url: String,
        referer: String?,
        formBody: String,
        timeoutMs: Long = HttpDownloadHelper.DEFAULT_TIMEOUT_MS
    ): String = withContext(Dispatchers.IO) {
        postFormOnce(url, referer, formBody, timeoutMs, USER_AGENT_DESKTOP)
    }

    fun exportCookieHeader(): String = TgStatCookieParser.toCookieHeader(cookieStore)

    private fun addCookie(host: String, name: String, value: String) {
        val list = cookieStore.getOrPut(host) { mutableListOf() }
        list.removeAll { it.name.equals(name, ignoreCase = true) }
        list.add(
            Cookie.Builder()
                .domain(host)
                .path("/")
                .name(name)
                .value(value)
                .secure()
                .build()
        )
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
        val client = baseClient.newBuilder()
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

        client.newCall(builder.build()).execute().use { response ->
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

    private fun postFormOnce(
        url: String,
        referer: String?,
        formBody: String,
        timeoutMs: Long,
        userAgent: String
    ): String {
        val client = baseClient.newBuilder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()

        val bodyBuilder = FormBody.Builder()
        for (part in formBody.split('&')) {
            if (part.isBlank()) continue
            val eq = part.indexOf('=')
            if (eq <= 0) continue
            val key = java.net.URLDecoder.decode(part.substring(0, eq), Charsets.UTF_8.name())
            val value = java.net.URLDecoder.decode(part.substring(eq + 1), Charsets.UTF_8.name())
            bodyBuilder.add(key, value)
        }

        val builder = Request.Builder()
            .url(url)
            .post(bodyBuilder.build())
            .header("User-Agent", userAgent)
            .header("Accept", "application/json, text/html, */*")
            .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
            .header("Content-Type", "application/x-www-form-urlencoded")

        if (!referer.isNullOrEmpty()) {
            builder.header("Referer", referer)
        }

        client.newCall(builder.build()).execute().use { response ->
            return response.body?.string() ?: ""
        }
    }

    companion object {
        private const val USER_AGENT_DESKTOP =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val USER_AGENT_MOBILE =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
