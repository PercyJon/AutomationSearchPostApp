package com.example.douyinautomation.automation

/**
 * Login uses a build-injected HTTPS origin when present. Otherwise a previously successful
 * origin, then a recovery field the operator can fill, can unlock the same account/password form.
 */
object LoginEndpointPolicy {
    fun normalize(raw: String?): String? {
        val value = raw?.trim()?.trimEnd('/').orEmpty()
        return value.takeIf { it.startsWith("https://") && value.length > "https://".length }
    }

    fun resolvedEndpoint(
        buildConfig: String,
        remembered: String?,
        typed: String?,
    ): String? = normalize(buildConfig) ?: normalize(remembered) ?: normalize(typed)

    fun shouldShowEndpointField(buildConfig: String): Boolean = normalize(buildConfig) == null
}
