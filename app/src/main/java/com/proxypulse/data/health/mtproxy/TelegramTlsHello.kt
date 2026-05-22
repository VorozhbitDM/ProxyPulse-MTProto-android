package com.proxypulse.data.health.mtproxy

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import kotlin.random.Random

/**
 * Port of tgnet `TlsHello` from Telegram Android `ConnectionSocket.cpp`.
 */
internal class TelegramTlsHello private constructor(
    private val ops: List<Op>,
    private val grease: ByteArray,
    private var domain: String
) {
    private val scopeStack = ArrayDeque<Int>()

    fun setDomain(value: String) {
        domain = value
    }

    fun writeToBuffer(out: ByteArray): Int {
        var offset = 0
        scopeStack.clear()
        for (op in ops) {
            offset = writeOp(op, out, offset)
        }
        return offset
    }

    private fun writeOp(op: Op, data: ByteArray, offsetStart: Int): Int {
        var offset = offsetStart
        when (op.type) {
            OpType.String -> {
                op.bytes?.let {
                    System.arraycopy(it, 0, data, offset, it.size)
                    offset += it.size
                }
            }
            OpType.Random -> {
                val tmp = ByteArray(op.length)
                RANDOM.nextBytes(tmp)
                System.arraycopy(tmp, 0, data, offset, op.length)
                offset += op.length
            }
            OpType.K -> {
                generatePublicKey(data, offset)
                offset += 32
            }
            OpType.M -> {
                generateMlKem768(data, offset)
                offset += 1184
            }
            OpType.Zero -> {
                offset += op.length
            }
            OpType.Domain -> {
                val size = minOf(domain.length, 253)
                System.arraycopy(domain.toByteArray(Charsets.UTF_8), 0, data, offset, size)
                offset += size
            }
            OpType.Grease -> {
                val g = grease[op.seed % grease.size]
                data[offset] = g
                data[offset + 1] = g
                offset += 2
            }
            OpType.BeginScope -> {
                scopeStack.addLast(offset)
                offset += 2
            }
            OpType.EndScope -> {
                val begin = scopeStack.removeLast()
                val size = offset - begin - 2
                data[begin] = ((size shr 8) and 0xFF).toByte()
                data[begin + 1] = (size and 0xFF).toByte()
            }
            OpType.E -> {
                val r = RANDOM.nextInt(4)
                val length = when (r) {
                    0 -> 144
                    1 -> 176
                    2 -> 208
                    else -> 240
                }
                val tmp = ByteArray(length)
                RANDOM.nextBytes(tmp)
                System.arraycopy(tmp, 0, data, offset, length)
                offset += length
            }
            OpType.P -> {
                val length = offset
                if (length <= 513) {
                    offset = writeOp(Op.string(byteArrayOf(0x00, 0x15)), data, offset)
                    offset = writeOp(Op.beginScope(), data, offset)
                    offset = writeOp(Op.zero(513 - length), data, offset)
                    offset = writeOp(Op.endScope(), data, offset)
                }
            }
            OpType.Permutation -> {
                val parts = op.entities!!.toMutableList()
                for (i in parts.indices) {
                    val j = i + RANDOM.nextInt(parts.size - i)
                    val tmp = parts[i]
                    parts[i] = parts[j]
                    parts[j] = tmp
                }
                for (part in parts) {
                    for (child in part) {
                        offset = writeOp(child, data, offset)
                    }
                }
            }
        }
        return offset
    }

    private enum class OpType {
        String, Random, K, M, P, E, Zero, Domain, Grease, BeginScope, EndScope, Permutation
    }

    private data class Op(
        val type: OpType,
        val length: Int = 0,
        val seed: Int = 0,
        val bytes: ByteArray? = null,
        val entities: List<List<Op>>? = null
    ) {
        companion object {
            fun string(bytes: ByteArray) = Op(OpType.String, bytes = bytes)
            fun random(length: Int) = Op(OpType.Random, length = length)
            fun zero(length: Int) = Op(OpType.Zero, length = length)
            fun grease(seed: Int) = Op(OpType.Grease, seed = seed)
            fun beginScope() = Op(OpType.BeginScope)
            fun endScope() = Op(OpType.EndScope)
            fun domain() = Op(OpType.Domain)
            fun k() = Op(OpType.K, length = 32)
            fun m() = Op(OpType.M)
            fun e() = Op(OpType.E)
            fun p() = Op(OpType.P)
            fun permutation(entities: List<List<Op>>) = Op(OpType.Permutation, entities = entities)
        }
    }

    companion object {
        private val RANDOM = SecureRandom()

        fun getDefault(): TelegramTlsHello {
            val grease = ByteArray(8)
            RANDOM.nextBytes(grease)
            for (i in grease.indices) {
                grease[i] = ((grease[i].toInt() and 0xF0) or 0x0A).toByte()
            }
            for (i in 1 until grease.size step 2) {
                if (grease[i] == grease[i - 1]) {
                    grease[i] = (grease[i].toInt() xor 0x10).toByte()
                }
            }
            val ops = listOf(
                Op.string(byteArrayOf(0x16, 0x03, 0x01)),
                Op.beginScope(),
                Op.string(byteArrayOf(0x01, 0x00)),
                Op.beginScope(),
                Op.string(byteArrayOf(0x03, 0x03)),
                Op.zero(32),
                Op.string(byteArrayOf(0x20)),
                Op.random(32),
                Op.string(byteArrayOf(0x00, 0x20)),
                Op.grease(0),
                Op.string(
                    byteArrayOf(
                        0x13, 0x01, 0x13, 0x02, 0x13, 0x03, 0xC0.toByte(), 0x2B, 0xC0.toByte(), 0x2F,
                        0xC0.toByte(), 0x2C, 0xC0.toByte(), 0x30, 0xCC.toByte(), 0xA9.toByte(), 0xCC.toByte(),
                        0xA8.toByte(), 0xC0.toByte(), 0x13, 0xC0.toByte(), 0x14, 0x00, 0x9C.toByte(), 0x00,
                        0x9D.toByte(), 0x00, 0x2F, 0x00, 0x35, 0x01, 0x00
                    )
                ),
                Op.beginScope(),
                Op.grease(2),
                Op.string(byteArrayOf(0x00, 0x00)),
                Op.permutation(
                    listOf(
                        listOf(
                            Op.string(byteArrayOf(0x00, 0x00)),
                            Op.beginScope(),
                            Op.beginScope(),
                            Op.string(byteArrayOf(0x00)),
                            Op.beginScope(),
                            Op.domain(),
                            Op.endScope(),
                            Op.endScope(),
                            Op.endScope()
                        ),
                        listOf(Op.string(byteArrayOf(0x00, 0x05, 0x00, 0x05, 0x01, 0x00, 0x00, 0x00, 0x00))),
                        listOf(
                            Op.string(byteArrayOf(0x00, 0x0A, 0x00, 0x0C, 0x00, 0x0A)),
                            Op.grease(4),
                            Op.string(byteArrayOf(0x11, 0xEC.toByte(), 0x00, 0x1D, 0x00, 0x17, 0x00, 0x18))
                        ),
                        listOf(Op.string(byteArrayOf(0x00, 0x0B, 0x00, 0x02, 0x01, 0x00))),
                        listOf(
                            Op.string(
                                byteArrayOf(
                                    0x00, 0x0D, 0x00, 0x12, 0x00, 0x10, 0x04, 0x03, 0x08, 0x04, 0x04, 0x01,
                                    0x05, 0x03, 0x08, 0x05, 0x05, 0x01, 0x08, 0x06, 0x06, 0x01
                                )
                            )
                        ),
                        listOf(
                            Op.string(
                                byteArrayOf(
                                    0x00, 0x10, 0x00, 0x0E, 0x00, 0x0C, 0x02, 0x68, 0x32, 0x08, 0x68, 0x74,
                                    0x74, 0x70, 0x2F, 0x31, 0x2E, 0x31
                                )
                            )
                        ),
                        listOf(Op.string(byteArrayOf(0x00, 0x12, 0x00, 0x00))),
                        listOf(Op.string(byteArrayOf(0x00, 0x17, 0x00, 0x00))),
                        listOf(Op.string(byteArrayOf(0x00, 0x1B, 0x00, 0x03, 0x02, 0x00, 0x02))),
                        listOf(Op.string(byteArrayOf(0x00, 0x23, 0x00, 0x00))),
                        listOf(
                            Op.string(byteArrayOf(0x00, 0x2B, 0x00, 0x07, 0x06)),
                            Op.grease(6),
                            Op.string(byteArrayOf(0x03, 0x04, 0x03, 0x03))
                        ),
                        listOf(Op.string(byteArrayOf(0x00, 0x2D, 0x00, 0x02, 0x01, 0x01))),
                        listOf(
                            Op.string(byteArrayOf(0x00, 0x33, 0x04, 0xEF.toByte(), 0x04, 0xED.toByte())),
                            Op.grease(4),
                            Op.string(byteArrayOf(0x00, 0x01, 0x00, 0x11, 0xEC.toByte(), 0x04, 0xC0.toByte())),
                            Op.m(),
                            Op.k(),
                            Op.string(byteArrayOf(0x00, 0x1D, 0x00, 0x20)),
                            Op.k()
                        ),
                        listOf(Op.string(byteArrayOf(0x44, 0xCD.toByte(), 0x00, 0x05, 0x00, 0x03, 0x02, 0x68, 0x32))),
                        listOf(
                            Op.string(byteArrayOf(0xFE.toByte(), 0x0D)),
                            Op.beginScope(),
                            Op.string(byteArrayOf(0x00, 0x00, 0x01, 0x00, 0x01)),
                            Op.random(1),
                            Op.string(byteArrayOf(0x00, 0x20)),
                            Op.random(32),
                            Op.beginScope(),
                            Op.e(),
                            Op.endScope(),
                            Op.endScope()
                        ),
                        listOf(Op.string(byteArrayOf(0xFF.toByte(), 0x01, 0x00, 0x01, 0x00)))
                    )
                ),
                Op.grease(3),
                Op.string(byteArrayOf(0x00, 0x01, 0x00)),
                Op.p(),
                Op.endScope(),
                Op.endScope(),
                Op.endScope()
            )
            return TelegramTlsHello(ops, grease, "")
        }

        /** Returns (client hello packet, HMAC workspace for response verification). */
        fun buildWithHmac(secret: ByteArray, domain: String, unixTime: Int): Pair<ByteArray, ByteArray>? {
            val buffer = ByteArray(65 * 1024)
            val hello = getDefault()
            hello.setDomain(domain)
            val size = hello.writeToBuffer(buffer)
            if (size <= 0 || size > 64 * 1024) return null
            val packet = buffer.copyOf(size)
            val workspace = MtCrypto.hmacSha256(secret, packet)
            val hash = workspace.copyOf()
            val view = ByteBuffer.wrap(hash).order(ByteOrder.LITTLE_ENDIAN)
            view.putInt(28, view.getInt(28) xor unixTime)
            System.arraycopy(hash, 0, packet, 11, 32)
            return packet to workspace
        }

        private fun generateMlKem768(key: ByteArray, offset: Int) {
            val q = 3329
            val n = 384
            val values = IntArray(n * 2)
            val raw = ByteArray(n * 2 * 4)
            RANDOM.nextBytes(raw)
            val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until n) {
                values[i * 2] = (bb.int.toLong() and 0xFFFFFFFFL).toInt() % q
                values[i * 2 + 1] = (bb.int.toLong() and 0xFFFFFFFFL).toInt() % q
            }
            for (i in 0 until n) {
                val a = values[i * 2]
                val b = values[i * 2 + 1]
                key[offset + i * 3] = (a and 0xFF).toByte()
                key[offset + i * 3 + 1] = ((a shr 8) or ((b and 0x0F) shl 4)).toByte()
                key[offset + i * 3 + 2] = (b shr 4).toByte()
            }
            val tail = ByteArray(32)
            RANDOM.nextBytes(tail)
            System.arraycopy(tail, 0, key, offset + 1152, 32)
        }

        private fun generatePublicKey(key: ByteArray, offset: Int) {
            val mod = BigInteger(
                "7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFED",
                16
            )
            val pow = BigInteger(
                "3FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF6",
                16
            )
            while (true) {
                val rnd = ByteArray(32)
                RANDOM.nextBytes(rnd)
                System.arraycopy(rnd, 0, key, offset, 32)
                key[offset + 31] = (key[offset + 31].toInt() and 0x7F).toByte()
                var x = BigInteger(1, key.copyOfRange(offset, offset + 32).reversedArray())
                if (x >= mod) continue
                var y = x.modPow(BigInteger.valueOf(3), mod)
                y = y.add(x.multiply(BigInteger.valueOf(486662))).mod(mod)
                y = y.add(BigInteger.ONE).mod(mod)
                y = y.multiply(x).mod(mod)
                if (y.modPow(pow, mod) != BigInteger.ONE) continue
                repeat(3) {
                    val num = x.pow(2).subtract(BigInteger.ONE).mod(mod).pow(2)
                    val den = y.multiply(BigInteger.valueOf(4)).mod(mod)
                    x = num.multiply(den.modInverse(mod)).mod(mod)
                }
                val out = x.toByteArray()
                val slice = ByteArray(32)
                val copyLen = minOf(32, out.size)
                System.arraycopy(out, maxOf(0, out.size - 32), slice, 32 - copyLen, copyLen)
                for (i in 0 until 16) {
                    val t = slice[i]
                    slice[i] = slice[31 - i]
                    slice[31 - i] = t
                }
                System.arraycopy(slice, 0, key, offset, 32)
                return
            }
        }
    }
}
