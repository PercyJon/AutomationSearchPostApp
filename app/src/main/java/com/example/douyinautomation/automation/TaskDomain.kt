package com.example.douyinautomation.automation

import java.util.Locale

/** The execution modes exposed by task configuration. Real sending stays opt-in and gated. */
enum class TaskExecutionMode {
    SAFE_BLANK_PROBE,
    REAL_SEND_REQUIRES_CONFIRMATION,
}

data class SearchPreset(
    val id: String,
    val label: String,
    val keyword: String,
    val enabled: Boolean = true,
)

data class SearchPresetCatalog(
    val version: String,
    val items: List<SearchPreset>,
    val updatedAtMillis: Long,
    val source: Source = Source.LOCAL_CACHE,
) {
    enum class Source {
        REMOTE,
        LOCAL_CACHE,
        BUILT_IN,
    }
}

data class TaskDraft(
    val id: String,
    val name: String,
    val presetIds: List<String> = emptyList(),
    val customKeywords: List<String> = emptyList(),
    val region: String? = null,
    val blockedKeywords: List<String> = emptyList(),
    val maxUsers: Int = DEFAULT_MAX_USERS,
    val messageTemplate: String? = null,
    val executionMode: TaskExecutionMode = TaskExecutionMode.SAFE_BLANK_PROBE,
) {
    fun validationErrors(): List<String> = buildList {
        if (name.trim().isEmpty()) add("任务名称不能为空")
        if (presetIds.isEmpty() && customKeywords.none { it.isNotBlank() }) {
            add("至少选择一个预设搜索词或填写自定义搜索词")
        }
        if (maxUsers !in 1..MAX_USERS) add("用户数量上限必须在 1-$MAX_USERS 之间")
        if (executionMode == TaskExecutionMode.REAL_SEND_REQUIRES_CONFIRMATION &&
            messageTemplate.isNullOrBlank()
        ) {
            add("真实发送模式需要消息模板")
        }
    }

    fun toSnapshot(
        presets: SearchPresetCatalog,
        nowMillis: Long,
    ): TaskSnapshot {
        require(validationErrors().isEmpty()) { validationErrors().joinToString("；") }
        val presetKeywords = presetIds.mapNotNull { id ->
            presets.items.firstOrNull { it.id == id && it.enabled }?.keyword
        }
        val queries = QueryComposer.composeAll(
            region = region,
            baseKeywords = presetKeywords + customKeywords,
        )
        require(queries.isNotEmpty()) { "任务没有可执行的搜索词" }
        return TaskSnapshot(
            taskId = id,
            taskName = name.trim(),
            presetVersion = presets.version,
            baseKeywords = queries.map { it.baseKeyword },
            region = QueryComposer.normalize(region),
            composedQueries = queries.map { it.query },
            normalizedBlockedKeywords = BlockedKeywordEvaluator.normalizeTerms(blockedKeywords),
            maxUsers = maxUsers,
            messageTemplate = messageTemplate?.trim()?.takeIf { it.isNotEmpty() },
            executionMode = executionMode,
            createdAtMillis = nowMillis,
        )
    }

    companion object {
        const val DEFAULT_MAX_USERS = 20
        const val MAX_USERS = 500
    }
}

data class TaskSnapshot(
    val taskId: String,
    val taskName: String,
    val presetVersion: String,
    val baseKeywords: List<String>,
    val region: String,
    val composedQueries: List<String>,
    val normalizedBlockedKeywords: List<String>,
    val maxUsers: Int,
    val messageTemplate: String?,
    val executionMode: TaskExecutionMode,
    val createdAtMillis: Long,
)

data class ComposedSearchQuery(
    val baseKeyword: String,
    val query: String,
)

/** Deterministic query composition used by both the preview UI and the automation controller. */
object QueryComposer {
    fun normalize(value: String?): String = value
        .orEmpty()
        .trim()
        .replace(Regex("\\s+"), "")
        .replace("：", ":")
        .replace("．", ".")
        .replace("…", "")
        .replace("·", "")
        .replace("。", "")

    fun compose(region: String?, baseKeyword: String): ComposedSearchQuery? {
        val base = normalize(baseKeyword)
        if (base.isEmpty()) return null
        val normalizedRegion = normalize(region)
        val query = when {
            normalizedRegion.isEmpty() -> base
            base.startsWith(normalizedRegion) -> base
            else -> normalizedRegion + base
        }
        return ComposedSearchQuery(baseKeyword = base, query = query)
    }

    fun composeAll(region: String?, baseKeywords: Iterable<String>): List<ComposedSearchQuery> =
        baseKeywords.mapNotNull { compose(region, it) }.distinctBy { it.query }
}

enum class UserTextField {
    DISPLAY_NAME,
    ACCOUNT_HANDLE,
    ROW_METADATA,
    OCR_TEXT,
}

data class UserResultText(
    val displayName: String? = null,
    val accountHandle: String? = null,
    val rowMetadata: List<String> = emptyList(),
    val ocrText: List<String> = emptyList(),
)

data class BlockedKeywordMatch(
    val keyword: String,
    val fields: Set<UserTextField>,
)

data class BlockedKeywordEvaluation(
    val blocked: Boolean,
    val matches: List<BlockedKeywordMatch> = emptyList(),
) {
    val matchedKeywords: List<String> get() = matches.map(BlockedKeywordMatch::keyword).distinct()
}

/**
 * User-supplied filters intentionally support contains matching only in M3. This keeps behavior
 * predictable and avoids turning a task rule into an unreviewed regular-expression engine.
 */
object BlockedKeywordEvaluator {
    fun normalizeTerms(terms: Iterable<String>): List<String> = terms
        .map(::normalize)
        .filter(String::isNotEmpty)
        .distinct()

    fun evaluate(
        text: UserResultText,
        blockedKeywords: Iterable<String>,
    ): BlockedKeywordEvaluation {
        val terms = normalizeTerms(blockedKeywords)
        if (terms.isEmpty()) return BlockedKeywordEvaluation(blocked = false)
        val fields = linkedMapOf(
            UserTextField.DISPLAY_NAME to listOfNotNull(text.displayName),
            UserTextField.ACCOUNT_HANDLE to listOfNotNull(text.accountHandle),
            UserTextField.ROW_METADATA to text.rowMetadata,
            UserTextField.OCR_TEXT to text.ocrText,
        )
        val matches = terms.mapNotNull { term ->
            val matchedFields = fields.filterValues { values ->
                values.any { normalize(it).contains(term) }
            }.keys
            term.takeIf { matchedFields.isNotEmpty() }?.let {
                BlockedKeywordMatch(keyword = it, fields = matchedFields)
            }
        }
        return BlockedKeywordEvaluation(blocked = matches.isNotEmpty(), matches = matches)
    }

    private fun normalize(value: String?): String = value
        .orEmpty()
        .trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), "")
        .replace("：", ":")
        .replace("…", "")
        .replace("·", "")
        .replace("。", "")
}

interface SearchPresetRepository {
    suspend fun load(forceRefresh: Boolean = false): SearchPresetCatalog
}

/** M3 local source; its interface is ready for a remote/cache implementation later. */
class LocalSearchPresetRepository(
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) : SearchPresetRepository {
    @Volatile private var catalog: SearchPresetCatalog = SearchPresetCatalog(
        version = BUILT_IN_VERSION,
        items = BUILT_IN_PRESETS,
        updatedAtMillis = nowMillis(),
        source = SearchPresetCatalog.Source.BUILT_IN,
    )

    override suspend fun load(forceRefresh: Boolean): SearchPresetCatalog = catalog

    fun replaceForPreview(items: List<SearchPreset>, version: String = "local-preview") {
        catalog = SearchPresetCatalog(
            version = version,
            items = items,
            updatedAtMillis = nowMillis(),
            source = SearchPresetCatalog.Source.LOCAL_CACHE,
        )
    }

    companion object {
        const val BUILT_IN_VERSION = "built-in-1"
        val BUILT_IN_PRESETS = listOf(
            SearchPreset("redwood-sofa", "红木沙发", "红木沙发"),
            SearchPreset("solid-wood-table", "实木餐桌", "实木餐桌"),
            SearchPreset("tea-table", "红木茶桌", "红木茶桌"),
            SearchPreset("redwood-furniture", "红木家具", "红木家具"),
        )
    }
}
