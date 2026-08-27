package com.example.douyinautomation.automation

/**
 * After a verified user-row tap, the next frames are often UNKNOWN because the profile tree has
 * not mounted. Full-screen page_probe OCR blocks the node poller without changing the click
 * post-condition. The same applies while waiting for the chat composer after tapping 发私信.
 */
object ProfileOpenOcrPolicy {
    fun shouldBypassUnknownPageOcr(phase: AutomationPhase): Boolean =
        phase == AutomationPhase.WAITING_FOR_PROFILE ||
            phase == AutomationPhase.OPENING_MESSAGE_ENTRY ||
            phase == AutomationPhase.WAITING_FOR_DIRECT_MESSAGE
}
