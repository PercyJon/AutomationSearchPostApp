package com.example.douyinautomation.automation

import java.net.URI

/**
 * Login uses the build-injected origin. Cleartext is accepted for loopback
 * (`127.0.0.1` / `localhost`) and the fixed production host `hk.sxjjerp.com`.
 */
object LoginEndpointPolicy {
    const val PRODUCTION_HTTP_HOST = "hk.sxjjerp.com"

    fun normalize(raw: String?): String? {
        val value = raw?.trim()?.trimEnd('/').orEmpty()
        if (value.startsWith("https://") && value.length > "https://".length) return value
        return value.takeIf(::isAllowedHttp)
    }

    fun isLoopbackHttp(raw: String): Boolean {
        val uri = httpUri(raw) ?: return false
        val host = uri.host?.lowercase() ?: return false
        return host == "127.0.0.1" || host == "localhost"
    }

    fun isAllowedHttp(raw: String): Boolean {
        val uri = httpUri(raw) ?: return false
        val host = uri.host?.lowercase() ?: return false
        return host == "127.0.0.1" || host == "localhost" || host == PRODUCTION_HTTP_HOST
    }

    private fun httpUri(raw: String): URI? {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        return uri.takeIf { it.scheme == "http" && !it.host.isNullOrBlank() }
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
