package com.example.douyinautomation.automation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class UserIdentityFingerprintTest {
    @Test
    fun `uses canonical SHA-256 instead of collision-prone string hash code`() {
        assertEquals("Aa".hashCode(), "BB".hashCode())
        assertNotEquals(
            UserIdentityFingerprint.fromStableKey("Aa"),
            UserIdentityFingerprint.fromStableKey("BB"),
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            UserIdentityFingerprint.fromStableKey("ABC"),
        )
    }
}
