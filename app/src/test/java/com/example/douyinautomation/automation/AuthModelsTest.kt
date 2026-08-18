package com.example.douyinautomation.automation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthModelsTest {
    @Test
    fun `missing config stays explicitly not configured`() {
        val state = HeartbeatPolicy.evaluate(null, null, 100L)

        assertEquals(LicenseStatus.NOT_CONFIGURED, state.status)
    }

    @Test
    fun `accepted heartbeat schedules the next check`() {
        val config = AuthConfig("https://api.example.test", "token", "device")
        val state = HeartbeatPolicy.evaluate(
            config = config,
            response = HeartbeatResponse(true, "ok", nextCheckAfterMillis = 10L),
            nowMillis = 100L,
        )

        assertEquals(LicenseStatus.VERIFIED, state.status)
        assertEquals(60_100L, state.nextHeartbeatAtMillis)
    }

    @Test
    fun `invalid endpoint is never considered usable`() {
        assertTrue(!AuthConfig("http://api.example.test", "token", "device").isUsable())
        assertTrue(!AuthConfig("https://api.example.test", "", "device").isUsable())
    }
}
