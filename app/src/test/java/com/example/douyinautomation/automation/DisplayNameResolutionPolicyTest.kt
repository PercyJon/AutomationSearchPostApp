package com.example.douyinautomation.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayNameResolutionPolicyTest {

    @Test
    fun profileOcrIsLimitedToUncertainOrOcrBackedNames() {
        assertTrue(
            DisplayNameResolutionPolicy.shouldUseProfileOcr(
                hasOcrEngine = true,
                currentSource = UserResultIdentity.Source.OCR,
                listName = "完整名称",
                hasAccessibilityCandidate = false,
            ),
        )
        assertTrue(
            DisplayNameResolutionPolicy.shouldUseProfileOcr(
                hasOcrEngine = true,
                currentSource = UserResultIdentity.Source.ACCESSIBILITY,
                listName = "名称..",
                hasAccessibilityCandidate = false,
            ),
        )
        assertTrue(
            DisplayNameResolutionPolicy.shouldUseProfileOcr(
                hasOcrEngine = true,
                currentSource = UserResultIdentity.Source.ACCESSIBILITY,
                listName = "抖音组织认证：",
                hasAccessibilityCandidate = false,
            ),
        )
        assertFalse(
            DisplayNameResolutionPolicy.shouldUseProfileOcr(
                hasOcrEngine = true,
                currentSource = UserResultIdentity.Source.ACCESSIBILITY,
                listName = "完整名称",
                hasAccessibilityCandidate = false,
            ),
        )
    }

    @Test
    fun directMessageOcrNeverOverridesAnAccessibilityName() {
        assertFalse(
            DisplayNameResolutionPolicy.shouldUseDirectMessageOcr(
                hasOcrEngine = true,
                hasAccessibilityCandidate = true,
            ),
        )
        assertTrue(
            DisplayNameResolutionPolicy.shouldUseDirectMessageOcr(
                hasOcrEngine = true,
                hasAccessibilityCandidate = false,
            ),
        )
    }

    @Test
    fun clippedNameDetectionRejectsBlankAndTruncatedValues() {
        assertTrue(DisplayNameResolutionPolicy.isClipped(null))
        assertTrue(DisplayNameResolutionPolicy.isClipped("名称…"))
        assertTrue(DisplayNameResolutionPolicy.isClipped("名称.."))
        assertFalse(DisplayNameResolutionPolicy.isClipped("完整名称"))
    }
}
