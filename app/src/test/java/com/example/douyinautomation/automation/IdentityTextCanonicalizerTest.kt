package com.example.douyinautomation.automation

import kotlin.test.Test
import kotlin.test.assertEquals

class IdentityTextCanonicalizerTest {
    @Test
    fun `normalizes traditional glyphs and OCR punctuation only for matching`() {
        assertEquals(
            IdentityTextCanonicalizer.normalize("佛山市澤轩家具有限公司"),
            IdentityTextCanonicalizer.normalize("佛山市泽轩家具有限公司"),
        )
    }
}
