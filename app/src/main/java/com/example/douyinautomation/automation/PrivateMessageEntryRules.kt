package com.example.douyinautomation.automation

/** A versioned, remote-deliverable rule catalog for the already verified profile action surface. */
data class PrivateMessageEntryRuleCatalog(
    val version: String,
    val blockedTerms: List<String>,
    val selectorAllowedTerms: List<String>,
    val iconAllowedTerms: List<String>,
    val updatedAtMillis: Long? = null,
    val source: Source = Source.BUILT_IN,
) {
    enum class Source {
        BUILT_IN,
        LOCAL_CACHE,
        REMOTE,
    }
}

/** The two existing entry routes intentionally retain distinct semantic vocabularies. */
enum class PrivateMessageEntryRoute {
    SELECTOR,
    ICON_FALLBACK,
}

/**
 * Pure private-message entry policy.
 *
 * A remote catalog can only make the entry rule more conservative: deny terms are unioned with
 * the built-in baseline, while allow terms are intersected with their route baseline. Therefore a
 * malformed or over-broad remote allow list cannot create a new automatic click target.
 */
object PrivateMessageEntryRulePolicy {
    private val builtInCatalog = PrivateMessageEntryRuleCatalog(
        version = "built-in-v1",
        blockedTerms = emptyList(),
        selectorAllowedTerms = listOf("发私信", "私信", "message", "direct message", "paper", "plane"),
        iconAllowedTerms = listOf("发私信", "私信", "message", "direct message", "paper", "plane", "im_"),
    )

    data class EffectiveRules(
        val version: String,
        val selectorBlockedTerms: Set<String>,
        val iconBlockedTerms: Set<String>,
        val selectorAllowedTerms: Set<String>,
        val iconAllowedTerms: Set<String>,
        val source: PrivateMessageEntryRuleCatalog.Source,
    )

    data class Verdict(
        val isAllowed: Boolean,
        val reason: String,
    )

    fun defaultRules(): EffectiveRules = effective(null)

    fun effective(remote: PrivateMessageEntryRuleCatalog?): EffectiveRules {
        val baselineSelectorBlocked = normalizeTerms(SELECTOR_BLOCKED_TERMS)
        val baselineIconBlocked = normalizeTerms(ICON_BLOCKED_TERMS)
        val baselineSelector = normalizeTerms(builtInCatalog.selectorAllowedTerms)
        val baselineIcon = normalizeTerms(builtInCatalog.iconAllowedTerms)
        if (remote == null) {
            return EffectiveRules(
                version = builtInCatalog.version,
                selectorBlockedTerms = baselineSelectorBlocked,
                iconBlockedTerms = baselineIconBlocked,
                selectorAllowedTerms = baselineSelector,
                iconAllowedTerms = baselineIcon,
                source = PrivateMessageEntryRuleCatalog.Source.BUILT_IN,
            )
        }
        val remoteBlocked = normalizeTerms(remote.blockedTerms)
        val remoteSelector = normalizeTerms(remote.selectorAllowedTerms)
        val remoteIcon = normalizeTerms(remote.iconAllowedTerms)
        return EffectiveRules(
            version = remote.version.trim().takeIf(String::isNotBlank) ?: builtInCatalog.version,
            selectorBlockedTerms = baselineSelectorBlocked + remoteBlocked,
            iconBlockedTerms = baselineIconBlocked + remoteBlocked,
            selectorAllowedTerms = narrowedBaseline(baselineSelector, remoteSelector),
            iconAllowedTerms = narrowedBaseline(baselineIcon, remoteIcon),
            source = remote.source,
        )
    }

    fun evaluate(
        route: PrivateMessageEntryRoute,
        searchableText: String,
        rules: EffectiveRules = defaultRules(),
    ): Verdict {
        val text = searchableText.lowercase()
        val blockedTerms = when (route) {
            PrivateMessageEntryRoute.SELECTOR -> rules.selectorBlockedTerms
            PrivateMessageEntryRoute.ICON_FALLBACK -> rules.iconBlockedTerms
        }
        val blocked = blockedTerms.firstOrNull(text::contains)
        if (blocked != null) return Verdict(false, "blocked:$blocked")
        val allowedTerms = when (route) {
            PrivateMessageEntryRoute.SELECTOR -> rules.selectorAllowedTerms
            PrivateMessageEntryRoute.ICON_FALLBACK -> rules.iconAllowedTerms
        }
        return if (allowedTerms.any(text::contains)) {
            Verdict(true, "semantic_message")
        } else {
            Verdict(false, "missing_message_semantics")
        }
    }

    private fun narrowedBaseline(
        baseline: Set<String>,
        remote: Set<String>,
    ): Set<String> = if (remote.isEmpty()) baseline else baseline.intersect(remote)

    private fun normalizeTerms(values: List<String>): Set<String> = values.asSequence()
        .map { it.trim().lowercase() }
        .filter { it.length in MIN_TERM_LENGTH..MAX_TERM_LENGTH }
        .toSet()

    private const val MIN_TERM_LENGTH = 2
    private const val MAX_TERM_LENGTH = 40
    private val SELECTOR_BLOCKED_TERMS = listOf("客服", "咨询", "购物车", "商品")
    private val ICON_BLOCKED_TERMS = listOf("客服", "咨询", "购物")
}
