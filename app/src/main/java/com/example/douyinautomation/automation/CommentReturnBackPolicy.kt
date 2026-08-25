package com.example.douyinautomation.automation

/**
 * Pure gates for returning from a commenter profile/DM to the comment sheet.
 *
 * Skip-probe starts on [PageKind.USER_PROFILE] and needs one BACK. Sampling after 120ms still
 * looks like the profile, so a second BACK overshoots onto the player. The DM path still needs
 * two BACKs without inserting that extra profile wait between them.
 */
internal object CommentReturnBackPolicy {
    fun shouldDispatchAnotherReturnBack(
        stillNested: Boolean,
        backsDispatched: Int,
        requiredBacks: Int,
        stillOnUserProfile: Boolean,
        profileLeavePollCompleted: Boolean,
    ): Boolean {
        if (!stillNested) return false
        if (backsDispatched < requiredBacks) return true
        if (stillOnUserProfile && !profileLeavePollCompleted) return false
        return stillNested
    }

    fun shouldPollForProfileLeave(
        stillOnUserProfile: Boolean,
        backsDispatched: Int,
        requiredBacks: Int,
    ): Boolean = stillOnUserProfile && backsDispatched > 0 && backsDispatched >= requiredBacks
}
