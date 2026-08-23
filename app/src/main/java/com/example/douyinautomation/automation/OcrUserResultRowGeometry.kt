package com.example.douyinautomation.automation

/** Screen-relative OCR row geometry derived from the verified 1080×2412 reference surface. */
object OcrUserResultRowGeometry {

    fun forScreen(screenSize: ScreenSize): Metrics {
        val width = screenSize.width.coerceAtLeast(1)
        val height = screenSize.height.coerceAtLeast(1)
        return Metrics(
            minFollowWidth = (width * MIN_FOLLOW_WIDTH_RATIO).toInt(),
            titleBottomTolerance = (height * TITLE_BOTTOM_TOLERANCE_RATIO).toInt(),
            minTitleDistance = (height * MIN_TITLE_DISTANCE_RATIO).toInt(),
            titleTopPadding = (height * TITLE_TOP_PADDING_RATIO).toInt(),
            rowBottomPadding = (height * ROW_BOTTOM_PADDING_RATIO).toInt(),
            maxStableRowDrift = maxStableRowDrift(height),
        )
    }

    fun maxStableRowDrift(screenHeight: Int): Int =
        (screenHeight.coerceAtLeast(1) * MAX_STABLE_ROW_DRIFT_RATIO).toInt()

    data class Metrics(
        val minFollowWidth: Int,
        val titleBottomTolerance: Int,
        val minTitleDistance: Int,
        val titleTopPadding: Int,
        val rowBottomPadding: Int,
        val maxStableRowDrift: Int,
    )

    private const val MIN_FOLLOW_WIDTH_RATIO = 0.03f
    private const val TITLE_BOTTOM_TOLERANCE_RATIO = 0.0067f
    private const val MIN_TITLE_DISTANCE_RATIO = 0.05f
    private const val TITLE_TOP_PADDING_RATIO = 0.0117f
    private const val ROW_BOTTOM_PADDING_RATIO = 0.0133f
    private const val MAX_STABLE_ROW_DRIFT_RATIO = 0.015f
}
