package com.example.douyinautomation.automation

/**
 * The in-app group/private-message sheet that opens after tapping a home IM heads-up.
 * It sits on top of HOME (tabs and search remain visible behind it) and is not a full
 * [PageKind.DIRECT_MESSAGE] page: there is often no labelled 发送 button.
 *
 * Classification only. The controller must BACK (or wait for the caller to BACK). It must not
 * tap 回复, 打招呼, or the dimmed search icon above the sheet.
 */
data class GroupChatOverlayDetection(
    val isGroupChatOverlay: Boolean,
    val confidence: Float,
    val reasons: List<String>,
)

object GroupChatOverlayDetector {
    private val composerMarkers = listOf("发送消息")
    private val chipMarkers = listOf("打招呼", "比心", "@群聊ai", "群聊ai", "捂脸")

    fun detect(context: ScreenContext): GroupChatOverlayDetection {
        val composerHits = composerHits(context)
        val chipHits = chipHits(context)
        val isOverlay = composerHits.isNotEmpty() && chipHits.isNotEmpty()
        val reasons = buildList {
            if (composerHits.isNotEmpty()) add("composer: ${composerHits.joinToString()}")
            if (chipHits.isNotEmpty()) add("chips: ${chipHits.joinToString()}")
            if (!isOverlay) add("No group-chat overlay signature matched")
        }
        return GroupChatOverlayDetection(
            isGroupChatOverlay = isOverlay,
            confidence = when {
                !isOverlay -> 0.1f
                composerHits.isNotEmpty() && chipHits.size >= 2 -> 0.94f
                else -> 0.88f
            },
            reasons = reasons,
        )
    }

    private fun composerHits(context: ScreenContext): List<String> {
        val bottom = (context.screenSize.height * COMPOSER_TOP_RATIO).toInt()
        val nodeHits = context.nodes.asSequence()
            .filter { it.isVisibleToUser && it.bounds.height > 0 && it.bounds.top >= bottom }
            .flatMap { node ->
                node.searchableText().asSequence().flatMap { value ->
                    TextNormalizer.matchingTerms(value, composerMarkers)
                }
            }
        val ocrHits = context.ocrBlocks.asSequence()
            .filter { block ->
                block.bounds == ScreenBounds.EMPTY || block.bounds.top >= bottom
            }
            .flatMap { block -> TextNormalizer.matchingTerms(block.text, composerMarkers) }
        return (nodeHits + ocrHits).distinct().toList()
    }

    private fun chipHits(context: ScreenContext): List<String> {
        val nodeHits = context.nodes.asSequence()
            .filter { it.isVisibleToUser }
            .flatMap { node ->
                node.searchableText().asSequence().flatMap { value ->
                    TextNormalizer.matchingTerms(value, chipMarkers)
                }
            }
        val ocrHits = context.ocrBlocks.asSequence().flatMap { block ->
            TextNormalizer.matchingTerms(block.text, chipMarkers)
        }
        return (nodeHits + ocrHits).distinct().toList()
    }

    private const val COMPOSER_TOP_RATIO = 0.62f
}
