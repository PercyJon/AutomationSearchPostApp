package com.example.douyinautomation.automation

/**
 * Small, conservative correction layer for Chinese profile names.
 *
 * The recognizer is not allowed to rewrite arbitrary names. It only corrects an explicitly known
 * two-character OCR confusion at the beginning of a longer name. This avoids ambiguous matches
 * such as "抗州" being equally close to multiple cities ending in "州".
 */
object ProfileNameCorrection {
    fun correct(value: String): String {
        val trimmed = value.trim()
        // Short two-to-four-character names are too ambiguous for an automatic correction.
        if (trimmed.length < 5) return trimmed
        val correctedPrefix = PREFIX_CORRECTIONS[trimmed.take(2)] ?: return trimmed
        return correctedPrefix + trimmed.drop(2)
    }

    private val PREFIX_CORRECTIONS = mapOf(
        "抗州" to "杭州",
        "杭洲" to "杭州",
        "广洲" to "广州",
        "苏洲" to "苏州",
        "郑洲" to "郑州",
        "福洲" to "福州",
        "温洲" to "温州",
        "常洲" to "常州",
        "兰洲" to "兰州",
    )
}
