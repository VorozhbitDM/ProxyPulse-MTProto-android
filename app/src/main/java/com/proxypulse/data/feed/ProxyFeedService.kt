package com.proxypulse.data.feed

import com.proxypulse.data.network.HttpDownloadHelper
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
        maxCdxSnapshotsToScan: Int = DEFAULT_MAX_CDX_SNAPSHOTS
    ) = coroutineScope {
        val cap = maxOf(1, maxRecentProxies)
        val cdxCap = maxOf(1, maxCdxSnapshotsToScan)
        val seen = linkedSetOf<String>()
        val errors = mutableListOf<String>()

        reportCdx(progress, 0, cdxCap, 0, cap)
        log?.invoke("Сбор: лента (1 стр.) + до $cdxCap снимков CDX → $cap прокси…")

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

        log?.invoke("Сбор завершён: ${seen.size}/$cap прокси · CDX снимков $cdxOk")
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
                val html = HttpDownloadHelper.download(candidate, "https://web.archive.org/")
                val parsed = ProxyLinkParser.parse(html)
                if (!ProxyLinkParser.looksLikeTelegramFeed(html) && parsed.isEmpty()) {
                    log?.invoke("Лента: ответ без постов Telegram, другой URL…")
                    continue
                }
                val snapshotId = ProxyLinkParser.getArchiveSnapshotId(html)
                onSnapshot(snapshotId)
                val added = emitNewProxies(parsed, seen, output, proxyCap)
                if (!snapshotId.isNullOrEmpty()) {
                    log?.invoke("Лента: снимок $snapshotId, +$added новых, всего ${seen.size}/$proxyCap")
                } else {
                    log?.invoke("Лента: +$added новых, всего ${seen.size}/$proxyCap")
                }
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
                val parsed = ProxyLinkParser.parse(html)
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
            if (seen.size >= proxyCap) break
            if (!seen.add(p.key)) continue
            added++
            output.add(p)
        }
        return added
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
        private const val MS_BETWEEN_CDX_SNAPSHOTS = 1200L
        private const val ARCHIVE_ENTRY_URL =
            "https://web.archive.org/web/2/https://t.me/s/ProxyMTProto"
    }
}
