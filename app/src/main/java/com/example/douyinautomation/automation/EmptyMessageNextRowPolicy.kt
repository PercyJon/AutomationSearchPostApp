package com.example.douyinautomation.automation

/**
 * After a verified blank probe, USER_RESULTS can classify before follow-button anchors are in
 * the tree. Poll that live structural row before falling back to OCR identity continuation.
 */
object EmptyMessageNextRowPolicy {
    fun shouldPollForNextStructuralRow(
        pageKind: PageKind?,
        nextVisible: StructuralUserRowMatch?,
        attempt: Int,
        maxAttempts: Int,
    ): Boolean =
        pageKind == PageKind.USER_RESULTS &&
            nextVisible == null &&
            attempt < maxAttempts
}
