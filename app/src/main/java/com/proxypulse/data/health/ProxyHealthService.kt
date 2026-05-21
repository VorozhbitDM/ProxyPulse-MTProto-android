package com.proxypulse.data.health

import com.proxypulse.domain.ProxyEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetAddress
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

class ProxyHealthService(
    private val dnsTimeoutMs: Int = 2000,
    private val connectTimeoutMs: Int = 2000,
    private val concurrency: Int = 50
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
        val pingMs = measureTcpPing(proxy.server, proxy.port)
        ProxyCheckResult(
            entry = proxy,
            isAvailable = pingMs != null,
            pingMs = pingMs
        )
    }

    private fun measureTcpPing(host: String, port: Int): Int? {
        val addresses = resolveAddresses(host) ?: return null
        for (address in orderAddresses(addresses)) {
            val start = System.currentTimeMillis()
            if (tryConnect(address, port)) {
                return maxOf(1, (System.currentTimeMillis() - start).toInt())
            }
        }
        return null
    }

    private fun resolveAddresses(host: String): Array<InetAddress>? {
        return try {
            val parsed = InetAddress.getByName(host)
            if (parsed.hostAddress == host || !parsed.hostAddress.contains(":")) {
                arrayOf(parsed)
            } else {
                InetAddress.getAllByName(host)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun orderAddresses(addresses: Array<InetAddress>): List<InetAddress> =
        addresses.sortedWith(
            compareBy<InetAddress> {
                when {
                    it.address.size == 4 -> 0
                    it.address.size == 16 -> 1
                    else -> 2
                }
            }
        )

    private fun tryConnect(address: InetAddress, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(address, port), connectTimeoutMs)
                socket.isConnected
            }
        } catch (_: Exception) {
            false
        }
    }
}
