package com.proxypulse.data.feed

import com.proxypulse.data.network.HttpDownloadHelper
import com.proxypulse.domain.ProxyEntry

/** Свежие списки tg:// (как в Telegram Proxy Parser), до архива. */
object ProxyTextListFeed {
    private val LIST_URLS = listOf(
        "https://raw.githubusercontent.com/kort0881/telegram-proxy-collector/main/proxy_eu.txt",
        "https://raw.githubusercontent.com/kort0881/telegram-proxy-collector/main/proxy_ru.txt"
    )

    suspend fun loadInto(
        seen: MutableSet<String>,
        output: MutableList<ProxyEntry>,
        proxyCap: Int,
        log: ((String) -> Unit)?
    ): Int {
        var totalAdded = 0
        for (url in LIST_URLS) {
            if (seen.size >= proxyCap) break
            try {
                val text = HttpDownloadHelper.download(url, timeoutMs = 20_000L, maxAttempts = 2)
                val parsed = ProxyLinkParser.parse(text)
                var added = 0
                for (p in parsed) {
                    if (seen.size >= proxyCap) break
                    if (seen.add(p.key)) {
                        output.add(p)
                        added++
                    }
                }
                totalAdded += added
                log?.invoke("Списки: ${url.substringAfterLast('/')}, +$added, всего ${seen.size}/$proxyCap")
            } catch (e: Exception) {
                log?.invoke("Списки: ${url.substringAfterLast('/')} — ${e.message}")
            }
        }
        return totalAdded
    }
}
