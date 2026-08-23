package com.example.douyinautomation.automation

/**
 * Pure matching policy for search-result identities.
 *
 * This object deliberately has no Accessibility, OCR, storage, or navigation dependencies. It
 * centralizes the comparison rules used for ordinary duplicate prevention and remote-resume
 * anchor lookup, so those rules can be verified without driving a page transition.
 */
object UserResultIdentityMatcher {
    fun processedMatchReason(
        identity: UserResultIdentity,
        processedIdentityHashes: Set<Int>,
        processedIdentities: Iterable<UserResultIdentity>,
    ): String? = "checkpoint_hash".takeIf { identity.key.hashCode() in processedIdentityHashes }
        ?: processedIdentities.firstNotNullOfOrNull { previous -> matchReason(previous, identity) }

    /** Rebuilds a comparable identity from a backend checkpoint without retaining new data. */
    fun remoteAnchorIdentity(key: String, savedName: String?): UserResultIdentity {
        val segments = key.split('|')
            .map { it.trim() }
            .filter(String::isNotBlank)
            .map(::normalizeText)
            .filter(String::isNotBlank)
            .distinct()
        val normalizedSavedName = savedName
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.trimStart().startsWith('|') }
            ?.let(::normalizeText)
        val handle = segments.firstOrNull { it.startsWith("handle:") }
            ?.removePrefix("handle:")
            ?.takeIf(String::isNotBlank)
        val metadata = segments
            .filterNot { normalizedSavedName != null && it == normalizedSavedName }
            .filterNot { it.startsWith("handle:") }
            .toSet()
        val visibleTokens = (segments + listOfNotNull(normalizedSavedName)).toSet()
        return UserResultIdentity(
            key = key,
            source = UserResultIdentity.Source.ACCESSIBILITY,
            displayName = normalizedSavedName,
            accountHandle = handle,
            stableMetadata = metadata,
            visibleTokens = visibleTokens,
        )
    }

    fun matchReason(previous: UserResultIdentity, identity: UserResultIdentity): String? = when {
        previous.key == identity.key -> "exact_key"
        !previous.accountHandle.isNullOrBlank() &&
            previous.accountHandle == identity.accountHandle -> "account_handle"
        previous.displayName.isNullOrBlank() &&
            metadataOverlap(previous.stableMetadata, identity.stableMetadata) -> "remote_metadata"
        sameDisplayName(previous.displayName, identity.displayName) &&
            metadataOverlap(previous.stableMetadata, identity.stableMetadata) -> "name_metadata"
        sameDisplayName(previous.displayName, identity.displayName) &&
            visibleMetadataOverlap(previous, identity) -> "name_visible_tokens"
        else -> null
    }

    /**
     * Anchor matching is intentionally more tolerant than ordinary duplicate skipping. It is
     * used only to find a continuation anchor and the caller rejects ambiguous same-name rows.
     */
    fun viewportAnchorMatchReason(
        previous: UserResultIdentity,
        current: UserResultIdentity,
    ): String? = matchReason(previous, current) ?:
        if (sameDisplayName(previous.displayName, current.displayName)) {
            "anchor_display_name"
        } else {
            null
        }

    private fun sameDisplayName(first: String?, second: String?): Boolean {
        if (first.isNullOrBlank() || second.isNullOrBlank()) return false
        val left = normalizeText(first)
        val right = normalizeText(second)
        if (left == right) return true
        // OCR can clip a long name at a viewport edge or miss a single middle character.
        val shorter = minOf(left.length, right.length)
        if (shorter >= 4 && (left.startsWith(right) || right.startsWith(left))) return true
        return normalizedDistance(left, right) <= 1
    }

    private fun normalizedDistance(first: String, second: String): Int {
        val left = normalizeText(first)
        val right = normalizeText(second)
        if (kotlin.math.abs(left.length - right.length) > 3) return 4
        if (left == right) return 0
        var previous = IntArray(right.length + 1) { it }
        for (i in left.indices) {
            val current = IntArray(right.length + 1)
            current[0] = i + 1
            for (j in right.indices) {
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + if (left[i] == right[j]) 0 else 1,
                )
            }
            previous = current
        }
        return previous[right.length]
    }

    private fun metadataOverlap(first: Set<String>, second: Set<String>): Boolean =
        first.any { left -> second.any { right -> left == right || normalizedDistance(left, right) <= 1 } }

    private fun visibleMetadataOverlap(
        previous: UserResultIdentity,
        current: UserResultIdentity,
    ): Boolean {
        val previousTokens = identityMetadataTokens(previous)
        val currentTokens = identityMetadataTokens(current)
        if (previousTokens.isEmpty() || currentTokens.isEmpty()) return false
        return previousTokens.any { left ->
            currentTokens.any { right ->
                left == right ||
                    left.startsWith(right) ||
                    right.startsWith(left) ||
                    normalizedDistance(left, right) <= 1
            }
        }
    }

    private fun identityMetadataTokens(identity: UserResultIdentity): Set<String> {
        val name = identity.displayName?.let(::normalizeText)
        return identity.visibleTokens
            .map(::normalizeText)
            .filter { token ->
                token.length >= 2 &&
                    token != name &&
                    token !in GENERIC_TOKENS &&
                    !token.all(Char::isDigit) &&
                    !token.contains("粉丝") &&
                    !token.contains("获赞")
            }
            .toSet()
    }

    private fun normalizeText(value: String): String = IdentityTextCanonicalizer.normalize(value)

    private val GENERIC_TOKENS = setOf(
        "关注",
        "回关",
        "已关注",
        "互相关注",
        "发私信",
        "背景图片",
        "背景图",
        "用户头像",
        "头像",
        "头像图片",
        "图片",
        "图片背景",
        "背景",
        "默认头像",
        "用户图片",
        "封面",
        "封面图片",
        "视频封面",
        "视频",
        "照片",
        "筛选",
        "按钮",
        "店铺账号",
        "商家认证账号",
        "朋友",
    )
}
