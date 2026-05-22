package com.proxypulse.data.health.mtproxy

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

/** Incremental AES-CTR matching OpenSSL AES_ctr128_encrypt / tgnet Connection. */
internal class AesCtrState(key: ByteArray, iv: ByteArray) {
    private val keySpec = SecretKeySpec(key, "AES")
    private var counter = iv.copyOf()
    private var keystream = ByteArray(0)
    private var keystreamPos = 16

    /** Advance CTR keystream without XOR (after 64-byte MTProxy init). */
    fun advance(bytes: Int) {
        if (bytes > 0) process(ByteArray(bytes))
    }

    fun process(data: ByteArray): ByteArray {
        val out = ByteArray(data.size)
        var offset = 0
        while (offset < data.size) {
            if (keystreamPos >= 16) {
                keystream = encryptBlock(counter)
                incrementCounter()
                keystreamPos = 0
            }
            val n = min(16 - keystreamPos, data.size - offset)
            for (i in 0 until n) {
                out[offset + i] =
                    (data[offset + i].toInt() xor keystream[keystreamPos + i].toInt()).toByte()
            }
            keystreamPos += n
            offset += n
        }
        return out
    }

    private fun encryptBlock(block: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        return cipher.doFinal(block)
    }

    private fun incrementCounter() {
        for (i in 15 downTo 0) {
            counter[i] = (counter[i] + 1).toByte()
            if (counter[i] != 0.toByte()) break
        }
    }
}
