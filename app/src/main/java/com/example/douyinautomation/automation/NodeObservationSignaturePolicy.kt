package com.example.douyinautomation.automation

/**
 * Incrementally hashes the same semantic and geometry fields used by the former node-tree
 * signature. It avoids allocating one concatenated string per accessibility event.
 */
object NodeObservationSignaturePolicy {
    fun hash(nodes: List<NodeSnapshot>): Int {
        var signature = INITIAL_SIGNATURE
        nodes.forEach { node ->
            signature = signature.mix(node.text?.hashCode() ?: 0)
            signature = signature.mix(node.contentDescription?.hashCode() ?: 0)
            signature = signature.mix(node.bounds.left)
            signature = signature.mix(node.bounds.top)
            signature = signature.mix(node.bounds.right)
            signature = signature.mix(node.bounds.bottom)
            signature = signature.mix(node.isSelected.asSignatureValue())
            signature = signature.mix(node.isVisibleToUser.asSignatureValue())
        }
        return signature
    }

    private fun Int.mix(value: Int): Int = this * HASH_MULTIPLIER + value

    private fun Boolean.asSignatureValue(): Int = if (this) 1 else 0

    private const val INITIAL_SIGNATURE = 1
    private const val HASH_MULTIPLIER = 31
}
