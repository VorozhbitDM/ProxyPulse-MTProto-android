package com.proxypulse.ui

import com.proxypulse.domain.ProxyEntry
import com.proxypulse.domain.SortMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SortedProxyListTest {
    @Test
    fun sortsByPingAscending() {
        val list = SortedProxyList()
        list.setSortMode(SortMode.Ping)
        list.upsertAvailable(ProxyEntry("b.com", 443, "ee01"), 300)
        list.upsertAvailable(ProxyEntry("a.com", 443, "ee02"), 100)
        val snap = list.snapshot()
        assertEquals("a.com", snap[0].server)
        assertEquals("b.com", snap[1].server)
    }

    @Test
    fun sortsByReactionsDescending() {
        val list = SortedProxyList()
        list.setSortMode(SortMode.Rating)
        list.upsertAvailable(ProxyEntry("low.com", 443, "ee01", reactionsCount = 2), 80)
        list.upsertAvailable(ProxyEntry("hot.com", 443, "ee02", reactionsCount = 97), 200)
        val snap = list.snapshot()
        assertEquals("hot.com", snap[0].server)
        assertEquals(97, snap[0].reactionsCount)
        assertEquals("low.com", snap[1].server)
    }
}
