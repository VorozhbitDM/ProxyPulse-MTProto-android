package com.proxypulse.data.health.mtproxy

import android.util.Base64

/** Same rules as tgnet `ConnectionsManager::decodeSecret`. */
internal object TelegramSecret {
    fun decode(input: String): ByteArray {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ByteArray(0)
        if (trimmed.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } && trimmed.length % 2 == 0) {
            return trimmed.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
        return base64UrlDecode(trimmed)
    }

    private fun base64UrlDecode(value: String): ByteArray {
        var s = value.trimEnd('=')
        if (s.length % 4 == 1) return ByteArray(0)
        val mapped = s.map { c ->
            when (c) {
                '-' -> '+'
                '_' -> '/'
                else -> c
            }
        }.joinToString("")
        return runCatching {
            Base64.decode(mapped, Base64.DEFAULT)
        }.getOrDefault(ByteArray(0))
    }
}

internal data class MtProxySecret(
    val raw: ByteArray,
    val randomPadding: Boolean,
    val fakeTlsDomain: String?
) {
    val isFakeTls: Boolean get() = fakeTlsDomain != null

    companion object {
        fun parse(secretHexOrB64: String): MtProxySecret? = fromDecoded(TelegramSecret.decode(secretHexOrB64))

        fun fromDecoded(bytes: ByteArray): MtProxySecret? {
            if (bytes.isEmpty()) return null
            return when {
                bytes.size == 16 -> MtProxySecret(bytes, randomPadding = false, fakeTlsDomain = null)
                bytes.size == 17 && bytes[0] == 0xDD.toByte() ->
                    MtProxySecret(bytes.copyOfRange(1, 17), randomPadding = true, fakeTlsDomain = null)
                bytes.size == 17 && bytes[0] == 0xEE.toByte() ->
                    MtProxySecret(bytes.copyOfRange(1, 17), randomPadding = true, fakeTlsDomain = null)
                bytes.size >= 18 && bytes[0] == 0xEE.toByte() -> {
                    val domainBytes = bytes.copyOfRange(17, bytes.size)
                    if (domainBytes.isEmpty()) return null
                    MtProxySecret(
                        raw = bytes.copyOfRange(1, 17),
                        randomPadding = true,
                        fakeTlsDomain = domainBytes.toString(Charsets.UTF_8)
                    )
                }
                else -> null
            }
        }
    }
}
