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
 * Availability: MTProxy handshake when possible, TCP+secret as fallback.
 * Displayed ping is always TCP connect RTT — not full handshake time
 * (handshake under 30-way scan looks much worse than a single recheck).
 */
class ProxyHealthService(
    private val mtProxyTimeoutMs: Int = MtProxyVerifier.DEFAULT_CHECK_TIMEOUT_MS,
    private val tcpTimeoutMs: Int = 3500,
    private val concurrency: Int = 12
) {
    suspend fun scan(
        proxies: List<ProxyEntry>,
        onProgress: (ScanProgress) -> Unit = {},
        onChecking: (ProxyEntry) -> Unit = {},
        onChecked: (ProxyCheckResult) -> Unit = {}
    ) = coroutineScope {
        if (proxies.isEmpty()) return@coroutineScope
        val total = proxies.size
        val completed = java.util.concurrent.atomic.AtomicInteger(0)
        val gate = Semaphore(concurrency)

        proxies.map { proxy ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    try {
                        coroutineContext.ensureActive()
                        onChecking(proxy)
                        val result = checkOne(proxy)
                        onChecked(result)
                        result
                    } finally {
                        val done = completed.incrementAndGet()
                        onProgress(ScanProgress(done, total))
                    }
                }
            }
        }.awaitAll()
    }

    suspend fun checkOne(proxy: ProxyEntry): ProxyCheckResult = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()
        // Handshake first (also warms DNS); then a short TCP connect for the UI ping.
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
