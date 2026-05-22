package com.proxypulse.data.health.mtproxy

import android.util.Base64
import java.math.BigInteger
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

internal object MtProtoRsa {
    private const val PEM = """
        -----BEGIN RSA PUBLIC KEY-----
        MIIBCgKCAQEAyr+18Rex2ohtVy8sroGPBwXD3DOoKCSpjDqYoXgCqB7ioln4eDCF
        fOBUlfXUEvM/fnKCpF46VkAftlb4VuPDeQSS/ZxZYEGqHaywlroVnXHIjgqoxiAd
        192xRGreuXIaUKmkwlM9JID9WS2jUsTpzQ91L8MEPLJ/4zrBwZua8W5fECwCCh2c
        9G5IzzBm+otMS/YKwmR1olzRCyEkyAEjXWqBI9Ftv5eG8m0VkBzOG655WIYdyV0H
        fDK/NWcvGqa0w/nriMD6mDjKOryamw0OP9QuYgMN0C9xMW9y8SmP4h92OAWodTYg
        Y1hZCxdv6cs5UnW9+PWvS+WIbkh+GaWYxwIDAQAB
        -----END RSA PUBLIC KEY-----
    """

    val fingerprint: ULong = 0x85FD64DE851D9DD0uL

    fun matchesFingerprint(value: Long): Boolean = value.toULong() == fingerprint

    private val publicKey by lazy {
        val b64 = PEM.lines()
            .filter { !it.startsWith("-----") }
            .joinToString("")
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(bytes))
    }

    fun encrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return cipher.doFinal(data)
    }
}
