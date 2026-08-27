package com.example.douyinautomation.automation

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarketingContentResolverTest {
    @Test
    fun `normalize pads and trims to five slots`() {
        val slots = MarketingContentResolver.normalizeSlots(listOf("  你好  ", "第二"))
        assertEquals(5, slots.size)
        assertEquals("你好", slots[0])
        assertEquals("第二", slots[1])
        assertTrue(slots.drop(2).all { it.isEmpty() })
    }

    @Test
    fun `selected first item is the default pick`() {
        val profile = MarketingContentProfile(
            contentType = MarketingContentType.BIZ_DM,
            slots = listOf("A", "B", "C", "", ""),
            selectedIndex = 0,
            randomEnabled = false,
        )
        val pick = MarketingContentResolver.resolve(profile)
        assertEquals(0, pick.index)
        assertEquals("A", pick.text)
        assertEquals("selected", pick.reason)
    }

    @Test
    fun `selected empty slot falls back to first non-blank`() {
        val profile = MarketingContentProfile(
            contentType = MarketingContentType.COMMENT_DM,
            slots = listOf("", "可用", "第三条", "", ""),
            selectedIndex = 0,
            randomEnabled = false,
        )
        val pick = MarketingContentResolver.resolve(profile)
        assertEquals(1, pick.index)
        assertEquals("可用", pick.text)
        assertEquals("fallback_first_non_blank", pick.reason)
    }

    @Test
    fun `random only draws from non-blank slots`() {
        val profile = MarketingContentProfile(
            contentType = MarketingContentType.BIZ_DM,
            slots = listOf("一", "", "三", "", "五"),
            selectedIndex = 0,
            randomEnabled = true,
        )
        val seen = mutableSetOf<Int>()
        listOf(0, 1, 2).forEach { draw ->
            val pick = MarketingContentResolver.resolve(profile, FixedIntRandom(draw))
            seen += pick.index!!
            assertTrue(pick.text in setOf("一", "三", "五"))
            assertEquals("random", pick.reason)
        }
        assertEquals(setOf(0, 2, 4), seen)
    }

    @Test
    fun `random with a single filled slot uses that slot`() {
        val profile = MarketingContentProfile(
            contentType = MarketingContentType.BIZ_DM,
            slots = listOf("", "仅一条", "", "", ""),
            selectedIndex = 0,
            randomEnabled = true,
        )
        val pick = MarketingContentResolver.resolve(profile)
        assertEquals(1, pick.index)
        assertEquals("仅一条", pick.text)
        assertEquals("fallback_first_non_blank", pick.reason)
    }

    @Test
    fun `all blank returns empty pick`() {
        val pick = MarketingContentResolver.resolve(MarketingContentProfile(MarketingContentType.BIZ_DM))
        assertNull(pick.index)
        assertNull(pick.text)
        assertEquals("empty", pick.reason)
        assertTrue(pick.isEmpty)
    }

    @Test
    fun `freeze captures resolved slot without later editor mutation`() {
        val profile = MarketingContentProfile(
            contentType = MarketingContentType.COMMENT_DM,
            slots = listOf("冻结文案", "另一条", "", "", ""),
            selectedIndex = 0,
            randomEnabled = false,
        )
        val frozen = MarketingContentResolver.freeze(profile)
        assertEquals("冻结文案", frozen.resolvedText)
        assertEquals(0, frozen.resolvedIndex)
        val edited = profile.copy(slots = listOf("新文案", "另一条", "", "", ""))
        assertEquals("冻结文案", frozen.resolvedText)
        assertEquals("新文案", MarketingContentResolver.resolve(edited).text)
    }
}

private class FixedIntRandom(private val value: Int) : Random() {
    override fun nextBits(bitCount: Int): Int = 0
    override fun nextInt(until: Int): Int = value.mod(until.coerceAtLeast(1))
}
