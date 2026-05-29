package com.proxypulse.data.feed

import com.proxypulse.data.network.HttpDownloadHelper
import com.proxypulse.data.network.HttpSession
import com.proxypulse.data.tgstat.TgStatFeedHelper
import com.proxypulse.domain.FeedSourceMode
import com.proxypulse.domain.ProxyEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

class ProxyFeedService {
    suspend fun fetchToList(
        output: MutableList<ProxyEntry>,
        log: ((String) -> Unit)? = null,
        progress: ((CollectProgress) -> Unit)? = null,
        maxRecentProxies: Int = DEFAULT_MAX_RECENT_PROXIES,
        maxCdxSnapshotsToScan: Int = DEFAULT_MAX_CDX_SNAPSHOTS,
        feedSource: FeedSourceMode = FeedSourceMode.Bypass,
        tgStatCookieHeader: String = ""
    ) = coroutineScope {
        val cap = maxOf(1, maxRecentProxies)
        val cdxCap = maxOf(1, maxCdxSnapshotsToScan)
        val seen = linkedSetOf<String>()
        val errors = mutableListOf<String>()

        reportCdx(progress, 0, cdxCap, 0, cap)
        log?.invoke("Загрузка ленты → $cap прокси…")

        if (feedSource == FeedSourceMode.Direct) {
            log?.invoke("Источник: t.me")
            tryLoadExternalFeed(seen, output, errors, log, cap, feedSource, tgStatCookieHeader)
            log?.invoke("Сбор завершён (t.me): ${seen.size}/$cap прокси")
            return@coroutineScope
        }

        log?.invoke("Источник: TGStat + archive.org")
        tryLoadExternalFeed(seen, output, errors, log, cap, feedSource, tgStatCookieHeader)

        if (seen.size >= cap) {
            log?.invoke("Сбор завершён: ${seen.size}/$cap прокси")
            return@coroutineScope
        }

        log?.invoke("TGStat: ${seen.size}/$cap — добираем из archive.org…")
        loadArchiveFeed(seen, output, errors, log, progress, cdxCap, cap, maxCdxSnapshotsToScan)
    }

    private suspend fun loadArchiveFeed(
        seen: MutableSet<String>,
        output: MutableList<ProxyEntry>,
        errors: MutableList<String>,
        log: ((String) -> Unit)?,
        progress: ((CollectProgress) -> Unit)?,
        cdxCap: Int,
        cap: Int,
        maxCdxSnapshotsToScan: Int
    ) = coroutineScope {
        log?.invoke("Архив: лента (1 стр.) + до $cdxCap снимков CDX…")

        val cdxDeferred = async { ArchiveCdxService.getRecentSnapshotIds(log) }

        var feedSnapshotId: String? = null
        if (!tryLoadFeedFirstPage(seen, output, errors, log, cap) { feedSnapshotId = it }) {
            log?.invoke("Лента: первая страница недоступна")
        }

        reportCdx(progress, 0, cdxCap, seen.size, cap)

        var cdxOk = 0
        coroutineContext.ensureActive()
        if (seen.size < cap) {
            val snapshots = try {
                cdxDeferred.await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log?.invoke("CDX: ${e.message}")
                emptyList()
            }
            cdxOk = loadCdxSnapshotsUntilFull(
                seen, output, errors, log, progress, cdxCap, cap, snapshots, feedSnapshotId
            )
        }

        log?.invoke("Загрузка завершена: ${seen.size}/$cap прокси · CDX снимков $cdxOk")
    }

    private suspend fun tryLoadExternalFeed(
        seen: MutableSet<String>,
        output: MutableList<ProxyEntry>,
        errors: MutableList<String>,
        log: ((String) -> Unit)?,
        proxyCap: Int,
        feedSource: FeedSourceMode,
        tgStatCookieHeader: String
    ) {
        var loadedAny = false
        for (url in TelegramMirrorSources.getUrls(feedSource)) {
            coroutineContext.ensureActive()
            if (seen.size >= proxyCap) break
            try {
                val added = if (feedSource == FeedSourceMode.Bypass && TgStatFeedHelper.isTgStatUrl(url)) {
                    loadTgStatFeed(url, seen, output, log, proxyCap, tgStatCookieHeader)
                } else {
                    loadTelegramWebFeed(url, seen, output, log, proxyCap)
                }
                if (added > 0) loadedAny = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log?.invoke("Лента: ${e.message}")
            }
        }

        if (!loadedAny) {
            synchronized(errors) {
                errors.add(
                    if (feedSource == FeedSourceMode.Bypass) {
                        "TGStat: не удалось загрузить ленту"
                    } else {
                        "Лента: не удалось загрузить t.me"
                    }
                )
            }
        }
    }

    private suspend fun loadTelegramWebFeed(
        url: String,
        seen: MutableSet<String>,
        output: MutableList<ProxyEntry>,
        log: ((String) -> Unit)?,
        proxyCap: Int
    ): Int {
        log?.invoke("Лента: $url…")
        var html = HttpDownloadHelper.download(url, timeoutMs = 20_000L, maxAttempts = 2)
        if (!ProxyLinkParser.looksLikeTelegramFeed(html)) {
            log?.invoke("Лента: ответ без постов Telegram, другой URL…")
            return 0
        }

        var totalAdded = emitNewProxies(ProxyLinkParser.parseFeed(html), seen, output, proxyCap)
        log?.invoke("Лента: $url, +$totalAdded новых, всего ${seen.size}/$proxyCap")
        if (seen.size >= proxyCap) return totalAdded

        var beforeId = ProxyLinkParser.getNextPageBeforeId(html)
        var pageNum = 1
        while (beforeId != null && seen.size < proxyCap && pageNum < MAX_FEED_PAGES_TO_SCAN) {
            coroutineContext.ensureActive()
            delay(MS_BETWEEN_FEED_PAGES)
            pageNum++

            val pageUrl = ProxyLinkParser.buildTelegramPageUrl(url, beforeId) ?: break
            log?.invoke("Лента: страница $pageNum, before=$beforeId…")
            html = HttpDownloadHelper.download(
                pageUrl,
                referer = url,
                timeoutMs = HttpDownloadHelper.PAGINATION_TIMEOUT_MS,
                maxAttempts = HttpDownloadHelper.PAGINATION_MAX_ATTEMPTS
            )

            if (!ProxyLinkParser.looksLikeTelegramFeed(html)) {
                log?.invoke("Лента: страница $pageNum пустая, остановка")
                break
            }

            val added = emitNewProxies(ProxyLinkParser.parseFeed(html), seen, output, proxyCap)
            totalAdded += added
            log?.invoke("Лента: стр. $pageNum, +$added новых, всего ${seen.size}/$proxyCap")
            if (added == 0) break

            val nextBefore = ProxyLinkParser.getNextPageBeforeId(html)
            if (nextBefore == null || nextBefore >= beforeId) break
            beforeId = nextBefore
        }

        return totalAdded
    }

    private suspend fun loadTgStatFeed(
        channelUrl: String,
        seen: MutableSet<String>,
        output: MutableList<ProxyEntry>,
        log: ((String) -> Unit)?,
        proxyCap: Int,
        tgStatCookieHeader: String
    ): Int {
        log?.invoke("Лента: $channelUrl…")
        val session = if (tgStatCookieHeader.isNotBlank()) {
            log?.invoke("TGStat: используется сохранённая сессия из настроек")
            HttpSession(tgStatCookieHeader)
        } else {
            HttpSession()
        }

        var html = session.download(channelUrl, timeoutMs = 20_000L, maxAttempts = 2)
        if (!ProxyLinkParser.looksLikeTelegramFeed(html)) {
            log?.invoke("Лента: ответ без постов, другой URL…")
            return 0
        }

        var totalAdded = emitNewProxies(ProxyLinkParser.parseFeed(html), seen, output, proxyCap)
        log?.invoke("Лента: $channelUrl, +$totalAdded новых, всего ${seen.size}/$proxyCap")
        if (seen.size >= proxyCap) return totalAdded

        val csrf = arrayOf("")
        val page = arrayOf("")
        val offset = intArrayOf(0)
        if (!TgStatFeedHelper.tryParsePaginationForm(html, csrf, page, offset)) {
            log?.invoke("TGStat: форма «Show more» не найдена")
            return totalAdded
        }

        val postsUrl = TgStatFeedHelper.getPostsEndpoint(channelUrl, html)
        val initialCsrf = csrf[0]
        var pageNum = 0
        var restrictedLogged = false
        var emptyPagesInRow = 0
        var duplicatePagesInRow = 0

        while (seen.size < proxyCap && pageNum < MAX_FEED_PAGES_TO_SCAN) {
            coroutineContext.ensureActive()
            delay(MS_BETWEEN_FEED_PAGES)
            pageNum++

            val response = session.postForm(
                postsUrl,
                channelUrl,
                TgStatFeedHelper.buildPostsBody(csrf[0], page[0], offset[0])
            )

            if (TgStatFeedHelper.isRestrictedResponse(response)) {
                if (!restrictedLogged) {
                    if (tgStatCookieHeader.isNotBlank()) {
                        log?.invoke("TGStat: сессия не принята — повторите вход в настройках")
                    } else {
                        log?.invoke("TGStat: следующие страницы только после входа (Настройки → TGStat)")
                    }
                    restrictedLogged = true
                }
                break
            }

            if (TgStatFeedHelper.isEndOfFeedResponse(response)) {
                log?.invoke("TGStat: лента закончилась на стр. $pageNum")
                break
            }

            var chunkHtml = TgStatFeedHelper.extractPostsHtml(response)
            if (!TgStatFeedHelper.containsProxyPosts(chunkHtml)) {
                if (TgStatFeedHelper.containsProxyPosts(response)) {
                    chunkHtml = response
                    emptyPagesInRow = 0
                } else {
                    emptyPagesInRow++
                    log?.invoke("TGStat: стр. $pageNum пустая ($emptyPagesInRow/3), offset=${offset[0]}")
                    if (emptyPagesInRow >= 3) break
                }
            } else {
                emptyPagesInRow = 0
            }

            if (TgStatFeedHelper.containsProxyPosts(chunkHtml)) {
                val added = emitNewProxies(ProxyLinkParser.parseFeed(chunkHtml), seen, output, proxyCap)
                totalAdded += added
                if (added > 0) {
                    duplicatePagesInRow = 0
                    log?.invoke("TGStat: стр. $pageNum, +$added новых, всего ${seen.size}/$proxyCap")
                } else {
                    duplicatePagesInRow++
                    log?.invoke(
                        "TGStat: стр. $pageNum, только дубликаты ($duplicatePagesInRow/3), всего ${seen.size}/$proxyCap"
                    )
                    if (duplicatePagesInRow >= 3) {
                        log?.invoke("TGStat: 3 страницы подряд с дубликатами — переход к archive.org")
                        break
                    }
                }
            }

            val refreshedCsrf = arrayOf("")
            csrf[0] = if (TgStatFeedHelper.tryRefreshCsrf(response, chunkHtml, refreshedCsrf) &&
                refreshedCsrf[0].isNotEmpty()
            ) {
                refreshedCsrf[0]
            } else {
                initialCsrf
            }

            val nextPage = arrayOf(page[0])
            val nextOffset = intArrayOf(offset[0])
            val previousOffset = offset[0]
            if (!TgStatFeedHelper.tryAdvancePagination(
                    response, chunkHtml, page[0], offset[0], nextPage, nextOffset
                )
            ) {
                log?.invoke("TGStat: offset не меняется (${offset[0]}), остановка")
                break
            }

            page[0] = nextPage[0]
            offset[0] = nextOffset[0]
            if (offset[0] <= previousOffset) {
                log?.invoke("TGStat: offset застрял на ${offset[0]}, остановка")
                break
            }
        }

        return totalAdded
    }

    private suspend fun tryLoadFeedFirstPage(
        seen: MutableSet<String>,
        output: MutableList<ProxyEntry>,
        errors: MutableList<String>,
        log: ((String) -> Unit)?,
        proxyCap: Int,
        onSnapshot: (String?) -> Unit
    ): Boolean {
        log?.invoke("Лента: последний снимок (web/2)…")
        for (candidate in ArchiveUrlHelper.getFirstPageCandidates(ARCHIVE_ENTRY_URL)) {
            coroutineContext.ensureActive()
            try {
                var html = HttpDownloadHelper.download(candidate, "https://web.archive.org/")
                if (!ProxyLinkParser.looksLikeTelegramFeed(html)) {
                    log?.invoke("Лента: ответ без постов Telegram, другой URL…")
                    continue
                }

                val feedSnapshotId = ProxyLinkParser.getArchiveSnapshotId(html)
                onSnapshot(feedSnapshotId)
                val added = emitNewProxies(ProxyLinkParser.parseFeed(html), seen, output, proxyCap)
                if (!feedSnapshotId.isNullOrEmpty()) {
                    log?.invoke("Лента: снимок $feedSnapshotId, +$added новых, всего ${seen.size}/$proxyCap")
                } else {
                    log?.invoke("Лента: +$added новых, всего ${seen.size}/$proxyCap")
                }

                loadArchiveFeedPages(html, feedSnapshotId, seen, output, log, proxyCap, candidate)
                return true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log?.invoke("Лента: ${e.message}")
            }
        }
        synchronized(errors) { errors.add("Лента: не удалось загрузить первую страницу") }
        return false
    }

    private suspend fun loadArchiveFeedPages(
        html: String,
        snapshotId: String?,
        seen: MutableSet<String>,
        output: MutableList<ProxyEntry>,
        log: ((String) -> Unit)?,
        proxyCap: Int,
        referer: String
    ) {
        if (seen.size >= proxyCap) return
        var currentHtml = html
        var beforeId = ProxyLinkParser.getNextPageBeforeId(currentHtml) ?: return
        val snap = snapshotId?.ifEmpty { null } ?: "2"
        var pageNum = 1

        while (beforeId != null && seen.size < proxyCap && pageNum < MAX_FEED_PAGES_TO_SCAN) {
            coroutineContext.ensureActive()
            delay(MS_BETWEEN_FEED_PAGES)
            pageNum++

            val pageUrl = ProxyLinkParser.buildArchivePageUrl(snap, beforeId) ?: break
            log?.invoke("Лента archive: стр. $pageNum, before=$beforeId…")
            try {
                currentHtml = HttpDownloadHelper.download(
                    pageUrl,
                    referer.ifEmpty { "https://web.archive.org/" },
                    timeoutMs = HttpDownloadHelper.PAGINATION_TIMEOUT_MS,
                    maxAttempts = HttpDownloadHelper.PAGINATION_MAX_ATTEMPTS
                )

                if (!ProxyLinkParser.looksLikeTelegramFeed(currentHtml)) {
                    log?.invoke("Лента archive: стр. $pageNum пустая, остановка")
                    break
                }

                val added = emitNewProxies(ProxyLinkParser.parseFeed(currentHtml), seen, output, proxyCap)
                log?.invoke("Лента archive: стр. $pageNum, +$added новых, всего ${seen.size}/$proxyCap")
                if (added == 0) break

                val nextBefore = ProxyLinkParser.getNextPageBeforeId(currentHtml)
                if (nextBefore == null || nextBefore >= beforeId) break
                beforeId = nextBefore
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log?.invoke("Лента archive: стр. $pageNum: ${e.message}")
                break
            }
        }
    }

    private suspend fun loadCdxSnapshotsUntilFull(
        seen: MutableSet<String>,
        output: MutableList<ProxyEntry>,
        errors: MutableList<String>,
        log: ((String) -> Unit)?,
        progress: ((CollectProgress) -> Unit)?,
        maxSnapshots: Int,
        proxyCap: Int,
        snapshots: List<String>,
        skipSnapshotId: String?
    ): Int {
        val batch = ArchiveCdxService.filterNewestFirst(snapshots)
            .filter { skipSnapshotId.isNullOrEmpty() || !it.equals(skipSnapshotId, ignoreCase = true) }
            .take(maxSnapshots)

        if (batch.isEmpty()) {
            log?.invoke("CDX: нет дополнительных свежих снимков")
            return 0
        }

        log?.invoke("CDX: до ${batch.size} снимков, с ${batch.first()}…")
        var loaded = 0

        for ((i, snapshotId) in batch.withIndex()) {
            coroutineContext.ensureActive()
            if (seen.size >= proxyCap) break
            if (i > 0) delay(MS_BETWEEN_CDX_SNAPSHOTS)

            try {
                val url = ArchiveUrlHelper.firstPageForSnapshot(snapshotId)
                val html = HttpDownloadHelper.download(
                    url = url,
                    referer = "https://web.archive.org/",
                    timeoutMs = HttpDownloadHelper.CDX_SNAPSHOT_TIMEOUT_MS,
                    maxAttempts = 2
                )
                val parsed = ProxyLinkParser.parseFeed(html)
                if (!ProxyLinkParser.looksLikeTelegramFeed(html) && parsed.isEmpty()) {
                    log?.invoke("CDX ${i + 1}/${batch.size} ($snapshotId): пустая страница")
                    continue
                }
                val added = emitNewProxies(parsed, seen, output, proxyCap)
                loaded++
                reportCdx(progress, i + 1, batch.size, seen.size, proxyCap)
                log?.invoke(
                    "CDX ${i + 1}/${batch.size} ($snapshotId): +$added новых, всего ${seen.size}/$proxyCap"
                )
                if (seen.size >= proxyCap) {
                    log?.invoke("Достигнут лимит $proxyCap прокси")
                    break
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                synchronized(errors) { errors.add("CDX $snapshotId: ${e.message}") }
                log?.invoke("CDX ${i + 1}/${batch.size} ($snapshotId): ${e.message}")
            }
        }
        return loaded
    }

    private suspend fun emitNewProxies(
        parsed: List<ProxyEntry>,
        seen: MutableSet<String>,
        output: MutableList<ProxyEntry>,
        proxyCap: Int
    ): Int {
        var added = 0
        for (p in parsed) {
            coroutineContext.ensureActive()
            if (seen.size >= proxyCap && !seen.contains(p.key)) break

            if (seen.contains(p.key)) {
                mergePublishedDate(output, p)
                continue
            }

            seen.add(p.key)
            added++
            output.add(p)
        }
        return added
    }

    private fun mergePublishedDate(output: MutableList<ProxyEntry>, incoming: ProxyEntry) {
        val publishedAt = incoming.publishedAt ?: return
        for (i in output.indices) {
            if (!output[i].key.equals(incoming.key, ignoreCase = true)) continue
            val existing = output[i]
            if (existing.publishedAt == null || publishedAt.isAfter(existing.publishedAt)) {
                existing.publishedAt = publishedAt
            }
            return
        }
    }

    private fun reportCdx(
        progress: ((CollectProgress) -> Unit)?,
        snapshotsDone: Int,
        snapshotsTarget: Int,
        proxiesFound: Int,
        proxyCap: Int
    ) {
        progress?.invoke(
            CollectProgress(
                isCdxPhase = true,
                cdxSnapshotsDone = snapshotsDone,
                cdxSnapshotsTarget = maxOf(1, snapshotsTarget),
                proxiesFound = proxiesFound,
                proxiesTarget = proxyCap
            )
        )
    }

    companion object {
        const val DEFAULT_MAX_RECENT_PROXIES = 100
        const val DEFAULT_MAX_CDX_SNAPSHOTS = 30
        private const val MAX_FEED_PAGES_TO_SCAN = 40
        private const val MS_BETWEEN_FEED_PAGES = 900L
        private const val MS_BETWEEN_CDX_SNAPSHOTS = 1200L
        private const val ARCHIVE_ENTRY_URL =
            "https://web.archive.org/web/2/https://t.me/s/ProxyMTProto"
    }
}
