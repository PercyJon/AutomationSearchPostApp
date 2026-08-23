package com.example.douyinautomation.automation

/**
 * Task-level comment-candidate dedupe state.
 *
 * It retains only SHA-256 identity references, so a resumed task can skip candidates already
 * attempted before process recreation without persisting comment text or raw account data.
 */
class CommentCandidateResumeLedger {
    private val fingerprints = LinkedHashSet<String>()

    fun restore(savedFingerprints: Iterable<String>) {
        fingerprints.clear()
        fingerprints += savedFingerprints.filter(String::isNotBlank)
    }

    fun contains(identityKey: String): Boolean =
        UserIdentityFingerprint.fromStableKey(identityKey) in fingerprints

    /** Returns the new fingerprint only when this candidate has not been attempted in the task. */
    fun markProcessed(identityKey: String): String? {
        val fingerprint = UserIdentityFingerprint.fromStableKey(identityKey)
        return fingerprint.takeIf(fingerprints::add)
    }

    fun snapshot(): Set<String> = fingerprints.toSet()

    fun clear() = fingerprints.clear()
}
