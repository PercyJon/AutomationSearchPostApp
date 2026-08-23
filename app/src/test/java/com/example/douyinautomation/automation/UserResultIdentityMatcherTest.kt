package com.example.douyinautomation.automation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserResultIdentityMatcherTest {
    @Test
    fun `checkpoint fingerprint does not merge two keys with the same legacy hash`() {
        val first = identity(key = "Aa")
        val second = identity(key = "BB")

        assertEquals(first.key.hashCode(), second.key.hashCode())
        assertEquals(
            "checkpoint_fingerprint",
            UserResultIdentityMatcher.processedMatchReason(
                identity = first,
                processedIdentityFingerprints = setOf(first.fingerprint),
                legacyProcessedIdentityHashes = emptySet(),
                processedIdentities = emptyList(),
            ),
        )
        assertNull(
            UserResultIdentityMatcher.processedMatchReason(
                identity = second,
                processedIdentityFingerprints = setOf(first.fingerprint),
                legacyProcessedIdentityHashes = emptySet(),
                processedIdentities = emptyList(),
            ),
        )
    }

    @Test
    fun `legacy checkpoint hash remains a read-only compatibility match`() {
        val identity = identity(key = "legacy-account")

        assertEquals(
            "legacy_checkpoint_hash",
            UserResultIdentityMatcher.processedMatchReason(
                identity = identity,
                processedIdentityFingerprints = emptySet(),
                legacyProcessedIdentityHashes = setOf(identity.key.hashCode()),
                processedIdentities = emptyList(),
            ),
        )
    }

    @Test
    fun `same account handle remains an ordinary duplicate match`() {
        val previous = identity(key = "old", handle = "designer_01")
        val current = identity(key = "new", handle = "designer_01")

        assertEquals("account_handle", UserResultIdentityMatcher.matchReason(previous, current))
    }

    @Test
    fun `anchor accepts an OCR clipped display name only when caller can prove uniqueness`() {
        val previous = identity(key = "previous", name = "杭州设计工作室")
        val current = identity(key = "current", name = "杭州设计工...")

        assertEquals(
            "anchor_display_name",
            UserResultIdentityMatcher.viewportAnchorMatchReason(previous, current),
        )
        assertNull(UserResultIdentityMatcher.matchReason(previous, current))
    }

    @Test
    fun `remote fallback key rebuilds metadata for a later identity match`() {
        val anchor = UserResultIdentityMatcher.remoteAnchorIdentity(
            key = "|杭州设计工作室|品牌服务",
            savedName = null,
        )
        val current = identity(
            key = "current",
            name = "杭州设计工作室",
            stableMetadata = setOf("品牌服务"),
        )

        assertEquals("remote_metadata", UserResultIdentityMatcher.matchReason(anchor, current))
    }

    @Test
    fun `generic visible labels cannot create a same-name duplicate match`() {
        val previous = identity(key = "previous", name = "设计工作室", visibleTokens = setOf("关注"))
        val current = identity(key = "current", name = "设计工作室", visibleTokens = setOf("关注"))

        assertNull(UserResultIdentityMatcher.matchReason(previous, current))
    }

    private fun identity(
        key: String,
        name: String? = null,
        handle: String? = null,
        stableMetadata: Set<String> = emptySet(),
        visibleTokens: Set<String> = emptySet(),
    ) = UserResultIdentity(
        key = key,
        source = UserResultIdentity.Source.ACCESSIBILITY,
        displayName = name,
        accountHandle = handle,
        stableMetadata = stableMetadata,
        visibleTokens = visibleTokens,
    )
}
