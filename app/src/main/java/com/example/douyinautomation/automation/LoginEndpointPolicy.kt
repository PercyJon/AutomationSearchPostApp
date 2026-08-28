package com.example.douyinautomation.automation

import java.net.URI

/**
 * Login uses a build-injected HTTPS origin when present. Otherwise a previously successful
 * origin, then a recovery field the operator can fill, can unlock the same account/password form.
 *
 * Cleartext is only accepted for loopback (`127.0.0.1` / `localhost`) so a USB `adb reverse`
 * session can hit the developer machine without opening HTTP to the public internet.
 */
object LoginEndpointPolicy {
    fun normalize(raw: String?): String? {
        val value = raw?.trim()?.trimEnd('/').orEmpty()
        if (value.startsWith("https://") && value.length > "https://".length) return value
        return value.takeIf(::isLoopbackHttp)
    }

    fun isLoopbackHttp(raw: String): Boolean {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return false
        if (uri.scheme != "http") return false
        val host = uri.host?.lowercase() ?: return false
        return host == "127.0.0.1" || host == "localhost"
    }

    fun resolvedEndpoint(
        buildConfig: String,
        remembered: String?,
        typed: String?,
    ): String? = normalize(buildConfig) ?: normalize(remembered) ?: normalize(typed)

    fun shouldShowEndpointField(buildConfig: String): Boolean = normalize(buildConfig) == null

    /** Debug APKs injected with loopback reuse the saved license against the local backend. */
    fun overlayLoopbackOrigin(config: AuthConfig?, buildConfig: String): AuthConfig? {
        val loopback = normalize(buildConfig)?.takeIf(::isLoopbackHttp) ?: return config
        return config?.copy(endpoint = loopback)
    }
}
