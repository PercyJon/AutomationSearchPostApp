package com.example.douyinautomation.automation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the P0 anchor matcher on Android's real Bitmap/AssetManager implementations. It uses a
 * local synthetic action rail only: no app navigation, accessibility action, or network request
 * is involved.
 */
@RunWith(AndroidJUnit4::class)
class AlphaMaskedActionIconMatcherInstrumentedTest {
    @Test
    fun findsBothAlphaCompositedAnchorsInTheirBoundedActionRail() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val screenshot = Bitmap.createBitmap(SCREEN_WIDTH, SCREEN_HEIGHT, Bitmap.Config.ARGB_8888)
        val likeTemplate = decodeAsset(context, "like_icon_template.png")
        val collectTemplate = decodeAsset(context, "collect_icon_template.png")
        try {
            Canvas(screenshot).apply {
                // A dark scene is deliberately not a fixed white RGB target. The templates keep
                // their alpha and are composited as the player would draw translucent controls.
                drawColor(Color.rgb(23, 19, 22))
                drawBitmap(likeTemplate, null, LIKE_DESTINATION, null)
                drawBitmap(collectTemplate, null, COLLECT_DESTINATION, null)
            }
            val matcher = AlphaMaskedActionIconMatcher(context.assets)

            val like = matcher.findMatch(
                bitmap = screenshot,
                templateId = AlphaMaskedActionIconMatcher.LIKE_TEMPLATE_ID,
                searchRegion = actionRailRegion(LIKE_SEARCH_TOP_FRACTION, LIKE_SEARCH_BOTTOM_FRACTION),
            )
            val collect = matcher.findMatch(
                bitmap = screenshot,
                templateId = AlphaMaskedActionIconMatcher.COLLECT_TEMPLATE_ID,
                searchRegion = actionRailRegion(COLLECT_SEARCH_TOP_FRACTION, COLLECT_SEARCH_BOTTOM_FRACTION),
            )

            assertNotNull(like)
            assertNotNull(collect)
            assertTrue(like!!.confidence >= 0.88f)
            assertTrue(collect!!.confidence >= 0.88f)
            assertCentreNear(like.bounds, LIKE_DESTINATION)
            assertCentreNear(collect.bounds, COLLECT_DESTINATION)
        } finally {
            likeTemplate.recycle()
            collectTemplate.recycle()
            screenshot.recycle()
        }
    }

    private fun decodeAsset(
        context: android.content.Context,
        name: String,
    ): Bitmap = requireNotNull(
        context.assets.open(name).use(BitmapFactory::decodeStream),
    ) { "Missing P0 action-rail template asset: $name" }

    private fun actionRailRegion(topFraction: Float, bottomFraction: Float): Rect = Rect(
        (SCREEN_WIDTH * ACTION_RAIL_LEFT_FRACTION).roundToInt(),
        (SCREEN_HEIGHT * topFraction).roundToInt(),
        SCREEN_WIDTH,
        (SCREEN_HEIGHT * bottomFraction).roundToInt(),
    )

    private fun assertCentreNear(actual: Rect, expected: Rect) {
        val tolerance = maxOf(expected.width(), expected.height()) * CENTRE_TOLERANCE_BY_ICON_SIZE
        assertTrue(abs(actual.centerX() - expected.centerX()).toFloat() <= tolerance)
        assertTrue(abs(actual.centerY() - expected.centerY()).toFloat() <= tolerance)
    }

    private companion object {
        private const val SCREEN_WIDTH = 1080
        private const val SCREEN_HEIGHT = 2412
        private const val ACTION_RAIL_LEFT_FRACTION = 0.72f
        private const val LIKE_SEARCH_TOP_FRACTION = 0.40f
        private const val LIKE_SEARCH_BOTTOM_FRACTION = 0.65f
        private const val COLLECT_SEARCH_TOP_FRACTION = 0.56f
        private const val COLLECT_SEARCH_BOTTOM_FRACTION = 0.86f
        private const val CENTRE_TOLERANCE_BY_ICON_SIZE = 0.75f
        private fun destination(
            leftFraction: Float,
            topFraction: Float,
            widthFraction: Float,
            heightFraction: Float,
        ): Rect {
            val left = (SCREEN_WIDTH * leftFraction).roundToInt()
            val top = (SCREEN_HEIGHT * topFraction).roundToInt()
            return Rect(
                left,
                top,
                left + (SCREEN_WIDTH * widthFraction).roundToInt(),
                top + (SCREEN_HEIGHT * heightFraction).roundToInt(),
            )
        }
        private val LIKE_DESTINATION = destination(
            leftFraction = 0.8685f,
            topFraction = 0.5456f,
            widthFraction = 0.0898f,
            heightFraction = 0.0365f,
        )
        private val COLLECT_DESTINATION = destination(
            leftFraction = 0.8685f,
            topFraction = 0.7168f,
            widthFraction = 0.0898f,
            heightFraction = 0.0381f,
        )
    }
}
