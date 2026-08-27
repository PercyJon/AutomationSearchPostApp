package com.example.douyinautomation.automation

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DisplayNameResolverTest {

    @Test
    fun `unified resolver delegates each verified surface to its existing parser`() {
        val profileContext = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(
                NodeSnapshot(text = "佛山红木家具", bounds = ScreenBounds(180, 360, 900, 430)),
            ),
        )
        val directMessageContext = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(
                NodeSnapshot(
                    className = "android.widget.ImageView",
                    contentDescription = "用户头像",
                    bounds = ScreenBounds(442, 325, 638, 521),
                ),
                NodeSnapshot(text = "佛山红木家具", bounds = ScreenBounds(300, 580, 780, 656)),
            ),
        )

        assertEquals(
            "佛山红木家具",
            DisplayNameResolver.fromAccessibility(
                DisplayNameResolver.Surface.PROFILE,
                profileContext,
                previousName = null,
            ),
        )
        assertEquals(
            "佛山红木家具",
            DisplayNameResolver.fromAccessibility(
                DisplayNameResolver.Surface.DIRECT_MESSAGE,
                directMessageContext,
                previousName = null,
            ),
        )
    }

    @Test
    fun `accessibility candidate always wins source arbitration`() {
        val resolution = DisplayNameResolver.arbitrate(
            accessibilityCandidate = "无障碍名称",
            ocrCandidate = "OCR 名称",
        )

        assertEquals("无障碍名称", resolution?.value)
        assertEquals(UserResultIdentity.Source.ACCESSIBILITY, resolution?.source)
    }

    @Test
    fun `organization chrome does not beat an OCR shop name`() {
        val resolution = DisplayNameResolver.arbitrate(
            accessibilityCandidate = "抖音组织认证：",
            ocrCandidate = "悟空室界木作旗舰店",
        )

        assertEquals("悟空室界木作旗舰店", resolution?.value)
        assertEquals(UserResultIdentity.Source.OCR, resolution?.source)
    }

    @Test
    fun `OCR is selected only when accessibility candidate is absent`() {
        val resolution = DisplayNameResolver.arbitrate(
            accessibilityCandidate = null,
            ocrCandidate = "OCR 名称",
        )

        assertEquals("OCR 名称", resolution?.value)
        assertEquals(UserResultIdentity.Source.OCR, resolution?.source)
        assertNull(DisplayNameResolver.arbitrate(null, null))
    }

    @Test
    fun `profile confirmation requires two equivalent fresh candidates`() = runBlocking {
        var waits = 0
        val confirmed = DisplayNameResolver.confirmProfileAccessibility(
            firstCandidate = "佛山红木家具",
            confirmationAttempts = 2,
            awaitNextConfirmation = { waits += 1 },
            nextCandidate = { "佛山红木家具" },
        )

        assertEquals("佛山红木家具", confirmed)
        assertEquals(1, waits)

        val unstable = DisplayNameResolver.confirmProfileAccessibility(
            firstCandidate = "佛山红木家具",
            confirmationAttempts = 2,
            awaitNextConfirmation = {},
            nextCandidate = { "佛山古典家具" },
        )
        assertNull(unstable)
    }

    @Test
    fun `OCR gate remains surface specific under unified entry`() {
        assertTrue(
            DisplayNameResolver.shouldUseOcr(
                surface = DisplayNameResolver.Surface.PROFILE,
                hasOcrEngine = true,
                currentSource = UserResultIdentity.Source.OCR,
                listName = "完整名称",
                hasAccessibilityCandidate = false,
            ),
        )
        assertTrue(
            !DisplayNameResolver.shouldUseOcr(
                surface = DisplayNameResolver.Surface.DIRECT_MESSAGE,
                hasOcrEngine = true,
                currentSource = UserResultIdentity.Source.ACCESSIBILITY,
                listName = "完整名称",
                hasAccessibilityCandidate = true,
            ),
        )
    }
}
