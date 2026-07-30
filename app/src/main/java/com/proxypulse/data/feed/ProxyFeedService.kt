package com.proxypulse.data.feed

import com.proxypulse.data.network.HttpDownloadHelper
import com.proxypulse.domain.ProxyEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** Lite: только первая страница TGStat (@ProxyMTProto), без авторизации и пагинации. */
class ProxyFeedService {
    suspend fun fetchToList(
        output: MutableList<ProxyEntry>,
        log: ((String) -> Unit)? = null,
        progress: ((CollectProgress) -> Unit)? = null
    ) {
        val seen = linkedSetOf<String>()
        log?.invoke("Загрузка ленты TGStat…")
        progress?.invoke(CollectProgress(proxiesFound = 0))

        var loadedAny = false
        for (url in TGSTAT_URLS) {
            coroutineContext.ensureActive()
            try {
                log?.invoke("Лента: $url…")
                val html = HttpDownloadHelper.download(url, timeoutMs = 20_000L, maxAttempts = 2)
                if (!ProxyLinkParser.looksLikeTelegramFeed(html)) {
                    log?.invoke("Лента: ответ без постов, другой URL…")
                    continue
                }
                val added = emitNewProxies(ProxyLinkParser.parseFeed(html), seen, output)
                log?.invoke("Лента: $url, +$added прокси")
                progress?.invoke(CollectProgress(proxiesFound = seen.size))
                if (added > 0 || seen.isNotEmpty()) {
                    loadedAny = true
                    break
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log?.invoke("Лента: ${e.message}")
            }
        }

        if (!loadedAny) {
            throw IllegalStateException("TGStat: не удалось загрузить первую страницу")
        }
        log?.invoke("Сбор завершён: ${seen.size} прокси")
        progress?.invoke(CollectProgress(proxiesFound = seen.size))
    }

    private fun emitNewProxies(
        parsed: List<ProxyEntry>,
        seen: MutableSet<String>,
        output: MutableList<ProxyEntry>
    ): Int {
        var added = 0
        for (p in parsed) {
            if (!seen.add(p.key)) continue
            added++
            output.add(p)
        }
        return added
    }

    companion object {
        private val TGSTAT_URLS = listOf(
            "https://tgstat.com/channel/@ProxyMTProto",
            "https://tgstat.ru/channel/@ProxyMTProto"
        )
    }
}
