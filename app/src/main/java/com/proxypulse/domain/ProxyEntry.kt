package com.proxypulse.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ProxyEntry(
    val server: String,
    val port: Int,
    val secret: String,
    var pingMs: Int? = null,
    var isAvailable: Boolean = false,
    var publishedAt: Instant? = null
) {
    val key: String
        get() = "$server|$port|$secret"

    val displayLabel: String
        get() = "$server:$port"

    val pingDisplay: String
        get() = if (isAvailable && pingMs != null) "$pingMs ms" else "—"

    val publishedDisplay: String?
        get() = publishedAt?.atZone(ZoneId.systemDefault())
            ?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

    val publishedCaption: String?
        get() = publishedDisplay?.let { "Опубликовано: $it" }

    fun clone(): ProxyEntry = copy()
}
