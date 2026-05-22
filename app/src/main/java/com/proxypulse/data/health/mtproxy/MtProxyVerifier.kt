package com.proxypulse.data.health.mtproxy

/**
 * Checks MTProxy like Telegram: Fake-TLS + obfuscated init; then MTProto to DC when possible.
 */
object MtProxyVerifier {
    const val DEFAULT_CHECK_TIMEOUT_MS = 12_000

    fun measurePing(
        host: String,
        port: Int,
        secretHex: String,
        timeoutMs: Int = DEFAULT_CHECK_TIMEOUT_MS
    ): Int? {
        if (MtProxySecret.parse(secretHex) == null) return null
        val start = System.currentTimeMillis()
        val session = MtProxyConnection.openSession(host, port, secretHex, timeoutMs) ?: return null
        return try {
            val left = (timeoutMs - (System.currentTimeMillis() - start)).toInt().coerceAtLeast(500)
            if (MtProtoTelegramCheck.verifyWithSession(session, left)) {
                maxOf(1, (System.currentTimeMillis() - start).toInt())
            } else {
                null
            }
        } finally {
            session.close()
        }
    }
}
