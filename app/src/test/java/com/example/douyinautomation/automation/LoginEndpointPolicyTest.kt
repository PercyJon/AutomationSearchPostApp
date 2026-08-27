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
}
