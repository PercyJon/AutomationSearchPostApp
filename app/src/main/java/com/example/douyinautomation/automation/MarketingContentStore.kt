package com.example.douyinautomation.automation

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Local cache plus server pull/push for marketing copy.
 * Pulls always overwrite the cache (server wins). Saves write locally first, then PUT, then
 * replace the profile with the server response.
 */
object MarketingContentStore {
    private const val PREFERENCES_NAME = "marketing_content_cache"
    private const val KEY_BUNDLE = "bundle"

    private val mutex = Mutex()
    @Volatile private var applicationContext: Context? = null
    @Volatile private var cachedBundle: MarketingContentBundle = MarketingContentBundle(
        source = MarketingContentBundle.Source.EMPTY,
    )

    fun initialize(context: Context) {
        if (applicationContext != null) {
            return
        }
        val app = context.applicationContext
        applicationContext = app
        cachedBundle = readPreferences(app) ?: MarketingContentBundle(
            source = MarketingContentBundle.Source.EMPTY,
        )
    }

    fun cached(): MarketingContentBundle = cachedBundle

    fun profile(type: MarketingContentType): MarketingContentProfile = cachedBundle.profile(type)

    suspend fun syncFromServer(): MarketingContentBundle = mutex.withLock {
        val context = applicationContext
        val config = AuthStore.currentConfig()?.takeIf(AuthConfig::isUsable)
        if (context == null || config == null) {
            return cachedBundle
        }
        val remote = AutomationHttpClient(config).fetchMarketingContents()
        val overwritten = remote.copy(
            syncedAtMillis = System.currentTimeMillis(),
            source = MarketingContentBundle.Source.REMOTE,
        )
        persistLocked(context, overwritten)
        overwritten
    }

    suspend fun saveProfile(profile: MarketingContentProfile): Result<MarketingContentProfile> {
        val normalized = profile.normalized()
        mutex.withLock {
            val context = applicationContext
                ?: return Result.failure(IllegalStateException("营销内容存储尚未初始化"))
            val local = cachedBundle.let { bundle ->
                bundle.copy(
                    profiles = bundle.profiles + (normalized.contentType to normalized),
                    source = MarketingContentBundle.Source.LOCAL,
                )
            }
            persistLocked(context, local)
        }
        val config = AuthStore.currentConfig()?.takeIf(AuthConfig::isUsable)
            ?: return Result.success(normalized)
        return runCatching {
            val saved = AutomationHttpClient(config).upsertMarketingContent(normalized)
            mutex.withLock {
                val context = applicationContext ?: return@runCatching saved
                val merged = cachedBundle.copy(
                    profiles = cachedBundle.profiles + (saved.contentType to saved),
                    syncedAtMillis = System.currentTimeMillis(),
                    source = MarketingContentBundle.Source.REMOTE,
                )
                persistLocked(context, merged)
            }
            saved
        }
    }

    private fun persistLocked(context: Context, bundle: MarketingContentBundle) {
        cachedBundle = bundle
        preferences(context).edit()
            .putString(KEY_BUNDLE, MarketingContentCodec.encodeBundle(bundle).toString())
            .apply()
    }

    private fun readPreferences(context: Context): MarketingContentBundle? =
        preferences(context).getString(KEY_BUNDLE, null)?.let(MarketingContentCodec::decodeBundle)

    private fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}
