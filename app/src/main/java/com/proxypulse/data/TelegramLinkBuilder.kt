package com.proxypulse.data

import com.proxypulse.domain.ProxyEntry

object TelegramLinkBuilder {
    /** Same query shape as Telegram expects; secret hex is not re-encoded. */
    fun buildTg(entry: ProxyEntry): String = buildLink("tg", entry)

    fun buildHttps(entry: ProxyEntry): String = buildLink("https", entry, host = "t.me")

    private fun buildLink(scheme: String, entry: ProxyEntry, host: String = "proxy"): String {
        val server = entry.server.trim()
        val secret = entry.secret.trim()
        return buildString {
            append(scheme)
            append("://")
            append(host)
            append("?server=")
            append(encodeQueryValue(server))
            append("&port=")
            append(entry.port)
            append("&secret=")
            append(secret)
        }
    }

    private fun encodeQueryValue(value: String): String {
        if (value.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == ':' }) {
            return value
        }
        return value.encodeToByteArray().joinToString("") { b ->
            val c = b.toInt() and 0xFF
            if (c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code ||
                c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code
            ) {
                c.toChar().toString()
            } else {
                "%${"%02X".format(c)}"
            }
        }
    }
}
