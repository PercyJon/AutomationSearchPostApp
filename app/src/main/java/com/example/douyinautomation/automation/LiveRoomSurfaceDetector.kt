package com.example.douyinautomation.automation

/**
 * Structural signals for Douyin live surfaces.  The detector intentionally requires the pair of
 * controls seen in an opened room: a close control in the upper-right and a share/forward
 * control in the lower-right.  A lone “直播中” label is not enough because it also appears on
 * ordinary profile/result cards.
 */
object LiveRoomSurfaceDetector {
    private val closeTerms = listOf("关闭", "close", "退出直播间", "退出直播", "×", "✕")
    private val shareTerms = listOf("分享", "share", "转发")
    private val liveContextTerms = listOf("直播间", "直播广场", "on live", "说点什么", "直播中")

    fun findCloseButton(context: ScreenContext): NodeSnapshot? = context.nodes.asSequence()
        .filter { it.isVisibleToUser && it.isClickable && it.bounds.width > 0 && it.bounds.height > 0 }
        .filter { node ->
            val bounds = node.normalizedBounds(context.screenSize)
            bounds.top <= 0.22f && bounds.left >= 0.72f &&
                TextNormalizer.matchesAny(node.searchableText().joinToString(" "), closeTerms)
        }
        .maxByOrNull { it.normalizedBounds(context.screenSize).centerX }

    fun findShareButton(context: ScreenContext): NodeSnapshot? = context.nodes.asSequence()
        .filter { it.isVisibleToUser && it.bounds.width > 0 && it.bounds.height > 0 }
        .filter { node ->
            val bounds = node.normalizedBounds(context.screenSize)
            bounds.top >= 0.62f && bounds.left >= 0.68f &&
                TextNormalizer.matchesAny(node.searchableText().joinToString(" "), shareTerms)
        }
        .maxByOrNull { it.normalizedBounds(context.screenSize).centerX }

    fun isOpenedRoom(context: ScreenContext): Boolean {
        val close = findCloseButton(context)
        val share = findShareButton(context)
        if (close != null && share != null) return true

        // OCR is used only to classify the page.  We still exit through global Back when the
        // semantic close node is unavailable, so OCR never directly authorizes a click.
        val allText = (context.nodeText() + context.ocrText()).joinToString(" ")
        val hasLiveContext = TextNormalizer.matchesAny(allText, liveContextTerms)
        val hasCloseShape = context.nodes.any { node ->
            val bounds = node.normalizedBounds(context.screenSize)
            node.isVisibleToUser && node.isClickable && isControlSized(node) &&
                bounds.top <= 0.22f && bounds.left >= 0.72f &&
                TextNormalizer.matchesAny(node.searchableText().joinToString(" "), closeTerms)
        } || context.ocrBlocks.any { block ->
            val bounds = block.bounds.normalized(context.screenSize)
            block.bounds != ScreenBounds.EMPTY && bounds.top <= 0.22f && bounds.left >= 0.72f &&
                TextNormalizer.matchesAny(block.text, closeTerms)
        } || hasUnlabelledCloseControl(context)
        val hasShareShape = context.nodes.any { node ->
            val bounds = node.normalizedBounds(context.screenSize)
            node.isVisibleToUser && node.isClickable && isControlSized(node) &&
                bounds.top >= 0.62f && bounds.left >= 0.68f &&
                TextNormalizer.matchesAny(node.searchableText().joinToString(" "), shareTerms)
        } || context.ocrBlocks.any { block ->
            val bounds = block.bounds.normalized(context.screenSize)
            block.bounds != ScreenBounds.EMPTY && bounds.top >= 0.62f && bounds.left >= 0.68f &&
                TextNormalizer.matchesAny(block.text, shareTerms)
        } || hasUnlabelledShareControl(context)
        // The geometry fallback is only allowed with room-specific context. A normal feed also
        // has a top-right search icon and a lower-right share icon, so requiring “说点什么” (or
        // the explicit room header) prevents those controls from being mistaken for a room.
        val hasRoomComposer = allText.contains("说点什么") ||
            allText.contains("欢迎来到直播间") ||
            allText.contains("直播广场")
        return hasLiveContext && hasRoomComposer && hasCloseShape && hasShareShape
    }

    fun hasEntryPrompt(context: ScreenContext): Boolean {
        val nodePrompt = context.nodes.any { node ->
            if (!node.isVisibleToUser || node.bounds.height <= 0) return@any false
            val bounds = node.normalizedBounds(context.screenSize)
            (node.bounds == ScreenBounds.EMPTY || (bounds.top >= 0.18f && bounds.bottom <= 0.92f)) &&
                TextNormalizer.matchesAny(node.searchableText().joinToString(" "), DouyinLabels.liveRoomEntry)
        }
        val ocrPrompt = context.ocrBlocks.any { block ->
            val bounds = block.bounds.normalized(context.screenSize)
            (block.bounds == ScreenBounds.EMPTY || (bounds.top >= 0.18f && bounds.bottom <= 0.92f)) &&
                TextNormalizer.matchesAny(block.text, DouyinLabels.liveRoomEntry)
        }
        val combined = context.ocrText().joinToString("")
        return nodePrompt || ocrPrompt || TextNormalizer.matchesAny(combined, DouyinLabels.liveRoomEntry)
    }

    private fun hasUnlabelledCloseControl(context: ScreenContext): Boolean =
        context.nodes.any { node ->
            val bounds = node.normalizedBounds(context.screenSize)
            node.isVisibleToUser && node.isClickable && isControlSized(node) &&
                bounds.top <= 0.22f && bounds.left >= 0.78f &&
                // X controls are normally close to square. Keeping this ratio tight avoids
                // treating a wide search/header container as the close button.
                aspectRatio(node).let { it in 0.65f..1.55f }
        }

    private fun hasUnlabelledShareControl(context: ScreenContext): Boolean =
        context.nodes.any { node ->
            val bounds = node.normalizedBounds(context.screenSize)
            node.isVisibleToUser && node.isClickable && isControlSized(node) &&
                bounds.top >= 0.72f && bounds.left >= 0.76f &&
                aspectRatio(node).let { it in 0.65f..1.55f }
        }

    private fun isControlSized(node: NodeSnapshot): Boolean =
        node.bounds.width in 24..260 && node.bounds.height in 24..260

    private fun aspectRatio(node: NodeSnapshot): Float =
        node.bounds.width.toFloat() / node.bounds.height.coerceAtLeast(1)
}
