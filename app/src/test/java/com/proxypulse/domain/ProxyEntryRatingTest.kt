package com.proxypulse.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ProxyEntryRatingTest {
    @Test
    fun ratingDisplay_showsReactionCount() {
        val entry = ProxyEntry("a.example", 443, "ee00", reactionsCount = 34)
        assertEquals("★ 34", entry.ratingDisplay)
    }
}
