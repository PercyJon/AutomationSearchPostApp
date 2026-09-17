package com.example.douyinautomation.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginEndpointPolicyTest {

    @Test
    fun buildConfigHttpsWinsOverRememberedAndTypedValues() {
        assertEquals(
            "https://managed.example",
            LoginEndpointPolicy.resolvedEndpoint(
                buildConfig = "https://managed.example/",
                remembered = "https://old.example",
                typed = "https://typed.example",
            ),
        )
    }

    @Test
    fun rememberedHttpsUnlocksLoginWhenTheApkHasNoOrigin() {
        assertEquals(
            "https://remembered.example",
            LoginEndpointPolicy.resolvedEndpoint(
                buildConfig = "",
                remembered = "https://remembered.example",
                typed = "",
            ),
        )
        assertFalse(LoginEndpointPolicy.shouldShowEndpointField("https://managed.example"))
        assertTrue(LoginEndpointPolicy.shouldShowEndpointField(""))
    }

    @Test
    fun typedHttpsIsTheRecoveryPathWhenNothingElseIsAvailable() {
        assertEquals(
            "https://typed.example",
            LoginEndpointPolicy.resolvedEndpoint(
                buildConfig = "not-https",
                remembered = null,
                typed = " https://typed.example/ ",
            ),
        )
        assertNull(
            LoginEndpointPolicy.resolvedEndpoint(
                buildConfig = "",
                remembered = "http://insecure.example",
                typed = "",
            ),
        )
    }

    @Test
    fun loopbackHttpIsAcceptedForLocalAdbReverse() {
        assertEquals(
            "http://127.0.0.1:8001",
            LoginEndpointPolicy.normalize("http://127.0.0.1:8001/"),
        )
        assertEquals(
            "http://localhost:8001",
            LoginEndpointPolicy.resolvedEndpoint(
                buildConfig = "http://localhost:8001",
                remembered = "https://remembered.example",
                typed = "",
            ),
        )
        assertNull(LoginEndpointPolicy.normalize("http://127.0.0.1.example"))
        val deviceIdHash = DeviceIdentity.hash("device")
        val restored = AuthConfig("https://remembered.example", "token", deviceIdHash)
        assertEquals(
            "http://127.0.0.1:8001",
            LoginEndpointPolicy.overlayLoopbackOrigin(restored, "http://127.0.0.1:8001")?.endpoint,
        )
        assertEquals(
            restored.endpoint,
            LoginEndpointPolicy.overlayLoopbackOrigin(restored, "https://managed.example")?.endpoint,
        )
        assertFalse(LoginEndpointPolicy.shouldShowEndpointField("http://127.0.0.1:8001"))
    }

    @Test
    fun productionHttpOriginIsAcceptedAndHidesTheEndpointField() {
        assertEquals(
            "http://hk.sxjjerp.com:9999",
            LoginEndpointPolicy.normalize("http://hk.sxjjerp.com:9999/"),
        )
        assertEquals(
            "http://hk.sxjjerp.com:9999",
            LoginEndpointPolicy.resolvedEndpoint(
                buildConfig = "http://hk.sxjjerp.com:9999/",
                remembered = "https://remembered.example",
                typed = "https://typed.example",
            ),
        )
        assertFalse(LoginEndpointPolicy.shouldShowEndpointField("http://hk.sxjjerp.com:9999/"))
        val restored = AuthConfig(
            "https://remembered.example",
            "token",
            DeviceIdentity.hash("device"),
        )
        assertEquals(
            restored.endpoint,
            LoginEndpointPolicy.overlayLoopbackOrigin(
                restored,
                "http://hk.sxjjerp.com:9999",
            )?.endpoint,
        )
    }
}
