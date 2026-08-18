package com.example.douyinautomation.automation

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Network/client boundary for the future preset endpoint. The app does not hard-code HTTP here. */
fun interface SearchPresetRemoteSource {
    suspend fun fetchCatalog(): SearchPresetCatalog
}

interface SearchPresetCache {
    fun read(): SearchPresetCatalog?
    fun write(catalog: SearchPresetCatalog)
}

/**
 * Remote-first repository with a bounded local fallback. A stale cache is still usable when the
 * endpoint is unavailable, but its source is marked LOCAL_CACHE so the UI can explain provenance.
 */
class CachedSearchPresetRepository(
    private val remote: SearchPresetRemoteSource,
    private val cache: SearchPresetCache,
    private val fallback: SearchPresetRepository = LocalSearchPresetRepository(),
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val maxCacheAgeMillis: Long = 24 * 60 * 60 * 1_000L,
) : SearchPresetRepository {
    override suspend fun load(forceRefresh: Boolean): SearchPresetCatalog {
        val cached = cache.read()?.takeIf { it.items.isNotEmpty() }
        if (!forceRefresh && cached != null && nowMillis() - cached.updatedAtMillis <= maxCacheAgeMillis) {
            return cached.copy(source = SearchPresetCatalog.Source.LOCAL_CACHE)
        }
        return runCatching {
            val remoteCatalog = remote.fetchCatalog().validated()
            val normalized = remoteCatalog.copy(
                updatedAtMillis = nowMillis(),
                source = SearchPresetCatalog.Source.REMOTE,
            )
            cache.write(normalized)
            normalized
        }.getOrElse {
            cached?.copy(source = SearchPresetCatalog.Source.LOCAL_CACHE)
                ?: fallback.load(forceRefresh = false).copy(source = SearchPresetCatalog.Source.BUILT_IN)
        }
    }

    private fun SearchPresetCatalog.validated(): SearchPresetCatalog {
        val validItems = items
            .filter { it.id.isNotBlank() && it.label.isNotBlank() && it.keyword.isNotBlank() }
            .distinctBy { it.id }
        require(validItems.isNotEmpty()) { "Preset endpoint returned no usable items" }
        return copy(items = validItems)
    }
}

/** Android-private JSON cache; no raw user-result or OCR content is stored here. */
class SharedPreferencesSearchPresetCache(
    context: Context,
    private val preferencesName: String = "search_preset_cache",
) : SearchPresetCache {
    private val preferences: SharedPreferences = context.applicationContext
        .getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    override fun read(): SearchPresetCatalog? = preferences.getString(KEY, null)
        ?.let(SearchPresetCatalogCodec::decode)

    override fun write(catalog: SearchPresetCatalog) {
        preferences.edit().putString(KEY, SearchPresetCatalogCodec.encode(catalog).toString()).apply()
    }

    private companion object {
        const val KEY = "catalog"
    }
}

object SearchPresetCatalogCodec {
    fun encode(catalog: SearchPresetCatalog): JSONObject = JSONObject().apply {
        put("version", catalog.version)
        put("updated_at", catalog.updatedAtMillis)
        put("source", catalog.source.name)
        put("items", JSONArray(catalog.items.map { item ->
            JSONObject().apply {
                put("id", item.id)
                put("label", item.label)
                put("keyword", item.keyword)
                put("enabled", item.enabled)
            }
        }))
    }

    fun decode(raw: String): SearchPresetCatalog? = runCatching {
        val json = JSONObject(raw)
        val itemsJson = json.optJSONArray("items") ?: return null
        val items = buildList(itemsJson.length()) {
            for (index in 0 until itemsJson.length()) {
                val item = itemsJson.getJSONObject(index)
                add(
                    SearchPreset(
                        id = item.getString("id"),
                        label = item.getString("label"),
                        keyword = item.getString("keyword"),
                        enabled = item.optBoolean("enabled", true),
                    ),
                )
            }
        }
        SearchPresetCatalog(
            version = json.getString("version"),
            items = items,
            updatedAtMillis = json.getLong("updated_at"),
            source = json.optString("source").let { value ->
                runCatching { SearchPresetCatalog.Source.valueOf(value) }
                    .getOrDefault(SearchPresetCatalog.Source.LOCAL_CACHE)
            },
        )
    }.getOrNull()
}
