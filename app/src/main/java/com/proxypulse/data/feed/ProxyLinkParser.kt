package com.proxypulse.data.feed

import com.proxypulse.domain.ProxyEntry
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

object ProxyLinkParser {
    private val linkRegex = Regex(
        """(?:tg://proxy/?\?|https?://(?:t\.me|telegram\.me)/proxy\?|/proxy\?)([^"'\s<>]+)""",
        RegexOption.IGNORE_CASE
    )
    private val messageBlockRegex = Regex("""data-post="([^"]+)"""", RegexOption.IGNORE_CASE)
    private val messageTimeRegex = Regex("""<time[^>]*datetime="([^"]+)"""", RegexOption.IGNORE_CASE)
    private val messageDateTextRegex = Regex(
        """class="[^"]*tgme_widget_message_date[^"]*"[^>]*>\s*(?:<time[^>]*>)?\s*([^<]+)""",
        RegexOption.IGNORE_CASE
    )
    private val plainPostRegex = Regex(
        """Server:\s*(?:<[^>]+>)?\s*`?([^`"<\s]+)`?\s*Port:\s*(?:<[^>]+>)?\s*`?(\d+)`?\s*Secret:\s*(?:<[^>]+>)?\s*`?([0-9a-fA-F+/=]+)`?""",
        RegexOption.IGNORE_CASE
    )
    private val tgStatProxyRegex = Regex(
        """Server:\s*(?:<[^>]+>)*\s*(.+?)\s*(?:<[^>]+>)*\s*Port:\s*(?:<[^>]+>)*\s*(\d+)\s*(?:<[^>]+>)*\s*Secret:\s*(?:<[^>]+>)*\s*([0-9a-fA-F+/=]+)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val tgStatDateRegex = Regex("""(\d{1,2}\s+[A-Za-z]{3},?\s+\d{1,2}:\d{2})""")
    private val tgStatPostContainerRegex = Regex(
        """id="post-\d+"[^>]*class="[^"]*post-container[^"]*"|class="[^"]*post-container[^"]*"""",
        RegexOption.IGNORE_CASE
    )
    private val tgStatReactionsRegex = Regex(
        """uil-thumbs-up[^>]*>\s*</i>\s*(\d+)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val inlineFieldsRegex = Regex(
        """server=([^&\s"'<>]+)&port=(\d+)&secret=([0-9a-fA-F+/=]+)""",
        RegexOption.IGNORE_CASE
    )

    fun looksLikeTelegramFeed(html: String?): Boolean {
        if (html.isNullOrEmpty()) return false
        return html.contains("data-post=", ignoreCase = true) ||
            html.contains("tgme_widget_message", ignoreCase = true) ||
            tgStatProxyRegex.containsMatchIn(html)
    }

    fun parse(text: String?): List<ProxyEntry> = parseFeed(text)

    fun parseFeed(text: String?): List<ProxyEntry> {
        if (text.isNullOrEmpty()) return emptyList()
        val normalized = text.replace("&amp;", "&")
        val list = mutableListOf<ProxyEntry>()
        val isTgStat = htmlLooksLikeTgStat(normalized)

        if (isTgStat) {
            parseTgStatPosts(normalized, list)
            if (list.isEmpty()) parseTgStatBlocks(normalized, list, reactions = 0)
        } else {
            parseTelegramPosts(normalized, list)
            parsePlainLinks(normalized, list, null)
            parsePlainTextProxies(normalized, list, null)
        }

        return mergeParsedEntries(list)
    }

    private fun htmlLooksLikeTgStat(html: String): Boolean =
        html.contains("tgstat.com", ignoreCase = true) ||
            html.contains("tgstat.ru", ignoreCase = true) ||
            tgStatPostContainerRegex.containsMatchIn(html)

    private fun mergeParsedEntries(list: List<ProxyEntry>): List<ProxyEntry> {
        val map = linkedMapOf<String, ProxyEntry>()
        for (entry in list) {
            val existing = map[entry.key.lowercase()]
            if (existing == null) {
                map[entry.key.lowercase()] = entry
            } else {
                if (entry.reactionsCount > existing.reactionsCount) {
                    existing.reactionsCount = entry.reactionsCount
                }
                if (entry.publishedAt != null) {
                    val incomingDate = entry.publishedAt
                    val existingDate = existing.publishedAt
                    if (existingDate == null || incomingDate!!.isAfter(existingDate)) {
                        existing.publishedAt = incomingDate
                    }
                }
            }
        }
        return map.values.toList()
    }

    private fun parseTelegramPosts(html: String, list: MutableList<ProxyEntry>) {
        val matches = messageBlockRegex.findAll(html).toList()
        if (matches.isEmpty()) return
        for (i in matches.indices) {
            val start = matches[i].range.first
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else html.length
            val chunk = html.substring(start, end)
            val publishedAt = extractPostDate(chunk)
            parsePlainLinks(chunk, list, publishedAt)
            parsePlainTextProxies(chunk, list, publishedAt)
        }
    }

    private fun extractPostDate(chunk: String): Instant? {
        messageTimeRegex.find(chunk)?.groupValues?.get(1)?.let { return tryParseDateTime(it) }
        messageDateTextRegex.find(chunk)?.groupValues?.get(1)?.trim()?.let { raw ->
            tryParseDateTime(raw)?.let { return it }
            tryParseTgStatDate(raw)?.let { return it }
        }
        return null
    }

    private fun parsePlainTextProxies(text: String, list: MutableList<ProxyEntry>, publishedAt: Instant?) {
        for (match in plainPostRegex.findAll(text)) {
            buildEntry(match.groupValues[1], match.groupValues[2], match.groupValues[3])?.let {
                it.publishedAt = publishedAt
                list.add(it)
            }
        }
    }

    private fun parsePlainLinks(text: String, list: MutableList<ProxyEntry>, publishedAt: Instant?) {
        for (match in linkRegex.findAll(text)) {
            tryParseQuery(match.groupValues[1])?.let {
                it.publishedAt = publishedAt
                list.add(it)
            }
        }
        if (list.isNotEmpty() || linkRegex.containsMatchIn(text)) return
        for (match in inlineFieldsRegex.findAll(text)) {
            buildEntry(match.groupValues[1], match.groupValues[2], match.groupValues[3])?.let {
                it.publishedAt = publishedAt
                list.add(it)
            }
        }
    }

    private fun parseTgStatPosts(html: String, list: MutableList<ProxyEntry>) {
        val starts = tgStatPostContainerRegex.findAll(html).map { it.range.first }.toList()
        if (starts.isEmpty()) return
        for (i in starts.indices) {
            val start = starts[i]
            val end = if (i + 1 < starts.size) starts[i + 1] else html.length
            val chunk = html.substring(start, end)
            val reactions = extractTgStatReactions(chunk)
            val publishedAt = extractTgStatDateFromChunk(chunk)
            val chunkList = mutableListOf<ProxyEntry>()
            parseTgStatBlocks(chunk, chunkList, reactions)
            parsePlainLinks(chunk, chunkList, publishedAt)
            for (entry in chunkList) {
                if (entry.reactionsCount == 0) entry.reactionsCount = reactions
                if (entry.publishedAt == null) entry.publishedAt = publishedAt
                list.add(entry)
            }
        }
    }

    private fun extractTgStatReactions(chunk: String): Int =
        tgStatReactionsRegex.find(chunk)?.groupValues?.get(1)?.toIntOrNull()?.coerceAtLeast(0) ?: 0

    private fun extractTgStatDateFromChunk(chunk: String): Instant? {
        for (match in tgStatDateRegex.findAll(chunk)) {
            tryParseTgStatDate(match.groupValues[1])?.let { return it }
        }
        return null
    }

    private fun parseTgStatBlocks(html: String, list: MutableList<ProxyEntry>, reactions: Int) {
        val seen = mutableSetOf<String>()
        for (match in tgStatProxyRegex.findAll(html)) {
            buildEntry(match.groupValues[1], match.groupValues[2], match.groupValues[3])?.let { entry ->
                if (!seen.add(entry.key.lowercase())) return@let
                entry.publishedAt = extractTgStatPostDate(html, match.range.first)
                entry.reactionsCount = reactions
                list.add(entry)
            }
        }
    }

    private fun extractTgStatPostDate(html: String, serverIndex: Int): Instant? {
        val blockStart = maxOf(0, serverIndex - 1500)
        val blockEnd = minOf(html.length, serverIndex + 160)
        val block = html.substring(blockStart, blockEnd)
        messageTimeRegex.find(block)?.groupValues?.get(1)?.let { return tryParseDateTime(it) }
        for (match in tgStatDateRegex.findAll(block).toList().asReversed()) {
            tryParseTgStatDate(match.groupValues[1])?.let { return it }
        }
        return null
    }

    private fun buildEntry(server: String, portText: String, secret: String): ProxyEntry? {
        val port = portText.toIntOrNull() ?: return null
        val trimmedServer = stripTags(server).trim().trim('`').trimEnd('.')
        val trimmedSecret = stripTags(secret).trim().trim('`')
        if (trimmedServer.isBlank() || port <= 0 || trimmedSecret.isBlank()) return null
        if (trimmedServer.equals("Unknown", ignoreCase = true) ||
            trimmedServer.equals("For", ignoreCase = true)
        ) {
            return null
        }
        return ProxyEntry(trimmedServer, port, trimmedSecret)
    }

    private fun tryParseQuery(rawQuery: String): ProxyEntry? {
        if (rawQuery.isBlank()) return null
        val query = URLDecoder.decode(rawQuery.trim().trimEnd('.'), StandardCharsets.UTF_8.name())
        val fields = parseQueryFields(query)
        val server = fields["server"] ?: return null
        val portText = fields["port"] ?: return null
        val secret = fields["secret"] ?: return null
        return buildEntry(server, portText, secret)
    }

    private fun parseQueryFields(query: String): Map<String, String> {
        val fields = mutableMapOf<String, String>()
        for (part in query.split('&')) {
            if (part.isBlank()) continue
            val eq = part.indexOf('=')
            if (eq <= 0) continue
            val key = URLDecoder.decode(part.substring(0, eq).trim(), StandardCharsets.UTF_8.name())
            val value = URLDecoder.decode(part.substring(eq + 1).trim(), StandardCharsets.UTF_8.name())
            if (key.isNotEmpty()) fields[key.lowercase()] = value
        }
        return fields
    }

    private fun tryParseDateTime(raw: String): Instant? {
        if (raw.isBlank()) return null
        return runCatching {
            Instant.parse(raw.trim())
        }.getOrNull() ?: runCatching {
            LocalDateTime.parse(raw.trim(), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .toInstant(ZoneOffset.UTC)
        }.getOrNull()
    }

    private fun tryParseTgStatDate(raw: String): Instant? {
        if (raw.isBlank()) return null
        val normalized = raw.trim().replace(Regex("""\s+"""), " ")
        val formats = listOf("d MMM HH:mm", "dd MMM HH:mm", "d MMM, HH:mm", "dd MMM, HH:mm")
        val locale = Locale.US
        val now = LocalDateTime.now(ZoneOffset.UTC)
        for (pattern in formats) {
            val formatter = DateTimeFormatter.ofPattern(pattern, locale)
            val parsed = runCatching { LocalDateTime.parse(normalized, formatter) }.getOrNull() ?: continue
            var odt = parsed.atOffset(ZoneOffset.UTC)
            if (odt.isAfter(now.atOffset(ZoneOffset.UTC).plusDays(1))) {
                odt = odt.minusYears(1)
            }
            return odt.toInstant()
        }
        return null
    }

    private fun stripTags(value: String): String =
        value.replace(Regex("<[^>]+>"), "").trim()
}
