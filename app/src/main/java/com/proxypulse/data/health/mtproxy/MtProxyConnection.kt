package com.proxypulse.data.health.mtproxy

import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MTProxy auth (Fake-TLS + 64-byte obfuscated init) — tgnet ConnectionSocket.cpp.
 */
internal object MtProxyConnection {
    private val TLS_CLIENT_ACK = byteArrayOf(0x14, 0x03, 0x03, 0x00, 0x01, 0x01)
    private val TLS_RECORD = byteArrayOf(0x17, 0x03, 0x03)

    fun openSession(host: String, port: Int, secretHex: String, timeoutMs: Int): MtProxySession? {
        val secret = MtProxySecret.parse(secretHex) ?: return null
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            socket.soTimeout = timeoutMs
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            val tlsWrapped = secret.isFakeTls && runFakeTls(input, output, secret, timeoutMs)
            return runObfuscated(socket, input, output, secret, wrappedInTls = tlsWrapped, timeoutMs)
        } catch (_: Exception) {
            runCatching { socket.close() }
            return null
        }
    }

    private fun runFakeTls(
        input: InputStream,
        output: OutputStream,
        secret: MtProxySecret,
        timeoutMs: Int
    ): Boolean {
        val domain = secret.fakeTlsDomain ?: return false
        val unixTime = (System.currentTimeMillis() / 1000).toInt()
        val built = TelegramTlsHello.buildWithHmac(secret.raw, domain, unixTime) ?: return false
        val (hello, clientHmacWorkspace) = built
        output.write(hello)
        output.flush()

        val accumulated = java.io.ByteArrayOutputStream()
        val deadline = System.currentTimeMillis() + timeoutMs
        val chunk = ByteArray(4096)
        while (accumulated.size() < 64 * 1024 && System.currentTimeMillis() < deadline) {
            val read = input.read(chunk)
            if (read < 0) return false
            if (read == 0) continue
            accumulated.write(chunk, 0, read)
            val data = accumulated.toByteArray()
            if (data.size < 16) continue
            if (data[0] != 0x16.toByte() || data[1] != 0x03.toByte() || data[2] != 0x03.toByte()) continue
            val len1 = ((data[3].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF)
            if (len1 > 64 * 1024 - 5) return false
            if (data.size < len1 + 5) continue
            val hello2 = byteArrayOf(0x14, 0x03, 0x03, 0x00, 0x01, 0x01, 0x17, 0x03, 0x03)
            if (data.size < len1 + 5 + hello2.size) continue
            if (!data.copyOfRange(len1 + 5, len1 + 5 + hello2.size).contentEquals(hello2)) continue
            val len2Off = len1 + 5 + 9
            val len2 = ((data[len2Off].toInt() and 0xFF) shl 8) or (data[len2Off + 1].toInt() and 0xFF)
            val total = len1 + 5 + 11 + len2
            if (data.size < total) continue

            val serverDigest = data.copyOfRange(11, 43)
            val msg = ByteArray(32 + data.size)
            System.arraycopy(clientHmacWorkspace, 0, msg, 0, 32)
            val cleared = data.copyOf()
            cleared.fill(0, 11, 43)
            System.arraycopy(cleared, 0, msg, 32, cleared.size)
            val expected = MtCrypto.hmacSha256(secret.raw, msg)
            if (!expected.contentEquals(serverDigest)) return false

            output.write(TLS_CLIENT_ACK)
            output.flush()
            return true
        }
        return false
    }

    private fun runObfuscated(
        socket: Socket,
        input: InputStream,
        output: OutputStream,
        secret: MtProxySecret,
        wrappedInTls: Boolean,
        timeoutMs: Int
    ): MtProxySession? {
        val built = MtProxyObfuscated.buildClientInit(secret) ?: return null
        val clientInit = built.wirePacket

        val sendCipher = AesCtrState(built.encryptKey, built.encryptIv)
        sendCipher.advance(64)

        if (wrappedInTls) {
            val record = ByteArray(5 + clientInit.size)
            System.arraycopy(TLS_RECORD, 0, record, 0, 3)
            record[3] = ((clientInit.size shr 8) and 0xFF).toByte()
            record[4] = (clientInit.size and 0xFF).toByte()
            System.arraycopy(clientInit, 0, record, 5, clientInit.size)
            output.write(record)
        } else {
            output.write(clientInit)
        }
        output.flush()

        val serverInit = if (wrappedInTls) {
            readObfuscatedViaTls(input, timeoutMs)
        } else {
            readExact(input, 64, timeoutMs)
        } ?: return null

        val recvCipher = AesCtrState(built.decryptKey, built.decryptIv)
        recvCipher.advance(64)

        val serverPlain = serverInit.copyOf()
        MtProxyObfuscated.decryptServerTail(serverPlain, built.decryptKey, built.decryptIv)
        if (!MtProxyObfuscated.isValidInit(serverPlain)) return null

        return MtProxySession(
            socket = socket,
            input = input,
            output = output,
            secret = secret,
            sendCipher = sendCipher,
            recvCipher = recvCipher,
            tlsWrapped = wrappedInTls
        )
    }

    private fun readObfuscatedViaTls(input: InputStream, timeoutMs: Int): ByteArray? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val header = readExact(input, 5, (deadline - System.currentTimeMillis()).toInt()) ?: return null
            if (header[0] != 0x17.toByte() || header[1] != 0x03.toByte() || header[2] != 0x03.toByte()) {
                continue
            }
            val len = ((header[3].toInt() and 0xFF) shl 8) or (header[4].toInt() and 0xFF)
            if (len < 64) continue
            val payload = readExact(input, len, (deadline - System.currentTimeMillis()).toInt()) ?: return null
            if (payload.size >= 64) return payload.copyOfRange(0, 64)
        }
        return null
    }

    private fun readExact(input: InputStream, length: Int, timeoutMs: Int): ByteArray? {
        if (timeoutMs <= 0) return null
        val buf = ByteArray(length)
        var off = 0
        val deadline = System.currentTimeMillis() + timeoutMs
        while (off < length) {
            if (System.currentTimeMillis() > deadline) return null
            val read = input.read(buf, off, length - off)
            if (read < 0) return null
            off += read
        }
        return buf
    }
}

/** Telethon/tgnet MTProxy obfuscated 64-byte init. */
internal object MtProxyObfuscated {
    private val BLOCKED_FIRST_INT = intArrayOf(
        0x44414548,
        0x54534F50,
        0x20544547,
        0x4954504F,
        0xDDDDDDDD.toInt(),
        0xEEEEEEEE.toInt(),
        0x02010316
    )

    data class ClientInitResult(
        val wirePacket: ByteArray,
        val encryptKey: ByteArray,
        val encryptIv: ByteArray,
        val decryptKey: ByteArray,
        val decryptIv: ByteArray
    )

    fun buildClientInit(secret: MtProxySecret): ClientInitResult? {
        val tag = if (secret.randomPadding) {
            byteArrayOf(0xDD.toByte(), 0xDD.toByte(), 0xDD.toByte(), 0xDD.toByte())
        } else {
            byteArrayOf(0xEE.toByte(), 0xEE.toByte(), 0xEE.toByte(), 0xEE.toByte())
        }
        var packet: ByteArray
        var buf: ByteBuffer
        do {
            packet = MtCrypto.randomBytes(64)
            buf = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
            val first = buf.getInt(0)
            if (packet[0] == 0xEF.toByte()) continue
            if (BLOCKED_FIRST_INT.any { it == first }) continue
            if (buf.getInt(4) == 0) continue
            break
        } while (true)

        System.arraycopy(tag, 0, packet, 56, 4)
        // DC2 for MTProto (tgnet bytes[60..61] = datacenter id)
        packet[60] = 2
        packet[61] = 0

        val encryptKey = MtCrypto.sha256(packet.copyOfRange(8, 40), secret.raw)
        val encryptIv = packet.copyOfRange(40, 56)
        val reversed = packet.copyOfRange(8, 56).reversedArray()
        val decryptKey = MtCrypto.sha256(reversed.copyOfRange(0, 32), secret.raw)
        val decryptIv = reversed.copyOfRange(32, 48)

        val encryptedTail = MtCrypto.aesCtrTransform(encryptKey, encryptIv, packet.copyOfRange(56, 64))
        System.arraycopy(encryptedTail, 0, packet, 56, 8)
        return ClientInitResult(packet, encryptKey, encryptIv, decryptKey, decryptIv)
    }

    fun decryptServerTail(packet: ByteArray, decryptKey: ByteArray, decryptIv: ByteArray) {
        val plain = MtCrypto.aesCtrTransform(decryptKey, decryptIv, packet.copyOfRange(56, 64))
        System.arraycopy(plain, 0, packet, 56, 8)
    }

    fun isValidInit(packet: ByteArray): Boolean {
        if (packet.size < 64) return false
        val tag = ByteBuffer.wrap(packet, 56, 4).order(ByteOrder.LITTLE_ENDIAN).getInt(0)
        if (tag == 0xEEEEEEEE.toInt() ||
            tag == 0xDDDDDDDD.toInt() ||
            tag == 0xEFEFEFEF.toInt() ||
            packet[56] == 0xEF.toByte()
        ) {
            return true
        }
        if (packet[0] == 0xEF.toByte()) return false
        val first = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).getInt(0)
        if (BLOCKED_FIRST_INT.any { it == first }) return false
        return ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).getInt(4) != 0
    }
}
