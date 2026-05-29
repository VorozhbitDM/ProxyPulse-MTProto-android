package com.proxypulse.data.tgstat

import com.proxypulse.data.feed.ProxyLinkParser
import com.proxypulse.data.network.HttpSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class TgStatAuthResult(
    val isAuthenticated: Boolean,
    val canPaginate: Boolean,
    val message: String
)

data class TgStatLoginStartResult(
    val success: Boolean,
    val message: String,
    val authKey: String = "",
    val session: HttpSession? = null,
    val telegramDeepLink: String = ""
)

data class TgStatPollResult(
    val isAuthenticated: Boolean,
    val message: String
)

object TgStatAuthService {
    private const val LOGIN_URL = "https://tgstat.com/login"
    private const val AUTH_URL = "https://tgstat.com/auth"
    private const val TEST_CHANNEL_URL = "https://tgstat.com/channel/@ProxyMTProto"
    const val BOT_USERNAME = "tg_analytics_bot"
    const val BOT_USERNAME_FOR_UI = BOT_USERNAME
    private const val POLL_INTERVAL_MS = 2000L
    private const val AUTH_TIMEOUT_MS = 180_000L

    private val authButtonRegex = Regex("""data-telegram-auth-button="([^"]+)"""", RegexOption.IGNORE_CASE)

    suspend fun startTelegramLogin(): TgStatLoginStartResult {
        return try {
            val session = HttpSession()
            val html = session.download(LOGIN_URL, timeoutMs = 20_000L, maxAttempts = 2)
            val authKey = parseAuthKey(html)
                ?: return TgStatLoginStartResult(
                    success = false,
                    message = "Не удалось получить ключ входа TGStat."
                )
            TgStatLoginStartResult(
                success = true,
                authKey = authKey,
                session = session,
                telegramDeepLink = buildTelegramDeepLink(authKey),
                message = "Откройте Telegram и нажмите Start у @$BOT_USERNAME."
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TgStatLoginStartResult(success = false, message = e.message ?: e.toString())
        }
    }

    suspend fun pollTelegramAuth(session: HttpSession?, authKey: String?): TgStatPollResult {
        if (session == null || authKey.isNullOrEmpty()) {
            return TgStatPollResult(false, "Сессия входа не инициализирована.")
        }
        return try {
            val body = "auth_key=${encode(authKey)}"
            val response = session.postForm(AUTH_URL, LOGIN_URL, body)
            if (isAuthOk(response)) {
                TgStatPollResult(true, "TGStat принял вход.")
            } else {
                TgStatPollResult(false, "Ожидание Start у @$BOT_USERNAME…")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TgStatPollResult(false, e.message ?: e.toString())
        }
    }

    suspend fun waitForTelegramAuth(
        session: HttpSession,
        authKey: String,
        progress: ((String) -> Unit)? = null
    ): TgStatAuthResult {
        val deadline = System.currentTimeMillis() + AUTH_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val poll = pollTelegramAuth(session, authKey)
            progress?.invoke(poll.message)
            if (poll.isAuthenticated) return testSession(session)
            delay(POLL_INTERVAL_MS)
        }
        return fail("Время ожидания истекло. Нажмите Start у @$BOT_USERNAME и повторите вход.")
    }

    fun buildTelegramDeepLink(authKey: String): String =
        "tg://resolve?domain=$BOT_USERNAME&start=${encode(authKey)}"

    fun buildTelegramHttpsLink(authKey: String): String =
        "https://t.me/$BOT_USERNAME?start=${encode(authKey)}"

    suspend fun testSession(session: HttpSession): TgStatAuthResult {
        return try {
            val html = session.download(TEST_CHANNEL_URL, timeoutMs = 20_000L, maxAttempts = 2)
            if (html.contains("Sign In", ignoreCase = true) &&
                html.contains("popup_ajax", ignoreCase = true) &&
                html.contains("""data-src="/login"""", ignoreCase = true) &&
                !html.contains("data-post=", ignoreCase = true)
            ) {
                return fail("Сессия не принята TGStat. Повторите вход через Telegram.")
            }

            val csrf = arrayOf("")
            val page = arrayOf("")
            val offset = intArrayOf(0)
            if (!TgStatFeedHelper.tryParsePaginationForm(html, csrf, page, offset)) {
                return TgStatAuthResult(
                    isAuthenticated = true,
                    canPaginate = false,
                    message = "Страница канала загружена, но форма пагинации не найдена."
                )
            }

            val postsUrl = TgStatFeedHelper.getPostsEndpoint(TEST_CHANNEL_URL, html)
            val response = session.postForm(
                postsUrl,
                TEST_CHANNEL_URL,
                TgStatFeedHelper.buildPostsBody(csrf[0], page[0], offset[0])
            )

            if (TgStatFeedHelper.isRestrictedResponse(response)) {
                return TgStatAuthResult(
                    isAuthenticated = false,
                    canPaginate = false,
                    message = "Сессия сохранена, но TGStat всё ещё требует вход для «Show more»."
                )
            }

            val chunkHtml = TgStatFeedHelper.extractPostsHtml(response)
            val hasPosts = chunkHtml.isNotEmpty() && ProxyLinkParser.looksLikeTelegramFeed(chunkHtml)
            return TgStatAuthResult(
                isAuthenticated = true,
                canPaginate = hasPosts,
                message = if (hasPosts) {
                    "Вход подтверждён: следующие страницы TGStat доступны."
                } else {
                    "Ответ получен, но вторая страница пустая."
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(e.message ?: e.toString())
        }
    }

    suspend fun testCookieHeader(cookieHeader: String): TgStatAuthResult {
        if (cookieHeader.isBlank()) return fail("Cookies не заданы")
        return testSession(HttpSession(cookieHeader))
    }

    private fun parseAuthKey(html: String?): String? {
        if (html.isNullOrEmpty()) return null
        val match = authButtonRegex.find(html) ?: return null
        val key = match.groupValues[1].trim()
        return key.ifEmpty { null }
    }

    private fun isAuthOk(response: String?): Boolean =
        !response.isNullOrEmpty() && response.contains("\"status\":\"ok\"", ignoreCase = true)

    private fun fail(message: String) = TgStatAuthResult(
        isAuthenticated = false,
        canPaginate = false,
        message = message
    )

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
