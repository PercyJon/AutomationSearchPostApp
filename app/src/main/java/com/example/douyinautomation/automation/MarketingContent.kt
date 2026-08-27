package com.example.douyinautomation.automation

import kotlin.random.Random
import org.json.JSONArray
import org.json.JSONObject

/** Per-account marketing copy, keyed by the two private-message task families. */
enum class MarketingContentType(val wireName: String) {
    BIZ_DM("biz_dm"),
    COMMENT_DM("comment_dm"),
    ;

    companion object {
        fun fromWire(value: String?): MarketingContentType? =
            entries.firstOrNull { it.wireName.equals(value?.trim(), ignoreCase = true) }

        fun forTaskType(taskType: AutomationTaskType): MarketingContentType = when (taskType) {
            AutomationTaskType.PROFILE_PRIVATE_MESSAGE -> BIZ_DM
            AutomationTaskType.COMMENT_PRIVATE_MESSAGE -> COMMENT_DM
        }
    }
}

data class MarketingContentPick(
    val index: Int?,
    val text: String?,
    val reason: String,
) {
    val isEmpty: Boolean get() = text.isNullOrBlank()
}

data class MarketingContentProfile(
    val contentType: MarketingContentType,
    val slots: List<String> = MarketingContentResolver.emptySlots(),
    val selectedIndex: Int = 0,
    val randomEnabled: Boolean = false,
    val remoteId: Long? = null,
    val version: Int = 0,
    val updatedAtMillis: Long? = null,
) {
    fun normalized(): MarketingContentProfile {
        val normalizedSlots = MarketingContentResolver.normalizeSlots(slots)
        return copy(
            slots = normalizedSlots,
            selectedIndex = selectedIndex.coerceIn(0, MarketingContentResolver.SLOT_COUNT - 1),
        )
    }

    fun filledCount(): Int = slots.count { it.isNotBlank() }
}

data class MarketingContentBundle(
    val profiles: Map<MarketingContentType, MarketingContentProfile> =
        MarketingContentType.entries.associateWith { MarketingContentProfile(it) },
    val syncedAtMillis: Long? = null,
    val source: Source = Source.LOCAL,
) {
    enum class Source {
        REMOTE,
        LOCAL,
        EMPTY,
    }

    fun profile(type: MarketingContentType): MarketingContentProfile =
        profiles[type]?.normalized() ?: MarketingContentProfile(type)
}

/**
 * Frozen copy attached to a running task. Later editor/admin changes must not mutate this.
 * Phase A-D stores it only; the send path still uses the blank-message probe.
 */
data class FrozenMarketingContent(
    val contentType: MarketingContentType,
    val slots: List<String>,
    val selectedIndex: Int,
    val randomEnabled: Boolean,
    val resolvedIndex: Int?,
    val resolvedText: String?,
    val reason: String,
) {
    fun toLogAttributes(): Map<String, Any?> = mapOf(
        "content_type" to contentType.wireName,
        "selected_index" to selectedIndex,
        "random" to randomEnabled,
        "resolved_index" to (resolvedIndex ?: -1),
        "has_text" to !resolvedText.isNullOrBlank(),
        "reason" to reason,
        "filled_count" to slots.count { it.isNotBlank() },
    )
}

object MarketingContentResolver {
    const val SLOT_COUNT = 5
    const val MAX_SLOT_CHARS = 1_000

    fun emptySlots(): List<String> = List(SLOT_COUNT) { "" }

    fun normalizeSlots(slots: List<String>?): List<String> {
        val trimmed = (slots ?: emptyList()).map { value ->
            value.trim().take(MAX_SLOT_CHARS)
        }
        return (trimmed + emptySlots()).take(SLOT_COUNT)
    }

    fun resolve(
        profile: MarketingContentProfile,
        random: Random = Random.Default,
    ): MarketingContentPick {
        val normalized = profile.normalized()
        val filled = normalized.slots.mapIndexedNotNull { index, text ->
            text.takeIf { it.isNotBlank() }?.let { index to it }
        }
        if (filled.isEmpty()) {
            return MarketingContentPick(index = null, text = null, reason = "empty")
        }
        if (normalized.randomEnabled && filled.size >= 2) {
            val pick = filled[random.nextInt(filled.size)]
            return MarketingContentPick(index = pick.first, text = pick.second, reason = "random")
        }
        val selected = normalized.slots.getOrNull(normalized.selectedIndex).orEmpty()
        if (selected.isNotBlank()) {
            val reason = if (normalized.randomEnabled) "selected_insufficient_for_random" else "selected"
            return MarketingContentPick(
                index = normalized.selectedIndex,
                text = selected,
                reason = reason,
            )
        }
        val fallback = filled.first()
        return MarketingContentPick(
            index = fallback.first,
            text = fallback.second,
            reason = "fallback_first_non_blank",
        )
    }

    fun freeze(
        profile: MarketingContentProfile,
        random: Random = Random.Default,
    ): FrozenMarketingContent {
        val normalized = profile.normalized()
        val pick = resolve(normalized, random)
        return FrozenMarketingContent(
            contentType = normalized.contentType,
            slots = normalized.slots,
            selectedIndex = normalized.selectedIndex,
            randomEnabled = normalized.randomEnabled,
            resolvedIndex = pick.index,
            resolvedText = pick.text,
            reason = pick.reason,
        )
    }

    fun freezeForTask(
        taskType: AutomationTaskType,
        bundle: MarketingContentBundle = MarketingContentStore.cached(),
        random: Random = Random.Default,
    ): FrozenMarketingContent = freeze(bundle.profile(MarketingContentType.forTaskType(taskType)), random)
}

object MarketingContentCodec {
    fun encodeProfile(profile: MarketingContentProfile): JSONObject = JSONObject().apply {
        val normalized = profile.normalized()
        put("content_type", normalized.contentType.wireName)
        put("slots", JSONArray(normalized.slots))
        put("selected_index", normalized.selectedIndex)
        put("random_enabled", normalized.randomEnabled)
        put("id", normalized.remoteId ?: JSONObject.NULL)
        put("version", normalized.version)
        put("updated_at", normalized.updatedAtMillis ?: JSONObject.NULL)
    }

    fun encodeBundle(bundle: MarketingContentBundle): JSONObject = JSONObject().apply {
        put(
            "items",
            JSONArray(MarketingContentType.entries.map { type -> encodeProfile(bundle.profile(type)) }),
        )
        put("synced_at", bundle.syncedAtMillis ?: JSONObject.NULL)
        put("source", bundle.source.name)
    }

    fun decodeProfile(raw: JSONObject?, fallbackType: MarketingContentType): MarketingContentProfile {
        if (raw == null) return MarketingContentProfile(fallbackType)
        val type = MarketingContentType.fromWire(raw.optString("content_type")) ?: fallbackType
        val slots = raw.optJSONArray("slots").toSlotList()
        return MarketingContentProfile(
            contentType = type,
            slots = slots,
            selectedIndex = raw.optInt("selected_index", 0),
            randomEnabled = raw.optBoolean("random_enabled", false),
            remoteId = raw.optLongOrNull("id"),
            version = raw.optInt("version", 0),
            updatedAtMillis = raw.optLongOrNull("updated_at")
                ?: AutomationHttpClient.parseTimestamp(raw.optString("updated_at").takeIf { it.isNotBlank() }),
        ).normalized()
    }

    fun decodeBundle(raw: String?): MarketingContentBundle? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(raw)
            decodeBundle(root)
        }.getOrNull()
    }

    fun decodeBundle(root: JSONObject): MarketingContentBundle {
        val items = root.optJSONArray("items")
        val decoded = mutableMapOf<MarketingContentType, MarketingContentProfile>()
        if (items != null) {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val type = MarketingContentType.fromWire(item.optString("content_type")) ?: continue
                decoded[type] = decodeProfile(item, type)
            }
        }
        val profiles = MarketingContentType.entries.associateWith { type ->
            decoded[type] ?: MarketingContentProfile(type)
        }
        val source = runCatching {
            MarketingContentBundle.Source.valueOf(root.optString("source"))
        }.getOrDefault(MarketingContentBundle.Source.LOCAL)
        return MarketingContentBundle(
            profiles = profiles,
            syncedAtMillis = root.optLongOrNull("synced_at"),
            source = source,
        )
    }

    fun encodeFrozen(frozen: FrozenMarketingContent): JSONObject = JSONObject().apply {
        put("content_type", frozen.contentType.wireName)
        put("slots", JSONArray(frozen.slots))
        put("selected_index", frozen.selectedIndex)
        put("random_enabled", frozen.randomEnabled)
        put("resolved_index", frozen.resolvedIndex ?: JSONObject.NULL)
        put("resolved_text", frozen.resolvedText ?: JSONObject.NULL)
        put("reason", frozen.reason)
    }

    fun decodeFrozen(raw: JSONObject?): FrozenMarketingContent? {
        if (raw == null) return null
        val type = MarketingContentType.fromWire(raw.optString("content_type")) ?: return null
        return FrozenMarketingContent(
            contentType = type,
            slots = raw.optJSONArray("slots").toSlotList(),
            selectedIndex = raw.optInt("selected_index", 0),
            randomEnabled = raw.optBoolean("random_enabled", false),
            resolvedIndex = raw.optIntOrNull("resolved_index"),
            resolvedText = if (raw.isNull("resolved_text")) null else raw.optString("resolved_text"),
            reason = raw.optString("reason", "unknown"),
        )
    }

    private fun JSONArray?.toSlotList(): List<String> {
        if (this == null) return MarketingContentResolver.emptySlots()
        val values = buildList(length()) {
            for (index in 0 until length()) {
                add(if (isNull(index)) "" else optString(index))
            }
        }
        return MarketingContentResolver.normalizeSlots(values)
    }

    private fun JSONObject.optLongOrNull(key: String): Long? {
        if (!has(key) || isNull(key)) return null
        return when (val value = opt(key)) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }?.takeIf { it > 0L }
    }

    private fun JSONObject.optIntOrNull(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return when (val value = opt(key)) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }
}
