package com.example.douyinautomation.automation

/**
 * Single entry point for display-name resolution after a page has already been verified.
 *
 * Surface-specific candidate extraction intentionally remains in the existing resolvers: those
 * rules contain the page-local accessibility/OCR geometry evidence. This object owns only the
 * common source order, OCR gate and profile-name stability confirmation, so controller code
 * cannot accidentally give OCR precedence over an accessible name.
 */
object DisplayNameResolver {

    enum class Surface {
        PROFILE,
        DIRECT_MESSAGE,
    }

    data class Resolution(
        val value: String,
        val source: UserResultIdentity.Source,
    )

    fun fromAccessibility(
        surface: Surface,
        context: ScreenContext,
        previousName: String?,
    ): String? = when (surface) {
        Surface.PROFILE -> ProfileDisplayNameResolver.fromAccessibility(context, previousName)
        Surface.DIRECT_MESSAGE -> DirectMessageDisplayNameResolver.fromAccessibility(context, previousName)
    }

    fun fromOcr(
        surface: Surface,
        context: ScreenContext,
        previousName: String?,
    ): String? = when (surface) {
        Surface.PROFILE -> ProfileDisplayNameResolver.fromOcr(context, previousName)
        Surface.DIRECT_MESSAGE -> DirectMessageDisplayNameResolver.fromOcr(context, previousName)
    }

    /** Accessibility is always selected before OCR once both candidates satisfy page evidence. */
    fun arbitrate(
        accessibilityCandidate: String?,
        ocrCandidate: String?,
    ): Resolution? = accessibilityCandidate
        ?.takeIf(String::isNotBlank)
        ?.let { Resolution(it, UserResultIdentity.Source.ACCESSIBILITY) }
        ?: ocrCandidate
            ?.takeIf(String::isNotBlank)
            ?.let { Resolution(it, UserResultIdentity.Source.OCR) }

    fun shouldUseOcr(
        surface: Surface,
        hasOcrEngine: Boolean,
        currentSource: UserResultIdentity.Source?,
        listName: String?,
        hasAccessibilityCandidate: Boolean,
    ): Boolean = when (surface) {
        Surface.PROFILE -> DisplayNameResolutionPolicy.shouldUseProfileOcr(
            hasOcrEngine = hasOcrEngine,
            currentSource = currentSource,
            listName = listName,
            hasAccessibilityCandidate = hasAccessibilityCandidate,
        )

        Surface.DIRECT_MESSAGE -> DisplayNameResolutionPolicy.shouldUseDirectMessageOcr(
            hasOcrEngine = hasOcrEngine,
            hasAccessibilityCandidate = hasAccessibilityCandidate,
        )
    }

    /**
     * Confirms a profile title against a fresh accessibility tree before it can be persisted.
     * The controller supplies the bounded wait and fresh tree read; this resolver owns the
     * equality rule and the confirmation loop.
     */
    suspend fun confirmProfileAccessibility(
        firstCandidate: String,
        confirmationAttempts: Int,
        awaitNextConfirmation: suspend () -> Unit,
        nextCandidate: suspend (currentCandidate: String) -> String?,
    ): String? {
        var candidate = firstCandidate
        repeat((confirmationAttempts - 1).coerceAtLeast(0)) {
            awaitNextConfirmation()
            val next = nextCandidate(candidate) ?: return null
            if (ProfileDisplayNameResolver.equivalent(candidate, next)) return next
            candidate = next
        }
        return null
    }
}
