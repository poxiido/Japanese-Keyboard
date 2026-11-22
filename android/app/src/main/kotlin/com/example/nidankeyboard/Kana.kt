package com.example.nidankeyboard

data class RowDef(
    val key: String,
    val label: String,
    val kana: Array<String?>,
    val dakKana: Array<String?>? = null,
    val handakKana: Array<String?>? = null
)

/**
 * Same table, column split and gesture rules as the web prototype:
 * tap = plain kana; right-drag = voiced (dakuten) kana if the cell has one,
 * else its small-kana form; left-drag = half-voiced (handakuten) kana, or
 * the small-kana form if the voiced slot was already taken by the right-drag.
 * う (row "a", index 2) is hardcoded the other way around: right = small ぅ,
 * left = voiced ゔ.
 */
object Kana {
    val ROWS = listOf(
        RowDef("a", "あ", arrayOf("あ", "い", "う", "え", "お")),
        RowDef("ka", "か", arrayOf("か", "き", "く", "け", "こ"), dakKana = arrayOf("が", "ぎ", "ぐ", "げ", "ご")),
        RowDef("sa", "さ", arrayOf("さ", "し", "す", "せ", "そ"), dakKana = arrayOf("ざ", "じ", "ず", "ぜ", "ぞ")),
        RowDef("ta", "た", arrayOf("た", "ち", "つ", "て", "と"), dakKana = arrayOf("だ", "ぢ", "づ", "で", "ど")),
        RowDef("na", "な", arrayOf("な", "に", "ぬ", "ね", "の")),
        RowDef(
            "ha", "は", arrayOf("は", "ひ", "ふ", "へ", "ほ"),
            dakKana = arrayOf("ば", "び", "ぶ", "べ", "ぼ"),
            handakKana = arrayOf("ぱ", "ぴ", "ぷ", "ぺ", "ぽ")
        ),
        RowDef("ma", "ま", arrayOf("ま", "み", "む", "め", "も")),
        RowDef("ya", "や", arrayOf("や", null, "ゆ", null, "よ")),
        RowDef("ra", "ら", arrayOf("ら", "り", "る", "れ", "ろ")),
        RowDef("wa", "わ", arrayOf("わ", null, "を", null, "ん"))
    )

    // First COL_SPLIT rows (あかさたな) sit in the left sub-column (portrait)
    // or top row (landscape, read right-to-left); the rest (はまやらわ) in
    // the other sub-column / bottom row.
    const val COL_SPLIT = 5

    val SMALL_MAP = mapOf(
        "あ" to "ぁ", "い" to "ぃ", "う" to "ぅ", "え" to "ぇ", "お" to "ぉ",
        "や" to "ゃ", "ゆ" to "ゅ", "よ" to "ょ", "つ" to "っ"
    )

    fun getRow(key: String): RowDef = ROWS.first { it.key == key }

    fun rightChar(row: RowDef, i: Int): String? {
        if (row.key == "a" && i == 2) return "ぅ"
        row.dakKana?.get(i)?.let { return it }
        val base = row.kana[i] ?: return null
        return SMALL_MAP[base] ?: base
    }

    fun leftChar(row: RowDef, i: Int): String? {
        if (row.key == "a" && i == 2) return "ゔ"
        row.handakKana?.get(i)?.let { return it }
        val base = row.kana[i] ?: return null
        if (row.dakKana?.get(i) != null) {
            SMALL_MAP[base]?.let { return it }
        }
        return base
    }
}
