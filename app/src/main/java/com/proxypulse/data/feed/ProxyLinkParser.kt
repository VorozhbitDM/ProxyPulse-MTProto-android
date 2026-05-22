package com.proxypulse.data.feed

import com.proxypulse.domain.ProxyEntry
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object ProxyLinkParser {
    private val linkRegex = Regex(
        """(?:tg://proxy/?\?|https?://(?:t\.me|telegram\.me)/proxy\?|/proxy\?)([^"'\s<>]+)""",
        RegexOption.IGNORE_CASE
    )
    private val inlineFieldsRegex = Regex(
        """server=([^&\s"'<>]+)&port=(\d+)&secret=([0-9a-fA-F]+)""",
        RegexOption.IGNORE_CASE
    )

    fun looksLikeTelegramFeed(html: String?): Boolean {
        if (html.isNullOrEmpty()) return false
        return html.contains("data-post=", ignoreCase = true) ||
            html.contains("tgme_widget_message", ignoreCase = true)
    }

    fun parse(text: String?): List<ProxyEntry> {
        if (text.isNullOrEmpty()) return emptyList()
        val normalized = text.replace("&amp;", "&")
        val result = mutableListOf<ProxyEntry>()
        for (match in linkRegex.findAll(normalized)) {
            tryParseQuery(match.groupValues[1])?.let { result.add(it) }
        }
        if (result.isEmpty()) {
            for (match in inlineFieldsRegex.findAll(normalized)) {
                val server = match.groupValues[1].trim().trimEnd('.')
                val port = match.groupValues[2].toIntOrNull() ?: continue
                val secret = match.groupValues[3].trim()
                if (server.isNotBlank() && port in 1..65535 && secret.isNotBlank()) {
                    result.add(ProxyEntry(server, port, secret))
                }
            }
        }
        return result
    }

    fun getArchiveSnapshotId(html: String?): String? {
        if (html.isNullOrEmpty()) return null
        val m = Regex(
            """/web/(\d{14})(?:if_|id_)?/?https://t\.me/s/ProxyMTProto""",
            RegexOption.IGNORE_CASE
        ).find(html) ?: return null
        return m.groupValues[1]
    }

    private fun tryParseQuery(rawQuery: String): ProxyEntry? {
        if (rawQuery.isBlank()) return null
        val query = URLDecoder.decode(rawQuery.trim().trimEnd('.'), StandardCharsets.UTF_8.name())
        val fields = parseQueryFields(query)
        val server = fields["server"] ?: return null
        val portText = fields["port"] ?: return null
        val secret = fields["secret"] ?: return null
        val port = portText.toIntOrNull() ?: return null
        val trimmedServer = server.trim().trimEnd('.')
        val trimmedSecret = secret.trim()
        if (trimmedServer.isBlank() || port <= 0 || trimmedSecret.isBlank()) return null
        return ProxyEntry(trimmedServer, port, trimmedSecret)
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
}
