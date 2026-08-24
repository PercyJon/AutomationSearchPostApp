package com.example.douyinautomation.automation

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** HTTPS/client boundary for the private-message entry safety-rule endpoint. */
fun interface PrivateMessageEntryRuleRemoteSource {
    suspend fun fetchRules(): PrivateMessageEntryRuleCatalog
}

interface PrivateMessageEntryRuleCache {
    fun read(): PrivateMessageEntryRuleCatalog?
    fun write(catalog: PrivateMessageEntryRuleCatalog)
}

/** Remote-first repository; network failures retain the last safe cache or built-in baseline. */
class CachedPrivateMessageEntryRuleRepository(
    private val remote: PrivateMessageEntryRuleRemoteSource,
    private val cache: PrivateMessageEntryRuleCache,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val maxCacheAgeMillis: Long = 24 * 60 * 60 * 1_000L,
) {
    suspend fun load(forceRefresh: Boolean = false): PrivateMessageEntryRuleCatalog {
        val cached = cache.read()
        if (!forceRefresh && cached != null && isFresh(cached)) {
            return cached.copy(source = PrivateMessageEntryRuleCatalog.Source.LOCAL_CACHE)
        }
        return runCatching {
            remote.fetchRules().sanitized().copy(
                updatedAtMillis = nowMillis(),
                source = PrivateMessageEntryRuleCatalog.Source.REMOTE,
            ).also(cache::write)
        }.getOrElse {
            cached?.copy(source = PrivateMessageEntryRuleCatalog.Source.LOCAL_CACHE)
                ?: PrivateMessageEntryRuleCatalog(
                    version = "built-in-v1",
                    blockedTerms = emptyList(),
                    selectorAllowedTerms = emptyList(),
                    iconAllowedTerms = emptyList(),
                    source = PrivateMessageEntryRuleCatalog.Source.BUILT_IN,
                )
        }
    }

    private fun isFresh(catalog: PrivateMessageEntryRuleCatalog): Boolean =
        catalog.updatedAtMillis?.let { nowMillis() - it <= maxCacheAgeMillis } == true

    private fun PrivateMessageEntryRuleCatalog.sanitized(): PrivateMessageEntryRuleCatalog = copy(
        version = version.trim().take(MAX_VERSION_LENGTH),
        blockedTerms = sanitizeTerms(blockedTerms),
        selectorAllowedTerms = sanitizeTerms(selectorAllowedTerms),
        iconAllowedTerms = sanitizeTerms(iconAllowedTerms),
    )

    private fun sanitizeTerms(values: List<String>): List<String> = values.asSequence()
        .map(String::trim)
        .filter { it.length in MIN_TERM_LENGTH..MAX_TERM_LENGTH }
        .distinct()
        .take(MAX_TERMS_PER_LIST)
        .toList()

    private companion object {
        const val MAX_VERSION_LENGTH = 64
        const val MIN_TERM_LENGTH = 2
        const val MAX_TERM_LENGTH = 40
        const val MAX_TERMS_PER_LIST = 64
    }
}

/** Android-private JSON cache for the remote safety-rule catalog. */
class SharedPreferencesPrivateMessageEntryRuleCache(
    context: Context,
    preferencesName: String = "private_message_entry_rule_cache",
) : PrivateMessageEntryRuleCache {
    private val preferences: SharedPreferences = context.applicationContext
        .getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    override fun read(): PrivateMessageEntryRuleCatalog? = preferences.getString(KEY, null)
        ?.let(PrivateMessageEntryRuleCatalogCodec::decode)

    override fun write(catalog: PrivateMessageEntryRuleCatalog) {
        preferences.edit().putString(KEY, PrivateMessageEntryRuleCatalogCodec.encode(catalog).toString()).apply()
    }

    private companion object {
        const val KEY = "catalog"
    }
}

object PrivateMessageEntryRuleCatalogCodec {
    fun encode(catalog: PrivateMessageEntryRuleCatalog): JSONObject = JSONObject().apply {
        put("version", catalog.version)
        put("blocked_terms", JSONArray(catalog.blockedTerms))
        put("selector_allowed_terms", JSONArray(catalog.selectorAllowedTerms))
        put("icon_allowed_terms", JSONArray(catalog.iconAllowedTerms))
        put("updated_at", catalog.updatedAtMillis ?: JSONObject.NULL)
        put("source", catalog.source.name)
    }

    fun decode(raw: String): PrivateMessageEntryRuleCatalog? = runCatching {
        val json = JSONObject(raw)
        PrivateMessageEntryRuleCatalog(
            version = json.optString("version"),
            blockedTerms = json.optJSONArray("blocked_terms").toStringList(),
            selectorAllowedTerms = json.optJSONArray("selector_allowed_terms").toStringList(),
            iconAllowedTerms = json.optJSONArray("icon_allowed_terms").toStringList(),
            updatedAtMillis = if (json.isNull("updated_at")) null else json.optLong("updated_at"),
            source = json.optString("source").let { source ->
                runCatching { PrivateMessageEntryRuleCatalog.Source.valueOf(source) }
                    .getOrDefault(PrivateMessageEntryRuleCatalog.Source.LOCAL_CACHE)
            },
        )
    }.getOrNull()

    private fun JSONArray?.toStringList(): List<String> = if (this == null) emptyList() else buildList(length()) {
        for (index in 0 until length()) {
            optString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
        }
    }
}

/** Process-local immutable rule snapshot used by every private-message entry route. */
object PrivateMessageEntryRuleStore {
    @Volatile private var currentRules = PrivateMessageEntryRulePolicy.defaultRules()
    @Volatile private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val cached = SharedPreferencesPrivateMessageEntryRuleCache(context).read()
            currentRules = PrivateMessageEntryRulePolicy.effective(cached)
            initialized = true
        }
    }

    suspend fun refresh(context: Context): PrivateMessageEntryRulePolicy.EffectiveRules {
        initialize(context)
        val catalog = AuthStore.loadPrivateMessageEntryRuleCatalog(context)
        return PrivateMessageEntryRulePolicy.effective(catalog).also { currentRules = it }
    }

    fun evaluate(route: PrivateMessageEntryRoute, searchableText: String): PrivateMessageEntryRulePolicy.Verdict =
        PrivateMessageEntryRulePolicy.evaluate(route, searchableText, currentRules)

    fun current(): PrivateMessageEntryRulePolicy.EffectiveRules = currentRules
}
