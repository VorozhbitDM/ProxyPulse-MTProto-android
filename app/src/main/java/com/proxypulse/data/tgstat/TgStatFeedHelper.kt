package com.proxypulse.data.tgstat

import com.proxypulse.data.feed.ProxyLinkParser
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object TgStatFeedHelper {
    private val csrfRegex = Regex("""name="_tgstat_csrk"\s+value="([^"]+)"""", RegexOption.IGNORE_CASE)
    private val metaCsrfRegex = Regex("""name="csrf-token"\s+content="([^"]+)"""", RegexOption.IGNORE_CASE)
    private val pageRegex = Regex("""class="lm-page"[^>]*value="([^"]+)"""", RegexOption.IGNORE_CASE)
    private val offsetRegex = Regex("""class="lm-offset"[^>]*value="([^"]+)"""", RegexOption.IGNORE_CASE)
    private val formActionRegex = Regex(
        """<form[^>]*class="[^"]*lm-form[^"]*"[^>]*action="([^"]+)"""",
        RegexOption.IGNORE_CASE
    )
    private val jsonPageRegex = Regex(""""page"\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
    private val jsonOffsetRegex = Regex(""""offset"\s*:\s*(\d+)""", RegexOption.IGNORE_CASE)

    fun isTgStatUrl(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        return url.contains("tgstat.com", ignoreCase = true) ||
            url.contains("tgstat.ru", ignoreCase = true)
    }

    fun tryParsePaginationForm(html: String?, csrf: Array<String>, page: Array<String>, offset: IntArray): Boolean {
        csrf[0] = ""
        page[0] = ""
        offset[0] = 0
        if (html.isNullOrEmpty()) return false

        var csrfMatch = csrfRegex.find(html)
        if (csrfMatch == null) csrfMatch = metaCsrfRegex.find(html)

        var pageMatch = pageRegex.find(html)
        if (pageMatch == null) {
            pageMatch = Regex("""name="page"\s+value="([^"]+)"""", RegexOption.IGNORE_CASE).find(html)
        }

        var offsetMatch = offsetRegex.find(html)
        if (offsetMatch == null) {
            offsetMatch = Regex("""name="offset"\s+value="([^"]+)"""", RegexOption.IGNORE_CASE).find(html)
        }

        if (csrfMatch == null || pageMatch == null || offsetMatch == null) return false

        val parsedOffset = offsetMatch.groupValues[1].toIntOrNull() ?: return false
        csrf[0] = csrfMatch.groupValues[1]
        page[0] = pageMatch.groupValues[1]
        offset[0] = parsedOffset
        return csrf[0].isNotEmpty() && page[0].isNotEmpty()
    }

    fun isEndOfFeedResponse(response: String?): Boolean {
        if (response.isNullOrBlank()) return true
        if (response.contains("\"status\":\"ok\"", ignoreCase = true)) {
            val html = extractPostsHtml(response)
            return !containsProxyPosts(html) && !containsProxyPosts(response)
        }
        return false
    }

    fun tryAdvancePagination(
        rawResponse: String?,
        chunkHtml: String?,
        currentPage: String,
        currentOffset: Int,
        nextPage: Array<String>,
        nextOffset: IntArray
    ): Boolean {
        nextPage[0] = currentPage
        nextOffset[0] = currentOffset + 20

        val parsedPage = arrayOf("")
        val parsedOffset = intArrayOf(-1)
        if (tryParsePageOffset(rawResponse, parsedPage, parsedOffset) && parsedOffset[0] > currentOffset) {
            nextPage[0] = parsedPage[0]
            nextOffset[0] = parsedOffset[0]
            return true
        }
        if (!chunkHtml.isNullOrEmpty() &&
            tryParsePageOffset(chunkHtml, parsedPage, parsedOffset) &&
            parsedOffset[0] > currentOffset
        ) {
            nextPage[0] = parsedPage[0]
            nextOffset[0] = parsedOffset[0]
            return true
        }
        return nextOffset[0] > currentOffset
    }

    fun tryRefreshCsrf(rawResponse: String?, chunkHtml: String?, csrf: Array<String>): Boolean {
        val page = arrayOf("")
        val offset = intArrayOf(0)
        if (tryParsePaginationForm(rawResponse, csrf, page, offset)) return true
        if (!chunkHtml.isNullOrEmpty() && tryParsePaginationForm(chunkHtml, csrf, page, offset)) return true
        csrf[0] = ""
        return false
    }

    fun getPostsEndpoint(channelUrl: String, html: String?): String {
        if (!html.isNullOrEmpty()) {
            val actionMatch = formActionRegex.find(html)
            if (actionMatch != null) {
                return resolvePostsUrl(channelUrl, actionMatch.groupValues[1])
            }
        }
        return channelUrl.trimEnd('/') + "/posts-last"
    }

    fun isRestrictedResponse(response: String?): Boolean =
        !response.isNullOrEmpty() && response.contains("\"status\":\"restricted\"", ignoreCase = true)

    fun containsProxyPosts(html: String?): Boolean {
        if (html.isNullOrEmpty()) return false
        return ProxyLinkParser.looksLikeTelegramFeed(html) ||
            html.contains("Server:", ignoreCase = true) ||
            html.contains("/proxy?", ignoreCase = true) ||
            html.contains("tg://proxy", ignoreCase = true)
    }

    fun extractPostsHtml(response: String?): String {
        if (response.isNullOrEmpty()) return response.orEmpty()
        if (response.trimStart().startsWith("{")) return unescapeJsonHtmlField(response)
        return response
    }

    fun buildPostsBody(csrf: String, page: String, offset: Int): String =
        "_tgstat_csrk=${encode(csrf)}&page=${encode(page)}&offset=$offset&hideDeleted=1"

    private fun tryParsePageOffset(text: String?, page: Array<String>, offset: IntArray): Boolean {
        page[0] = ""
        offset[0] = -1
        if (text.isNullOrEmpty()) return false

        var pageMatch = pageRegex.findAll(text).lastOrNull()
        if (pageMatch == null) {
            pageMatch = Regex("""name="page"\s+value="([^"]+)"""", RegexOption.IGNORE_CASE)
                .findAll(text).lastOrNull()
        }
        if (pageMatch == null) {
            pageMatch = jsonPageRegex.findAll(text).lastOrNull()
        }

        var offsetMatch = offsetRegex.findAll(text).lastOrNull()
        if (offsetMatch == null) {
            offsetMatch = Regex("""name="offset"\s+value="([^"]+)"""", RegexOption.IGNORE_CASE)
                .findAll(text).lastOrNull()
        }
        if (offsetMatch == null) {
            offsetMatch = jsonOffsetRegex.findAll(text).lastOrNull()
        }

        if (pageMatch == null || offsetMatch == null) return false
        val parsedOffset = offsetMatch.groupValues[1].toIntOrNull() ?: return false
        page[0] = pageMatch.groupValues[1]
        offset[0] = parsedOffset
        return page[0].isNotEmpty()
    }

    private fun resolvePostsUrl(channelUrl: String, action: String): String {
        var path = action.trim()
        if (path.startsWith("http", ignoreCase = true)) return path
        val base = channelUrl.trimEnd('/')
        return if (path.startsWith('/')) {
            val host = Regex("""^(https?://[^/]+)""").find(channelUrl)?.groupValues?.get(1) ?: ""
            host + path
        } else {
            "$base/$path"
        }
    }

    private fun unescapeJsonHtmlField(json: String): String {
        val key = "\"html\":\""
        val start = json.indexOf(key, ignoreCase = true)
        if (start < 0) return json
        var i = start + key.length
        val sb = StringBuilder()
        while (i < json.length) {
            val c = json[i]
            if (c == '\\' && i + 1 < json.length) {
                val escaped = decodeJsonEscape(json, i)
                if (escaped != null) {
                    sb.append(escaped.first)
                    i = escaped.second
                    continue
                }
            }
            if (c == '"') break
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun decodeJsonEscape(json: String, index: Int): Pair<Char, Int>? {
        return when (json[index + 1]) {
            '"' -> '"' to index + 2
            '\\' -> '\\' to index + 2
            'n' -> '\n' to index + 2
            'r' -> '\r' to index + 2
            't' -> '\t' to index + 2
            '/' -> '/' to index + 2
            'u' -> if (index + 5 < json.length) {
                val hex = json.substring(index + 2, index + 6)
                hex.toIntOrNull(16)?.let { it.toChar() to index + 6 }
            } else {
                null
            }
            else -> null
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
