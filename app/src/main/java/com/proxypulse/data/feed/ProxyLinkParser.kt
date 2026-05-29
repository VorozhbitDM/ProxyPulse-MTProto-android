package com.proxypulse.data.feed

import com.proxypulse.domain.ProxyEntry
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

object ProxyLinkParser {
    private val linkRegex = Regex(
        """(?:tg://proxy/?\?|https?://(?:t\.me|telegram\.me)/proxy\?|/proxy\?)([^"'\s<>]+)""",
        RegexOption.IGNORE_CASE
    )
    private val postIdRegex = Regex("""data-post="[^"]+/(\d+)"""")
    private val archiveNextPageRegex = Regex(
        """<link\s+rel="prev"\s+href="([^"]*?ProxyMTProto\?before=\d+[^"]*)"""",
        RegexOption.IGNORE_CASE
    )
    private val archiveMorePageRegex = Regex(
        """href="(/web/\d+(?:if_|id_)?/?https://t\.me/s/ProxyMTProto\?before=\d+)"""",
        RegexOption.IGNORE_CASE
    )
    private val archiveSnapshotRegex = Regex(
        """/web/(\d{14})(?:if_|id_)?/?https://t\.me/s/ProxyMTProto""",
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
        val pageDate = tryParseArchivePageDate(normalized)
        val isTgStat = htmlLooksLikeTgStat(normalized)

        if (isTgStat) parseTgStatBlocks(normalized, list)
        parseTelegramPosts(normalized, list, pageDate)
        parsePlainLinks(normalized, list, pageDate)
        parsePlainTextProxies(normalized, list, pageDate)
        if (!isTgStat) parseTgStatBlocks(normalized, list)

        return mergeParsedEntries(list)
    }

    fun getArchiveSnapshotId(html: String?): String? {
        if (html.isNullOrEmpty()) return null
        return archiveSnapshotRegex.find(html)?.groupValues?.get(1)
    }

    fun getPaginationBeforeId(html: String?): Long? {
        if (html.isNullOrEmpty()) return null
        var minId: Long? = null
        for (match in postIdRegex.findAll(html)) {
            val id = match.groupValues[1].toLongOrNull() ?: continue
            if (minId == null || id < minId) minId = id
        }
        return minId
    }

    fun getArchiveNextPageHref(html: String?): String? {
        if (html.isNullOrEmpty()) return null
        val m = archiveNextPageRegex.find(html)
        if (m != null) return normalizeArchiveFeedHref(m.groupValues[1])
        val m2 = archiveMorePageRegex.find(html)
        return m2?.groupValues?.get(1)?.let { normalizeArchiveFeedHref(it) }
    }

    fun getNextPageBeforeId(html: String?): Long? {
        val href = getArchiveNextPageHref(html)
        if (!href.isNullOrEmpty()) {
            ArchiveUrlHelper.parseBeforeId("https://web.archive.org$href")?.let { return it }
        }
        return getPaginationBeforeId(html)
    }

    fun buildArchivePageUrl(snapshotId: String?, beforeId: Long?): String? {
        if (snapshotId.isNullOrEmpty()) return null
        var url = "https://web.archive.org/web/${snapshotId}if_/https://t.me/s/ProxyMTProto"
        if (beforeId != null) url += "?before=$beforeId"
        return url
    }

    fun buildTelegramPageUrl(baseUrl: String?, beforeId: Long): String? {
        if (baseUrl.isNullOrEmpty()) return null
        val separator = if (baseUrl.contains('?')) "&" else "?"
        return "$baseUrl${separator}before=$beforeId"
    }

    private fun htmlLooksLikeTgStat(html: String): Boolean =
        html.contains("tgstat.com", ignoreCase = true) || html.contains("tgstat.ru", ignoreCase = true)

    private fun mergeParsedEntries(list: List<ProxyEntry>): List<ProxyEntry> {
        val map = linkedMapOf<String, ProxyEntry>()
        for (entry in list) {
            val existing = map[entry.key.lowercase()]
            if (existing == null) {
                map[entry.key.lowercase()] = entry
            } else if (entry.publishedAt != null) {
                val incomingDate = entry.publishedAt
                val existingDate = existing.publishedAt
                if (existingDate == null || incomingDate!!.isAfter(existingDate)) {
                    existing.publishedAt = incomingDate
                }
            }
        }
        return map.values.toList()
    }

    private fun parseTelegramPosts(html: String, list: MutableList<ProxyEntry>, pageDate: Instant?) {
        val matches = messageBlockRegex.findAll(html).toList()
        if (matches.isEmpty()) return
        for (i in matches.indices) {
            val start = matches[i].range.first
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else html.length
            val chunk = html.substring(start, end)
            val publishedAt = extractPostDate(chunk) ?: pageDate
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

    private fun tryParseArchivePageDate(html: String): Instant? {
        val snapshotId = getArchiveSnapshotId(html) ?: return null
        if (snapshotId.length < 8) return null
        val date = LocalDate.parse(snapshotId.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE)
        return date.atStartOfDay(ZoneOffset.UTC).toInstant()
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

    private fun parseTgStatBlocks(html: String, list: MutableList<ProxyEntry>) {
        val seen = mutableSetOf<String>()
        for (match in tgStatProxyRegex.findAll(html)) {
            buildEntry(match.groupValues[1], match.groupValues[2], match.groupValues[3])?.let { entry ->
                if (!seen.add(entry.key.lowercase())) return@let
                entry.publishedAt = extractTgStatPostDate(html, match.range.first)
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

    private fun normalizeArchiveFeedHref(href: String?): String? {
        if (href.isNullOrBlank()) return null
        var path = href.trim()
        if (path.startsWith("http", ignoreCase = true) &&
            path.contains("web.archive.org", ignoreCase = true)
        ) {
            val idx = path.indexOf("/web/", ignoreCase = true)
            if (idx >= 0) path = path.substring(idx)
        }
        return if (path.startsWith('/')) path else "/$path"
    }
}
