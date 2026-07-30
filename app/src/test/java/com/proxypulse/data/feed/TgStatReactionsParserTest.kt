package com.proxypulse.data.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TgStatReactionsParserTest {
    @Test
    fun parseFeed_readsReactionsFromPostContainer() {
        val html = """
            <html><body>
            <div id="post-1" class="card card-body border post-container">
              <div class="post-text">Server: hot.example.com<br />Port: 443<br />Secret: eeabcdef0123456789</div>
              <i class="uil-thumbs-up"></i>97
            </div>
            <div id="post-2" class="card card-body border post-container">
              <div class="post-text">Server: cold.example.com<br />Port: 443<br />Secret: ee0123456789abcdef</div>
              <i class="uil-thumbs-up"></i>3
            </div>
            </body></html>
        """.trimIndent()

        val list = ProxyLinkParser.parseFeed(html)
        assertEquals(2, list.size)
        val byServer = list.associateBy { it.server }
        assertEquals(97, byServer.getValue("hot.example.com").reactionsCount)
        assertEquals(3, byServer.getValue("cold.example.com").reactionsCount)
        assertEquals("★ 97", byServer.getValue("hot.example.com").ratingDisplay)
    }

    @Test
    fun parseFeed_zeroReactionsWhenMissing() {
        val html = """
            <div class="post-container">
              <div class="post-text">Server: plain.example.com<br />Port: 443<br />Secret: eeaaaaaaaaaaaaaaaa</div>
            </div>
        """.trimIndent()
        val list = ProxyLinkParser.parseFeed(html)
        assertTrue(list.isNotEmpty())
        assertEquals(0, list[0].reactionsCount)
    }
}
