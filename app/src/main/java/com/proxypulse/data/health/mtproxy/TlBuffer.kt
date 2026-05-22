package com.proxypulse.data.health.mtproxy

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

internal class TlBuffer(initial: Int = 64) {
    private val buf = ByteBuffer.allocate(initial).order(ByteOrder.LITTLE_ENDIAN)

    fun bytes(): ByteArray {
        val out = ByteArray(buf.position())
        buf.flip()
        buf.get(out)
        buf.clear()
        buf.order(ByteOrder.LITTLE_ENDIAN)
        return out
    }

    fun position(): Int = buf.position()

    fun writeInt(value: Int) = buf.putInt(value)
    fun writeLong(value: Long) = buf.putLong(value)
    fun writeByte(value: Int) = buf.put(value.toByte())
    fun writeBytes(data: ByteArray) = buf.put(data)

    fun writeInt128(value: ByteArray) {
        require(value.size == 16)
        writeBytes(value)
    }

    fun writeInt256(value: ByteArray) {
        require(value.size == 32)
        writeBytes(value)
    }

    fun writeString(value: String) {
        val raw = value.toByteArray(Charsets.UTF_8)
        if (raw.size <= 253) {
            writeByte(raw.size)
            writeBytes(raw)
            val pad = (4 - ((1 + raw.size) % 4)) % 4
            repeat(pad) { writeByte(0) }
        } else {
            writeByte(254)
            writeByte(raw.size)
            writeByte(raw.size shr 8)
            writeByte(raw.size shr 16)
            writeBytes(raw)
            val pad = (4 - (raw.size % 4)) % 4
            repeat(pad) { writeByte(0) }
        }
    }

    fun writeBytes128(value: ByteArray) {
        require(value.size <= 127)
        writeByte(value.size)
        writeBytes(value)
        padInPlace()
    }

    private fun padInPlace() {
        val pad = (4 - (position() % 4)) % 4
        repeat(pad) { writeByte(0) }
    }

    companion object {
        fun randomInt128(): ByteArray = ByteArray(16).also { Random.Default.nextBytes(it) }
        fun randomInt256(): ByteArray = ByteArray(32).also { Random.Default.nextBytes(it) }
    }
}

internal class TlReader(data: ByteArray) {
    private val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

    fun readInt(): Int = buf.int
    fun readLong(): Long = buf.long

    fun readInt128(): ByteArray {
        val out = ByteArray(16)
        buf.get(out)
        return out
    }

    fun readInt256(): ByteArray {
        val out = ByteArray(32)
        buf.get(out)
        return out
    }

    fun readString(): String {
        val first = buf.get().toInt() and 0xFF
        val len = if (first == 254) {
            val b0 = buf.get().toInt() and 0xFF
            val b1 = buf.get().toInt() and 0xFF
            val b2 = buf.get().toInt() and 0xFF
            b0 or (b1 shl 8) or (b2 shl 16)
        } else {
            first
        }
        val bytes = ByteArray(len)
        buf.get(bytes)
        val consumed = if (first == 254) 4 + len else 1 + len
        val pad = (4 - (consumed % 4)) % 4
        buf.position(buf.position() + pad)
        return String(bytes, Charsets.UTF_8)
    }

    fun remaining(): Int = buf.remaining()
}
