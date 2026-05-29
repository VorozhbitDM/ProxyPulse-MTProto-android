package com.proxypulse.data.tgstat

import okhttp3.Cookie
import java.util.concurrent.ConcurrentHashMap

object TgStatCookieParser {
    fun toCookieHeader(store: Map<String, List<Cookie>>): String {
        val names = mutableSetOf<String>()
        val parts = mutableListOf<String>()
        for (host in listOf("tgstat.com", "tgstat.ru")) {
            for (cookie in store[host].orEmpty()) {
                if (cookie.name.isBlank() || !names.add(cookie.name.lowercase())) continue
                parts.add("${cookie.name}=${cookie.value}")
            }
        }
        return parts.joinToString("; ")
    }

    fun parsePairs(raw: String?): Sequence<Pair<String, String>> = sequence {
        if (raw.isNullOrBlank()) return@sequence
        var text = raw.trim()
        if (text.startsWith("Cookie:", ignoreCase = true)) {
            text = text.substring(7).trim()
        }
        for (part in text.split(';', '\n', '\r')) {
            val segment = part.trim()
            if (segment.isEmpty() || segment.startsWith('#')) continue
            val eq = segment.indexOf('=')
            if (eq <= 0) continue
            var name = segment.substring(0, eq).trim()
            var value = segment.substring(eq + 1).trim()
            if (name.isEmpty()) continue
            if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) {
                value = value.substring(1, value.length - 1)
            }
            yield(name to value)
        }
    }
}
