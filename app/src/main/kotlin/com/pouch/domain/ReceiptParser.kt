package com.pouch.domain

/**
 * Turns OCR text lines into a suggested title + total. Pure Kotlin so it is unit tested.
 * The user always checks and can correct the result before saving (Stage 2 decision).
 */
object ReceiptParser {

    /** One line of recognised text and its vertical position on the photo (pixels). */
    data class Line(val text: String, val top: Int = 0, val bottom: Int = 0)

    data class Result(val title: String?, val totalCents: Long?)

    // Estonian + English words that usually sit next to the amount to pay.
    private val TOTAL_WORDS = listOf(
        "kokku", "summa", "tasuda", "maksta", "total", "amount due", "to pay", "sum", "grand total",
    )
    private val IGNORE_WORDS = listOf("km", "käibemaks", "vat", "tax", "subtotal", "vahesumma", "tagasi", "change")

    // 12.50 / 12,50 / 1 234,50 (but not dates like 12.11.2026)
    private val AMOUNT = Regex("""(?<![\d.,])(\d{1,3}(?:[  ]?\d{3})*|\d+)[.,](\d{2})(?![\d.,])""")

    fun parse(lines: List<Line>): Result {
        val clean = lines.filter { it.text.isNotBlank() }
        return Result(title = findTitle(clean), totalCents = findTotal(clean))
    }

    fun amountsIn(text: String): List<Long> = AMOUNT.findAll(text).map { m ->
        val whole = m.groupValues[1].replace(" ", "").replace(" ", "")
        whole.toLong() * 100 + m.groupValues[2].toLong()
    }.toList()

    private fun findTotal(lines: List<Line>): Long? {
        for (line in lines) {
            val lower = line.text.lowercase()
            val isTotal = TOTAL_WORDS.any { it in lower } && IGNORE_WORDS.none { it in lower }
            if (!isTotal) continue

            // Amount on the same line ("KOKKU 12,50") ...
            amountsIn(line.text).lastOrNull()?.let { return it }
            // ... or on a separate line at the same height (ML Kit often splits right-aligned prices).
            lines.filter { it !== line && overlapsVertically(it, line) }
                .flatMap { amountsIn(it.text) }
                .maxOrNull()
                ?.let { return it }
        }
        // Fallback: the biggest amount on the receipt is usually the total.
        return lines.flatMap { amountsIn(it.text) }.maxOrNull()
    }

    private fun findTitle(lines: List<Line>): String? =
        lines.take(5)
            .map { it.text.trim() }
            .firstOrNull { text -> text.count { it.isLetter() } >= 3 && amountsIn(text).isEmpty() }
            ?.take(60)

    private fun overlapsVertically(a: Line, b: Line): Boolean {
        if (a.bottom <= a.top || b.bottom <= b.top) return false
        return a.top < b.bottom && b.top < a.bottom
    }
}
