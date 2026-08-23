package com.example.douyinautomation.automation

/**
 * Keeps a post-swipe continuation anchored to the exact already-processed identity.
 *
 * A custom list can keep one off-screen row structurally visible while that row has no stable
 * identity. That unrelated row must not prevent a continuation after the previous user itself
 * has been observed stably. The caller still applies normal identity and geometry gates before
 * opening the following row.
 */
internal data class UserResultsAnchorObservation(
    val key: String?,
    val consecutiveObservations: Int,
)

internal object UserResultsAnchorContinuationPolicy {
    fun observe(
        previous: UserResultsAnchorObservation?,
        anchorIndex: Int,
        anchorFingerprint: String?,
    ): UserResultsAnchorObservation {
        val key = anchorFingerprint?.takeIf { anchorIndex >= 0 }
            ?.let { "$anchorIndex:$it" }
        val consecutiveObservations = if (key != null && key == previous?.key) {
            previous.consecutiveObservations + 1
        } else {
            1
        }
        return UserResultsAnchorObservation(key, consecutiveObservations)
    }

    fun canContinue(
        observation: UserResultsAnchorObservation,
        stableViewportObservations: Int,
        requiredObservations: Int,
    ): Boolean =
        observation.key != null &&
            observation.consecutiveObservations >= requiredObservations &&
            stableViewportObservations >= requiredObservations
}
