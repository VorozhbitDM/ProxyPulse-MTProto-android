package com.proxypulse.data.health.mtproxy

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MTProto intermediate protocol over an authenticated MTProxy tunnel (tgnet Connection::sendMessageData).
 */
internal class MtProtoTransport(
    private val session: MtProxySession,
    private val timeoutMs: Int
) {
    private val encryptStream = session.sendCipher
    private val decryptStream = session.recvCipher
    private val tlsRecordHeader = byteArrayOf(0x17, 0x03, 0x03)

    fun sendPlainMessage(body: ByteArray) {
        val inner = ByteArray(20 + body.size)
        ByteBuffer.wrap(inner).order(ByteOrder.LITTLE_ENDIAN).apply {
            putLong(0L)
            putLong(MtProtoMessageId.next())
            putInt(body.size)
            put(body)
        }
        sendPacket(wrapIntermediate(inner))
    }

    fun receivePlainMessage(): ByteArray? {
        val inner = receivePacket() ?: return null
        if (inner.size < 20) return null
        val bb = ByteBuffer.wrap(inner).order(ByteOrder.LITTLE_ENDIAN)
        bb.long
        bb.long
        val len = bb.int
        if (len <= 0 || inner.size < 20 + len) return null
        return inner.copyOfRange(20, 20 + len)
    }

    private fun wrapIntermediate(inner: ByteArray): ByteArray {
        val lenBytes = ByteArray(4)
        ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).putInt(inner.size)
        return encryptStream.process(lenBytes) + encryptStream.process(inner)
    }

    private fun unwrapIntermediate(packet: ByteArray): ByteArray? {
        if (packet.size < 4) return null
        val lenBytes = decryptStream.process(packet.copyOfRange(0, 4))
        val bodyLen = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).int
        if (bodyLen <= 0 || packet.size < 4 + bodyLen) return null
        return decryptStream.process(packet.copyOfRange(4, 4 + bodyLen))
    }

    private fun sendPacket(payload: ByteArray) {
        if (session.tlsWrapped) {
            val frame = ByteArray(5 + payload.size)
            System.arraycopy(tlsRecordHeader, 0, frame, 0, 3)
            frame[3] = ((payload.size shr 8) and 0xFF).toByte()
            frame[4] = (payload.size and 0xFF).toByte()
            System.arraycopy(payload, 0, frame, 5, payload.size)
            session.output.write(frame)
        } else {
            session.output.write(payload)
        }
        session.output.flush()
    }

    private fun receivePacket(): ByteArray? {
        val payload = if (session.tlsWrapped) {
            readTlsPayload(session.input) ?: return null
        } else {
            readRawPayload(session.input) ?: return null
        }
        return unwrapIntermediate(payload)
    }

    private fun readTlsPayload(input: InputStream): ByteArray? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val header = readExact(input, 5, deadline) ?: return null
            if (header[0] != 0x17.toByte() || header[1] != 0x03.toByte() || header[2] != 0x03.toByte()) {
                continue
            }
            val len = ((header[3].toInt() and 0xFF) shl 8) or (header[4].toInt() and 0xFF)
            if (len <= 0 || len > 64 * 1024) continue
            val payload = readExact(input, len, deadline) ?: return null
            if (payload.size >= 4) return payload
        }
        return null
    }

    private fun readRawPayload(input: InputStream): ByteArray? {
        val deadline = System.currentTimeMillis() + timeoutMs
        val header = readExact(input, 4, deadline) ?: return null
        val len = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).int and 0x7FFFFFFF
        if (len <= 0 || len > 2 * 1024 * 1024) return null
        return readExact(input, len, deadline)
    }

    private fun readExact(input: InputStream, length: Int, deadline: Long): ByteArray? {
        val buf = ByteArray(length)
        var off = 0
        while (off < length) {
            if (System.currentTimeMillis() > deadline) return null
            val read = input.read(buf, off, length - off)
            if (read < 0) return null
            if (read == 0) continue
            off += read
        }
        return buf
    }
}
