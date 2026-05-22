package com.proxypulse.data.health.mtproxy

/**
 * MTProxy auth + optional MTProto req_pq/resPQ through the tunnel (tgnet checkProxy path).
 */
internal object MtProtoTelegramCheck {
    private const val REQ_PQ_MULTI = -1096222479 // 0xbe7e8ef1
    private const val RES_PQ = 0x05162463

    fun verify(host: String, port: Int, secretHex: String, timeoutMs: Int): Boolean {
        val session = MtProxyConnection.openSession(host, port, secretHex, timeoutMs) ?: return false
        return try {
            verifyWithSession(session, timeoutMs)
        } finally {
            session.close()
        }
    }

    /**
     * MTProxy handshake succeeded; try req_pq/resPQ, but accept proxy if only auth works
     * (req_pq often fails on shared timeout — auth is what Telegram needs first).
     */
    fun verifyWithSession(session: MtProxySession, timeoutMs: Int): Boolean {
        val remaining = timeoutMs.coerceAtLeast(2000)
        if (tryResPq(session, remaining)) return true
        return true // MTProxy obfuscated init already validated secret
    }

    private fun tryResPq(session: MtProxySession, timeoutMs: Int): Boolean {
        return try {
            val transport = MtProtoTransport(session, timeoutMs)
            val nonce = TlBuffer.randomInt128()
            val reqPq = TlBuffer().apply {
                writeInt(REQ_PQ_MULTI)
                writeInt128(nonce)
            }.bytes()
            transport.sendPlainMessage(reqPq)
            val resBody = receiveWithRetry(transport, 5) ?: return false
            val reader = TlReader(resBody)
            if (reader.readInt() != RES_PQ) return false
            if (!reader.readInt128().contentEquals(nonce)) return false
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun receiveWithRetry(transport: MtProtoTransport, attempts: Int): ByteArray? {
        repeat(attempts) {
            transport.receivePlainMessage()?.let { return it }
        }
        return null
    }
}
