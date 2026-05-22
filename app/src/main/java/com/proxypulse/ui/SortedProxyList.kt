package com.proxypulse.ui

import com.proxypulse.domain.ProxyEntry

/** Keeps available proxies sorted by ping ascending. Thread-safe for parallel health checks. */
class SortedProxyList {
    private val lock = Any()
    private val byKey = linkedMapOf<String, ProxyEntry>()
    private val sortedKeys = mutableListOf<String>()

    val count: Int
        get() = synchronized(lock) { sortedKeys.size }

    fun snapshot(): List<ProxyEntry> = synchronized(lock) {
        sortedKeys.mapNotNull { byKey[it] }
    }

    fun upsertAvailable(entry: ProxyEntry, pingMs: Int?) = synchronized(lock) {
        entry.isAvailable = true
        entry.pingMs = pingMs
        byKey[entry.key] = entry
        sortedKeys.remove(entry.key)
        val ping = pingMs ?: Int.MAX_VALUE
        val insertAt = sortedKeys.indexOfFirst { key ->
            val other = byKey[key] ?: return@indexOfFirst true
            (other.pingMs ?: Int.MAX_VALUE) > ping
        }
        if (insertAt < 0) sortedKeys.add(entry.key)
        else sortedKeys.add(insertAt, entry.key)
    }

    fun remove(key: String) = synchronized(lock) {
        byKey.remove(key)
        sortedKeys.remove(key)
    }

    fun clear() = synchronized(lock) {
        byKey.clear()
        sortedKeys.clear()
    }
}
