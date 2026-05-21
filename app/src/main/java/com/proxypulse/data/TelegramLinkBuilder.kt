package com.proxypulse.data

import com.proxypulse.domain.ProxyEntry
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object TelegramLinkBuilder {
    fun buildTg(entry: ProxyEntry): String = buildUri("tg://proxy?", entry)

    fun buildHttps(entry: ProxyEntry): String = buildUri("https://t.me/proxy?", entry)

    private fun buildUri(prefix: String, entry: ProxyEntry): String {
        val server = encode(entry.server)
        val secret = encode(entry.secret)
        return "${prefix}server=$server&port=${entry.port}&secret=$secret"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
