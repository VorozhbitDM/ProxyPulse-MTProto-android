package com.proxypulse.data.feed

import com.proxypulse.data.network.HttpDownloadHelper
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object ArchiveCdxService {
    const val FRESH_SNAPSHOT_MAX_AGE_DAYS = 120

    private val cacheTtlMs = 15 * 60 * 1000L
    private val cacheLock = ReentrantLock()
    private var cachedIds: List<String>? = null
    private var cachedAtMs: Long = 0

    private const val SORT_NEWEST_FIRST = "&sort=reverse"

    private val cdxUrlCandidates = listOf(
        "https://web.archive.org/cdx/search/cdx?url=t.me/s/ProxyMTProto&output=text&fl=timestamp&collapse=timestamp&limit=40$SORT_NEWEST_FIRST",
        "https://web.archive.org/cdx/search/cdx?url=t.me/s/ProxyMTProto&output=json&fl=timestamp&filter=statuscode:200&collapse=digest&limit=40$SORT_NEWEST_FIRST",
        "https://web.archive.org/cdx/search/cdx?url=https://t.me/s/ProxyMTProto&output=json&fl=timestamp&filter=statuscode:200&collapse=digest&limit=40$SORT_NEWEST_FIRST"
    )

    private val timestampJsonRegex = Regex("""\["(\d{14})"\]""")
    private val timestampFallbackRegex = Regex("""(\d{14})""")

    suspend fun getRecentSnapshotIds(log: ((String) -> Unit)? = null): List<String> {
        cacheLock.withLock {
            val cached = cachedIds
            if (!cached.isNullOrEmpty() && System.currentTimeMillis() - cachedAtMs < cacheTtlMs) {
                log?.invoke("CDX: список из кэша (${cached.size} снимков)")
                return cached
            }
        }

        log?.invoke("CDX: запрос списка снимков (сервер archive.org, обычно 5–20 с)…")
        val ids = mutableListOf<String>()
        var lastError: String? = null

        for (url in cdxUrlCandidates) {
            val isText = url.contains("output=text", ignoreCase = true)
            try {
                val body = HttpDownloadHelper.download(
                    url = url,
                    referer = "https://web.archive.org/",
                    timeoutMs = HttpDownloadHelper.CDX_LIST_TIMEOUT_MS,
                    maxAttempts = 1
                )
                parseTimestamps(body, isText, ids)
                if (ids.isNotEmpty()) {
                    cacheLock.withLock {
                        cachedIds = ids.toList()
                        cachedAtMs = System.currentTimeMillis()
                    }
                    val filtered = filterNewestFirst(ids)
                    val newest = filtered.firstOrNull() ?: "—"
                    log?.invoke(
                        "CDX: ${filtered.size} снимков (сначала самые новые, от ${formatSnapshotDate(newest)})"
                    )
                    return filtered
                }
                lastError = "пустой ответ"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e.message
                log?.invoke("CDX: ${e.message}")
            }
        }

        if (ids.isEmpty()) {
            log?.invoke("CDX: список не получен (${lastError ?: "нет данных"})")
        }
        return filterNewestFirst(ids)
    }

    fun filterNewestFirst(ids: List<String>): List<String> {
        if (ids.isEmpty()) return emptyList()
        val cutoff = getFreshnessCutoffTimestamp()
        return ids
            .filter { it.length == 14 && it.all { ch -> ch.isDigit() } }
            .filter { it >= cutoff }
            .distinct()
            .sortedDescending()
    }

    fun getFreshnessCutoffTimestamp(): String {
        val dt = Instant.now().atOffset(ZoneOffset.UTC).minusDays(FRESH_SNAPSHOT_MAX_AGE_DAYS.toLong())
        return DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(dt)
    }

    private fun formatSnapshotDate(timestampId: String): String {
        if (timestampId.length != 14) return timestampId
        return try {
            val dt = java.time.LocalDateTime.parse(
                timestampId,
                DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            )
            "${dt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))} UTC"
        } catch (_: Exception) {
            timestampId
        }
    }

    private fun parseTimestamps(body: String, isText: Boolean, ids: MutableList<String>) {
        if (body.isEmpty()) return
        val seen = ids.toMutableSet()
        if (isText) {
            for (line in body.split('\n', '\r')) {
                val t = line.trim().trim('"', '[', ']', ',')
                if (t.length != 14 || !t[0].isDigit()) continue
                if (t.equals("timestamp", ignoreCase = true)) continue
                if (seen.add(t)) ids.add(t)
            }
            return
        }
        for (m in timestampJsonRegex.findAll(body)) {
            val ts = m.groupValues[1]
            if (seen.add(ts)) ids.add(ts)
        }
        if (ids.isNotEmpty()) return
        for (m in timestampFallbackRegex.findAll(body)) {
            val ts = m.groupValues[1]
            if (seen.add(ts)) ids.add(ts)
        }
    }
}
