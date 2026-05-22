package com.proxypulse.data.health.mtproxy

import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

/** Authenticated MTProxy tunnel with CTR streams aligned to tgnet after 64-byte init. */
internal class MtProxySession(
    val socket: Socket,
    val input: InputStream,
    val output: OutputStream,
    val secret: MtProxySecret,
    val sendCipher: AesCtrState,
    val recvCipher: AesCtrState,
    val tlsWrapped: Boolean
) {
    fun close() = runCatching { socket.close() }
}
