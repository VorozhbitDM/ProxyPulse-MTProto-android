package com.proxypulse.data.health.mtproxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MtProxySecretTest {
    @Test
    fun parse_plain16() {
        val s = MtProxySecret.parse("0123456789abcdef0123456789abcdef")!!
        assertEquals(16, s.raw.size)
        assertNull(s.fakeTlsDomain)
    }

    @Test
    fun parse_ddPadding() {
        val s = MtProxySecret.parse("dd0123456789abcdef0123456789abcdef")!!
        assertTrue(s.randomPadding)
        assertNull(s.fakeTlsDomain)
    }

    @Test
    fun parse_eeFakeTls() {
        val domainHex = "676f6f676c652e636f6d" // google.com
        val s = MtProxySecret.parse(
            "ee0123456789abcdef0123456789abcdef$domainHex"
        )!!
        assertNotNull(s.fakeTlsDomain)
        assertEquals("google.com", s.fakeTlsDomain)
        assertTrue(s.randomPadding)
    }

    @Test
    fun parse_ee16BytesWithoutDomain() {
        val hex = "ee" + "0123456789abcdef0123456789abcdef"
        val s = MtProxySecret.parse(hex)!!
        assertEquals(16, s.raw.size)
        assertNull(s.fakeTlsDomain)
        assertTrue(s.randomPadding)
    }

    @Test
    fun parse_invalid() {
        assertNull(MtProxySecret.parse(""))
        assertNull(MtProxySecret.parse("abcd"))
    }
}
