package com.proxypulse.domain

data class ProxyEntry(
    val server: String,
    val port: Int,
    val secret: String,
    var pingMs: Int? = null,
    var isAvailable: Boolean = false
) {
    val key: String
        get() = "$server|$port|$secret"

    val displayLabel: String
        get() = "$server:$port"

    val pingDisplay: String
        get() = if (isAvailable && pingMs != null) "$pingMs ms" else "—"

    fun clone(): ProxyEntry = copy()
}
