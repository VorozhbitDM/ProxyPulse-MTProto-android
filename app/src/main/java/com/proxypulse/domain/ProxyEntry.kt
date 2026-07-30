package com.proxypulse.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ProxyEntry(
    val server: String,
    val port: Int,
    val secret: String,
    var pingMs: Int? = null,
    var isAvailable: Boolean = false,
    var publishedAt: Instant? = null,
    /** Реакции пользователей на пост TGStat (👍 / ★ и др.). */
    var reactionsCount: Int = 0
) {
    val key: String
        get() = "$server|$port|$secret"

    val displayLabel: String
        get() = "$server:$port"

    val pingDisplay: String
        get() = if (isAvailable && pingMs != null) "$pingMs ms" else "—"

    val ratingDisplay: String
        get() = "★ $reactionsCount"

    val publishedDisplay: String?
        get() = publishedAt?.atZone(ZoneId.systemDefault())
            ?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

    val publishedCaption: String?
        get() = publishedDisplay?.let { "Опубликовано: $it" }

    fun clone(): ProxyEntry = copy()
}
