package com.proxypulse.data.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyLinkParserTest {
    @Test
    fun looksLikeTelegramFeed_detectsFeed() {
        val html = javaClass.classLoader!!
            .getResourceAsStream("feed-sample.html")!!
            .bufferedReader()
            .readText()
        assertTrue(ProxyLinkParser.looksLikeTelegramFeed(html))
    }

    @Test
    fun parse_extractsProxiesFromFixture() {
        val html = javaClass.classLoader!!
            .getResourceAsStream("feed-sample.html")!!
            .bufferedReader()
            .readText()
        val list = ProxyLinkParser.parse(html)
        assertEquals(2, list.size)
        assertEquals("example.com", list[0].server)
        assertEquals(443, list[0].port)
        assertEquals("ee0123456789abcdef", list[0].secret)
        assertEquals("backup.example.com", list[1].server)
        assertEquals(8443, list[1].port)
    }

    @Test
    fun parse_skipsMalformed() {
        val html = """<a href="tg://proxy?server=only">bad</a>"""
        assertTrue(ProxyLinkParser.parse(html).isEmpty())
    }

    @Test
    fun looksLikeTelegramFeed_emptyFalse() {
        assertFalse(ProxyLinkParser.looksLikeTelegramFeed(null))
        assertFalse(ProxyLinkParser.looksLikeTelegramFeed(""))
    }
}
