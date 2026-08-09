package com.proxypulse.data.health

import com.proxypulse.data.health.mtproxy.MtProxySecret
import com.proxypulse.data.health.mtproxy.MtProxyVerifier
import com.proxypulse.domain.ProxyEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

data class ScanProgress(val completed: Int, val total: Int)

data class ProxyCheckResult(
    val entry: ProxyEntry,
    val isAvailable: Boolean,
    val pingMs: Int?
)

/**
 * Phase 1 — parallel availability (TCP).
 * Phase 2 / recheck — low-concurrency full check (MT + TCP RTT) for accurate ping.
 */
class ProxyHealthService(
    private val mtProxyTimeoutMs: Int = MtProxyVerifier.DEFAULT_CHECK_TIMEOUT_MS,
    private val tcpTimeoutMs: Int = 3500,
    private val availabilityConcurrency: Int = 12,
    private val refineConcurrency: Int = 2
) {
    /** Parallel TCP availability; available proxies appear ASAP with provisional ping. */
    suspend fun scanAvailability(
        proxies: List<ProxyEntry>,
        onProgress: (ScanProgress) -> Unit = {},
        onChecked: (ProxyCheckResult) -> Unit = {}
    ) = runParallel(proxies, availabilityConcurrency, onProgress, onChecked) {
        checkAvailable(it)
    }

    /** Accurate ping pass — same measurement as manual refresh, low concurrency. */
    suspend fun refinePings(
        proxies: List<ProxyEntry>,
        onProgress: (ScanProgress) -> Unit = {},
        onChecking: (ProxyEntry) -> Unit = {},
        onChecked: (ProxyCheckResult) -> Unit = {}
    ) = runParallel(proxies, refineConcurrency, onProgress, onChecked, onChecking) {
        checkOne(it)
    }

    suspend fun checkOne(proxy: ProxyEntry): ProxyCheckResult = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()
        val mtPing = MtProxyVerifier.measurePing(
            host = proxy.server,
            port = proxy.port,
            secretHex = proxy.secret,
            timeoutMs = mtProxyTimeoutMs
        )
        val tcpPing = measureTcpPing(proxy.server, proxy.port, proxy.secret)
        if (mtPing == null && tcpPing == null) {
            return@withContext ProxyCheckResult(proxy, isAvailable = false, pingMs = null)
        }
        ProxyCheckResult(
            entry = proxy,
            isAvailable = true,
            pingMs = tcpPing ?: mtPing
        )
    }

    /** Lightweight TCP-only check for background refresh. */
    suspend fun checkLight(proxy: ProxyEntry): ProxyCheckResult = checkAvailable(proxy)

    private suspend fun checkAvailable(proxy: ProxyEntry): ProxyCheckResult = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()
        val tcpPing = measureTcpPing(proxy.server, proxy.port, proxy.secret)
            ?: return@withContext ProxyCheckResult(proxy, isAvailable = false, pingMs = null)
        ProxyCheckResult(proxy, isAvailable = true, pingMs = tcpPing)
    }

    private suspend fun runParallel(
        proxies: List<ProxyEntry>,
        concurrency: Int,
        onProgress: (ScanProgress) -> Unit,
        onChecked: (ProxyCheckResult) -> Unit,
        onChecking: (ProxyEntry) -> Unit = {},
        check: suspend (ProxyEntry) -> ProxyCheckResult
    ) = coroutineScope {
        if (proxies.isEmpty()) return@coroutineScope
        val total = proxies.size
        val completed = java.util.concurrent.atomic.AtomicInteger(0)
        val gate = Semaphore(concurrency.coerceAtLeast(1))

        proxies.map { proxy ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    try {
                        coroutineContext.ensureActive()
                        onChecking(proxy)
                        onChecked(check(proxy))
                    } finally {
                        onProgress(ScanProgress(completed.incrementAndGet(), total))
                    }
                }
            }
        }.awaitAll()
    }

    private fun measureTcpPing(host: String, port: Int, secret: String): Int? {
        if (MtProxySecret.parse(secret) == null) return null
        if (host.isBlank() || port !in 1..65535) return null
        val start = System.currentTimeMillis()
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), tcpTimeoutMs)
                if (socket.isConnected) {
                    maxOf(1, (System.currentTimeMillis() - start).toInt())
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
