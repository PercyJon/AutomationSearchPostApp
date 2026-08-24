package com.example.douyinautomation.automation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrivateMessageEntryRulePolicyTest {

    @Test
    fun `built-in rules preserve existing message semantics and safety rejection`() {
        val rules = PrivateMessageEntryRulePolicy.defaultRules()

        assertTrue(
            PrivateMessageEntryRulePolicy.evaluate(
                PrivateMessageEntryRoute.SELECTOR,
                "发私信",
                rules,
            ).isAllowed,
        )
        assertFalse(
            PrivateMessageEntryRulePolicy.evaluate(
                PrivateMessageEntryRoute.SELECTOR,
                "咨询客服，私信",
                rules,
            ).isAllowed,
        )
    }

    @Test
    fun `remote deny terms are added without removing built-in deny terms`() {
        val remote = PrivateMessageEntryRuleCatalog(
            version = "remote-1",
            blockedTerms = listOf("商城"),
            selectorAllowedTerms = emptyList(),
            iconAllowedTerms = emptyList(),
            source = PrivateMessageEntryRuleCatalog.Source.REMOTE,
        )
        val rules = PrivateMessageEntryRulePolicy.effective(remote)

        assertFalse(
            PrivateMessageEntryRulePolicy.evaluate(
                PrivateMessageEntryRoute.SELECTOR,
                "发私信，进入商城",
                rules,
            ).isAllowed,
        )
        assertFalse(
            PrivateMessageEntryRulePolicy.evaluate(
                PrivateMessageEntryRoute.SELECTOR,
                "发私信，联系客服",
                rules,
            ).isAllowed,
        )
    }

    @Test
    fun `icon fallback retains its existing broad shopping rejection`() {
        val rules = PrivateMessageEntryRulePolicy.defaultRules()

        assertFalse(
            PrivateMessageEntryRulePolicy.evaluate(
                PrivateMessageEntryRoute.ICON_FALLBACK,
                "发私信，购物入口",
                rules,
            ).isAllowed,
        )
    }

    @Test
    fun `remote allow terms can narrow but never widen selector actions`() {
        val narrowed = PrivateMessageEntryRulePolicy.effective(
            PrivateMessageEntryRuleCatalog(
                version = "remote-2",
                blockedTerms = emptyList(),
                selectorAllowedTerms = listOf("发私信", "新增入口"),
                iconAllowedTerms = emptyList(),
                source = PrivateMessageEntryRuleCatalog.Source.REMOTE,
            ),
        )

        assertTrue(
            PrivateMessageEntryRulePolicy.evaluate(
                PrivateMessageEntryRoute.SELECTOR,
                "发私信",
                narrowed,
            ).isAllowed,
        )
        assertFalse(
            PrivateMessageEntryRulePolicy.evaluate(
                PrivateMessageEntryRoute.SELECTOR,
                "私信",
                narrowed,
            ).isAllowed,
        )
        assertFalse(
            PrivateMessageEntryRulePolicy.evaluate(
                PrivateMessageEntryRoute.SELECTOR,
                "新增入口",
                narrowed,
            ).isAllowed,
        )
    }
}
