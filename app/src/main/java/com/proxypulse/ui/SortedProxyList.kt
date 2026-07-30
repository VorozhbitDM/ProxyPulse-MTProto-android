package com.proxypulse.ui

import com.proxypulse.domain.ProxyEntry
import com.proxypulse.domain.SortMode

/** Available proxies with switchable sort: ping ↑ or rating (stars) ↓. */
class SortedProxyList {
    private val lock = Any()
    private val byKey = linkedMapOf<String, ProxyEntry>()
    private var sortMode: SortMode = SortMode.Rating

    val count: Int
        get() = synchronized(lock) { byKey.size }

    fun snapshot(): List<ProxyEntry> = synchronized(lock) {
        byKey.values.sortedWith(comparatorFor(sortMode))
    }

    fun setSortMode(mode: SortMode) = synchronized(lock) {
        sortMode = mode
    }

    fun upsertAvailable(entry: ProxyEntry, pingMs: Int?) = synchronized(lock) {
        entry.isAvailable = true
        entry.pingMs = pingMs
        byKey[entry.key] = entry
    }

    fun remove(key: String) = synchronized(lock) {
        byKey.remove(key)
    }

    fun clear() = synchronized(lock) {
        byKey.clear()
    }

    companion object {
        fun comparatorFor(mode: SortMode): Comparator<ProxyEntry> = when (mode) {
            SortMode.Ping -> compareBy<ProxyEntry> { it.pingMs ?: Int.MAX_VALUE }
                .thenBy { it.displayLabel }
            SortMode.Rating -> compareByDescending<ProxyEntry> { it.reactionsCount }
                .thenBy { it.pingMs ?: Int.MAX_VALUE }
                .thenBy { it.displayLabel }
        }
    }
}
