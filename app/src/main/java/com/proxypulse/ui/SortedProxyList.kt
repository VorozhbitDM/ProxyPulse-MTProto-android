package com.proxypulse.ui

import com.proxypulse.domain.ProxyEntry

/** Keeps available proxies sorted by ping ascending. */
class SortedProxyList {
    private val byKey = linkedMapOf<String, ProxyEntry>()
    private val sortedKeys = mutableListOf<String>()

    val entries: List<ProxyEntry>
        get() = sortedKeys.mapNotNull { byKey[it] }

    val count: Int get() = sortedKeys.size

    fun upsertAvailable(entry: ProxyEntry, pingMs: Int?) {
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

    fun remove(key: String) {
        byKey.remove(key)
        sortedKeys.remove(key)
    }

    fun clear() {
        byKey.clear()
        sortedKeys.clear()
    }
}
