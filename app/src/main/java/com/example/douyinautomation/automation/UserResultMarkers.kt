package com.example.douyinautomation.automation

/** Non-terminal helper rows rendered inside a user-results RecyclerView. */
object UserResultMarkers {
    private val accountHelpTokens = listOf(
        "找不到想找的账号",
        "找不到想要的账号",
        "找不到想搜的账号",
        "找不到想要的帐户",
        "告诉我们",
    )

    fun hasAccountHelp(context: ScreenContext): Boolean =
        (context.nodeText() + context.ocrText()).any { raw ->
            val value = TextNormalizer.normalize(raw)
            accountHelpTokens.any { token -> value.contains(TextNormalizer.normalize(token)) }
        }

    fun accountHelpOnly(context: ScreenContext): Boolean =
        hasAccountHelp(context) && StructuralUserRowDetector.find(context) == null
}
