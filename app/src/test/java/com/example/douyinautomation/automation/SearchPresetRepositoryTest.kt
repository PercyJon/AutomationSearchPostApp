package com.example.douyinautomation.automation

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchPresetRepositoryTest {
    @Test
    fun `fresh cache is used without calling remote`() = runBlocking {
        val cache = FakeCache(catalog("cache", 900L))
        var calls = 0
        val repository = CachedSearchPresetRepository(
            remote = SearchPresetRemoteSource { calls++; catalog("remote", 1_000L) },
            cache = cache,
            nowMillis = { 1_000L },
        )

        val loaded = repository.load()

        assertEquals("cache", loaded.version)
        assertEquals(SearchPresetCatalog.Source.LOCAL_CACHE, loaded.source)
        assertEquals(0, calls)
    }

    @Test
    fun `remote refresh is cached and invalid remote falls back`() = runBlocking {
        val cache = FakeCache(null)
        var fail = false
        val repository = CachedSearchPresetRepository(
            remote = SearchPresetRemoteSource {
                if (fail) error("offline")
                catalog("remote", 0L)
            },
            cache = cache,
            nowMillis = { 2_000L },
        )

        val remote = repository.load(forceRefresh = true)
        assertEquals("remote", remote.version)
        assertEquals(SearchPresetCatalog.Source.REMOTE, remote.source)
        assertEquals("remote", cache.value?.version)

        fail = true
        val fallback = repository.load(forceRefresh = true)
        assertEquals("remote", fallback.version)
        assertEquals(SearchPresetCatalog.Source.LOCAL_CACHE, fallback.source)
    }

    private fun catalog(version: String, updatedAt: Long) = SearchPresetCatalog(
        version = version,
        items = listOf(SearchPreset("one", "红木沙发", "红木沙发")),
        updatedAtMillis = updatedAt,
        source = SearchPresetCatalog.Source.REMOTE,
    )

    private class FakeCache(var value: SearchPresetCatalog?) : SearchPresetCache {
        override fun read(): SearchPresetCatalog? = value
        override fun write(catalog: SearchPresetCatalog) { value = catalog }
    }
}
