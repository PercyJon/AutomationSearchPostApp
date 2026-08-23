package com.example.douyinautomation.automation

import java.security.MessageDigest

/**
 * Opaque durable identity reference for task deduplication and app-private audit correlation.
 *
 * The stable row key itself stays in memory or in the existing private record field. Checkpoints
 * retain only this SHA-256 value, so a 32-bit [String.hashCode] collision cannot merge users.
 */
object UserIdentityFingerprint {
    fun fromStableKey(stableKey: String): String {
        val canonical = IdentityTextCanonicalizer.normalize(stableKey)
        require(canonical.isNotBlank()) { "A stable identity key is required" }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    /** A diagnostic-safe identifier that is short enough to avoid retaining a full fingerprint. */
    fun logPrefix(fingerprint: String?): String? = fingerprint?.take(LOG_PREFIX_LENGTH)

    private const val LOG_PREFIX_LENGTH = 12
}
