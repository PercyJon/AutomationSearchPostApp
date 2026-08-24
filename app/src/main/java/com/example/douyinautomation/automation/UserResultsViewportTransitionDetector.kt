package com.example.douyinautomation.automation

/**
 * Recognises the short interval after the User tab becomes selected while its ViewPager has not
 * yet exposed any visible child content to accessibility. It is a state observation only; it
 * never authorises a gesture.
 */
internal object UserResultsViewportTransitionDetector {
    fun isSettling(context: ScreenContext): Boolean {
        val pager = context.nodes.firstOrNull { node ->
            node.isVisibleToUser &&
                node.bounds.width > 0 &&
                node.bounds.height > 0 &&
                node.className?.contains("ViewPager", ignoreCase = true) == true
        } ?: return false
        return context.nodes.none { node ->
            node.hierarchyPath.size > pager.hierarchyPath.size &&
                node.hierarchyPath.take(pager.hierarchyPath.size) == pager.hierarchyPath &&
                node.isVisibleToUser &&
                node.bounds.width > 0 &&
                node.bounds.height > 0
        }
    }
}
