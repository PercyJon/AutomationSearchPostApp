package com.example.douyinautomation.automation

/** Immutable result of adding one comment viewport to the in-memory candidate ledger. */
data class CommentLedgerUpdate(
    val added: Int,
    val duplicates: Int,
    val total: Int,
)

/**
 * Dedupe boundary for comment pagination. A partially loaded second viewport commonly contains
 * the last two or three rows from the previous viewport; identity keys keep those rows from
 * being opened twice. Raw comment text is not persisted by this class.
 */
class CommentCandidateLedger {
    private val candidatesByIdentity = LinkedHashMap<String, CommentUserCandidate>()

    val candidates: List<CommentUserCandidate>
        get() = candidatesByIdentity.values.toList()

    val size: Int get() = candidatesByIdentity.size

    fun add(extraction: CommentCandidateExtraction): CommentLedgerUpdate {
        var added = 0
        var duplicates = 0
        extraction.candidates.forEach { candidate ->
            if (candidatesByIdentity.putIfAbsent(candidate.identityKey, candidate) == null) {
                added += 1
            } else {
                duplicates += 1
            }
        }
        return CommentLedgerUpdate(added = added, duplicates = duplicates, total = size)
    }

    fun clear() = candidatesByIdentity.clear()
}
