package com.example.douyinautomation.automation

/**
 * Canonicalizes identity text only for matching and deduplication.
 *
 * Douyin OCR can alternate between simplified/traditional glyphs or drop one glyph from a long
 * company name on adjacent frames. Stored display text is never rewritten; this compact mapping
 * only prevents the same account from being treated as two users.
 */
object IdentityTextCanonicalizer {
    fun normalize(value: String): String = value
        .lowercase()
        .filterNot(Char::isWhitespace)
        .replace("…", "")
        .replace(".", "")
        .replace("·", "")
        .replace("。", "")
        .map { TRADITIONAL_OCR_EQUIVALENTS[it] ?: it }
        .joinToString("")

    private val TRADITIONAL_OCR_EQUIVALENTS = mapOf(
        '澤' to '泽',
        '軒' to '轩',
        '傢' to '家',
        '俱' to '具',
        '櫃' to '柜',
        '廠' to '厂',
        '廣' to '广',
        '順' to '顺',
        '區' to '区',
        '興' to '兴',
        '業' to '业',
        '門' to '门',
        '後' to '后',
        '關' to '关',
        '認' to '认',
        '證' to '证',
        '號' to '号',
        '賬' to '账',
        '鋪' to '铺',
        '標' to '标',
        '題' to '题',
        '與' to '与',
        '荼' to '茶',
        '荠' to '荞',
        '養' to '养',
        '陰' to '阴',
        '陽' to '阳',
        '紅' to '红',
        '發' to '发',
        '國' to '国',
        '東' to '东',
        '華' to '华',
        '龍' to '龙',
        '鳳' to '凤',
        '漢' to '汉',
        '寧' to '宁',
        '鄉' to '乡',
        '縣' to '县',
        '鎮' to '镇',
        '莊' to '庄',
        '來' to '来',
        '雲' to '云',
        '電' to '电',
        '話' to '话',
    )
}
