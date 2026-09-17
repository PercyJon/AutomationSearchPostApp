package com.example.douyinautomation.automation

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

/** Platform JSON/HTTP contract checks. Run on an Android runtime, not the JVM android.jar stub. */
class AutomationHttpClientInstrumentedTest {
    @Test
    fun catalogEnvelopeIsDecodedAndBearerHeaderIsApplied() = runBlocking {
        val connection = StubConnection(
            URL("https://api.example.test/api/v1/automation/mobile/search-presets"),
            """
            {"success":true,"data":{"version":7,"updated_at":"2026-08-18 10:00:00","items":[{"id":12,"label":"红木沙发","keyword":"红木沙发","enabled":true,"sort":1}]}}
            """.trimIndent(),
        )
        val client = AutomationHttpClient(
            config = AuthConfig("https://api.example.test", "secret-token", DeviceIdentity.hash("device")),
            connectionFactory = { connection },
        )

        val catalog = client.fetchCatalog()

        assertEquals("7", catalog.version)
        assertEquals("红木沙发", catalog.items.single().keyword)
        assertEquals("Bearer secret-token", connection.requestProperties["Authorization"])
        assertTrue(connection.requestedUrl.endsWith("/api/v1/automation/mobile/search-presets"))
    }

    @Test
    fun privateMessageEntryRulesEnvelopeIsDecodedAndBearerHeaderIsApplied() = runBlocking {
        val connection = StubConnection(
            URL("https://api.example.test/api/v1/automation/mobile/private-message-entry-rules"),
            """
            {"success":true,"data":{"version":"2026-08-24","updated_at":"2026-08-24 10:00:00","blocked_terms":["客服","咨询","购物车","商城"],"selector_allowed_terms":["发私信","私信"],"icon_allowed_terms":["发私信","私信","im_"]}}
            """.trimIndent(),
        )
        val client = AutomationHttpClient(
            config = AuthConfig("https://api.example.test", "secret-token", DeviceIdentity.hash("device")),
            connectionFactory = { connection },
        )

        val catalog = client.fetchRules()

        assertEquals("2026-08-24", catalog.version)
        assertTrue("商城" in catalog.blockedTerms)
        assertEquals(listOf("发私信", "私信"), catalog.selectorAllowedTerms)
        assertEquals("Bearer secret-token", connection.requestProperties["Authorization"])
        assertTrue(connection.requestedUrl.endsWith("/api/v1/automation/mobile/private-message-entry-rules"))
    }

    @Test
    fun mobileLoginCompletesSliderCaptchaBeforePostingCredentials() = runBlocking {
        val captcha = RecordingStubConnection(
            """{"success":true,"data":{"enable":true,"key":"captcha-key-1","img_base":""}}""",
        )
        val slider = RecordingStubConnection(
            """{"success":true,"data":{"captcha_key":"captcha-key-1","verified":true}}""",
        )
        val login = RecordingStubConnection(
            """{"success":true,"data":{"license_id":20,"license_token":"mobile-license","account":{"username":"admin","name":"管理员"},"device_id_hash":"${DeviceIdentity.hash("device")}"}}""",
        )
        val factory: (URL) -> HttpURLConnection = { url ->
            when {
                url.path.endsWith("/system/auth/captcha/get") -> captcha.also { it.requestedUrl = url.toString() }
                url.path.endsWith("/system/auth/captcha/slider/complete") -> slider.also { it.requestedUrl = url.toString() }
                url.path.endsWith("/automation/mobile/login") -> login.also { it.requestedUrl = url.toString() }
                else -> error("unexpected url $url")
            }
        }

        val response = AutomationHttpClient.login(
            endpoint = "https://api.example.test",
            username = "admin",
            password = "secret",
            deviceIdHash = DeviceIdentity.hash("device"),
            connectionFactory = factory,
        )

        assertEquals("admin", response.accountUsername)
        assertEquals("管理员", response.accountName)
        assertEquals("GET", captcha.requestMethod)
        assertEquals("POST", slider.requestMethod)
        assertTrue(slider.requestBody.toString(Charsets.UTF_8).contains("\"captcha_key\":\"captcha-key-1\""))
        val loginBody = login.requestBody.toString(Charsets.UTF_8)
        assertTrue(loginBody.contains("\"username\":\"admin\""))
        assertTrue(loginBody.contains("\"captcha_key\":\"captcha-key-1\""))
        assertTrue(loginBody.contains("\"captcha\":\"captcha-key-1\""))
        assertTrue(login.requestedUrl.endsWith("/api/v1/automation/mobile/login"))
        assertTrue(login.requestProperties["Authorization"] == null)
    }

    private class RecordingStubConnection(
        private val responseBody: String,
    ) : HttpURLConnection(URL("https://api.example.test/")) {
        val requestProperties = linkedMapOf<String, String>()
        val requestBody = ByteArrayOutputStream()
        var requestedUrl: String = url.toString()

        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = 200
        override fun getInputStream() = ByteArrayInputStream(responseBody.toByteArray())
        override fun getOutputStream() = requestBody
        override fun setRequestProperty(key: String, value: String) {
            requestProperties[key] = value
        }
    }

    private class StubConnection(
        url: URL,
        private val responseBody: String,
    ) : HttpURLConnection(url) {
        val requestProperties = linkedMapOf<String, String>()
        val requestBody = ByteArrayOutputStream()
        var requestedUrl: String = url.toString()

        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = 200
        override fun getInputStream() = ByteArrayInputStream(responseBody.toByteArray())
        override fun getOutputStream() = requestBody
        override fun setRequestProperty(key: String, value: String) {
            requestProperties[key] = value
        }
    }
}
