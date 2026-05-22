package com.proxypulse.data.health.mtproxy

internal object MtProtoMessageId {
    private var seq = 0

    @Synchronized
    fun next(): Long {
        val now = System.currentTimeMillis() / 1000
        seq = (seq + 1) and 0xFFFF
        return (now shl 32) or ((seq * 4).toLong())
    }
}
