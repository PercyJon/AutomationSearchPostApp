package com.example.douyinautomation.automation

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class PrivateMessageEntryRuleRepositoryTest {

    @Test
    fun `remote rules are cached and failed refresh falls back to cache`() = runBlocking {
        val cache = FakeCache()
        var shouldFail = false
        val repository = CachedPrivateMessageEntryRuleRepository(
            remote = PrivateMessageEntryRuleRemoteSource {
                if (shouldFail) error("offline")
                PrivateMessageEntryRuleCatalog(
                    version = "remote-1",
                    blockedTerms = listOf("商城"),
                    selectorAllowedTerms = listOf("发私信"),
                    iconAllowedTerms = listOf("发私信", "im_"),
                )
            },
            cache = cache,
            nowMillis = { 1_000L },
        )

        val remote = repository.load(forceRefresh = true)
        assertEquals(PrivateMessageEntryRuleCatalog.Source.REMOTE, remote.source)
        assertEquals("remote-1", cache.value?.version)

        shouldFail = true
        val cached = repository.load(forceRefresh = true)
        assertEquals(PrivateMessageEntryRuleCatalog.Source.LOCAL_CACHE, cached.source)
        assertEquals("remote-1", cached.version)
    }

    @Test
    fun `missing remote and cache returns built-in catalog marker`() = runBlocking {
        val repository = CachedPrivateMessageEntryRuleRepository(
            remote = PrivateMessageEntryRuleRemoteSource { error("offline") },
            cache = FakeCache(),
        )

        val fallback = repository.load(forceRefresh = true)

        assertEquals(PrivateMessageEntryRuleCatalog.Source.BUILT_IN, fallback.source)
        assertEquals("built-in-v1", fallback.version)
    }

    private class FakeCache(
        var value: PrivateMessageEntryRuleCatalog? = null,
    ) : PrivateMessageEntryRuleCache {
        override fun read(): PrivateMessageEntryRuleCatalog? = value

        override fun write(catalog: PrivateMessageEntryRuleCatalog) {
            value = catalog
        }
    }
}
