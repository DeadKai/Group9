package com.pouch.domain

/**
 * Turns OCR text lines into a suggested title + total. Pure Kotlin so it is unit tested.
 * The user always checks and can correct the result before saving (Stage 2 decision).
 *
 * ORDER differs per shop
 * So the parser never relies order
 * sorted into a [Kind] and the total is chosen by priority:
 *   1. a real total line (Kokku, Tasuda, Total, "Summa km-ga", ...)
 *   2. Summa + Käibemaks, when there is no total line
 *   3. Summa alone
 *   4. the biggest amount on the receipt (ignoring cash given / change and receipt numbers, codes, phone numbers)
 */
object ReceiptParser {

    /** One line of recognised text and its vertical position on the photo (pixels). */
    data class Line(val text: String, val top: Int = 0, val bottom: Int = 0)

    data class Result(val title: String?, val totalCents: Long?)

    private enum class Kind { TOTAL, SUBTOTAL, VAT, PAYMENT, ID, OTHER }

    // "with VAT" means the line IS the final amount ("Summa km-ga", "Kokku käibemaksuga").
    private val INCLUDING_VAT = listOf("km-ga", "kmga", "km ga", "käibemaksuga", "incl. vat", "incl vat")
    // "without VAT" means it's only the subtotal ("Summa km-ta").
    private val EXCLUDING_VAT = listOf("km-ta", "kmta", "km ta", "käibemaksuta", "excl. vat", "excl vat", "without vat")
    private val SUBTOTAL_WORDS = listOf("vahesumma", "subtotal", "sub-total")
    private val VAT = Regex("""käibemaks|\bkm\b|\bvat\b|\btax\b""")
    // Card / cash lines: the card amount can confirm the total, cash given must never be the total.
    private val PAYMENT_WORDS = listOf("kaart", "card", "sularaha", "cash", "tagasi", "change", "makstud", "paid")
    private val TOTAL_WORDS = listOf("kokku", "tasuda", "maksta", "total", "to pay", "amount due")
    private val SUM = Regex("""summa|\bsum\b""")
    // Receipt / register / card terminal numbers and codes. Never prices.
    private val ID_WORDS = Regex(
        """\b(nr|no|tšekk|tsekk|kviitung|arve|kassa|kassir|kmkr|reg|registrikood|tel|terminal|tehing|kood|rrn|auth|aid|id)\b"""
    )

    /** Biggest amount we believe can be on a receipt (€99 999.99). Anything bigger is a number like a receipt ID. */
    private const val MAX_CENTS = 99_999_99L
    /** "123456,78" has too many digits before the comma to be a price. */
    private const val MAX_WHOLE_DIGITS = 5

    // 12.50 / 12,50 / 1 234,50
    // Not matched: dates (12.11.2026), parts of long numbers (2165671236785.17 or "12345 678,50").
    // Still matched: column layouts like "3 1,29 3,87" (quantity, price, sum).
    private val AMOUNT = Regex("""(?<![\d.,])(?<!\d{4} )(\d{1,3}(?:[ \u00A0]\d{3})+|\d+)[.,](\d{2})(?![\d.,])""")

    fun parse(lines: List<Line>): Result {
        val clean = lines.filter { it.text.isNotBlank() }
        return Result(title = findTitle(clean), totalCents = findTotal(clean))
    }

    fun amountsIn(text: String): List<Long> = AMOUNT.findAll(text).mapNotNull { m ->
        val whole = m.groupValues[1].replace(" ", "").replace("\u00A0", "")
        if (whole.length > MAX_WHOLE_DIGITS) return@mapNotNull null
        val cents = whole.toLong() * 100 + m.groupValues[2].toLong()
        cents.takeIf { it <= MAX_CENTS }
    }.toList()

    /** The order of the checks matters: e.g. "Käibemaks kokku" is VAT, not the total. */
    private fun kindOf(text: String): Kind {
        val t = text.lowercase()
        return when {
            INCLUDING_VAT.any { it in t } -> Kind.TOTAL
            EXCLUDING_VAT.any { it in t } -> Kind.SUBTOTAL
            SUBTOTAL_WORDS.any { it in t } -> Kind.SUBTOTAL
            VAT.containsMatchIn(t) -> Kind.VAT
            PAYMENT_WORDS.any { it in t } -> Kind.PAYMENT
            TOTAL_WORDS.any { it in t } -> Kind.TOTAL
            SUM.containsMatchIn(t) -> Kind.SUBTOTAL
            ID_WORDS.containsMatchIn(t) -> Kind.ID
            else -> Kind.OTHER
        }
    }

    private fun findTotal(lines: List<Line>): Long? {
        val kinds = lines.associateWith { kindOf(it.text) }
        fun amountsOf(kind: Kind): List<Long> =
            lines.filter { kinds[it] == kind }.mapNotNull { amountFor(it, lines) }

        // 1. Explicit total line. If there are several, the final total is the biggest.
        amountsOf(Kind.TOTAL).maxOrNull()?.let { return it }

        // 2./3. Summa (+ Käibemaks).
        val subtotal = amountsOf(Kind.SUBTOTAL).maxOrNull()
        if (subtotal != null) {
            val vat = amountsOf(Kind.VAT)
            if (vat.isEmpty()) return subtotal
            val withVat = subtotal + vat.sum()
            // Some shops print "Summa" already WITH VAT. The card payment tells us which one is right.
            val paid = amountsOf(Kind.PAYMENT).toSet()
            return when {
                withVat in paid -> withVat
                subtotal in paid -> subtotal
                else -> withVat
            }
        }

        // 4. Fallback: biggest ordinary amount (not cash given, change or VAT).
        return lines.filter { kinds[it] == Kind.OTHER }.flatMap { amountsIn(it.text) }.maxOrNull()
    }

    /** Amount on the same line ("KOKKU 12,50"), or on a line at the same height
     *  (ML Kit often reads right-aligned prices as a separate line). */
    private fun amountFor(line: Line, lines: List<Line>): Long? {
        amountsIn(line.text).lastOrNull()?.let { return it }
        return lines.filter { it !== line && overlapsVertically(it, line) }
            .flatMap { amountsIn(it.text) }
            .maxOrNull()
    }

    /** Lines at the top that are never the shop name: greetings, addresses, web, date/time. */
    private val NOT_A_NAME = Regex(
        """tere tulemast|aitäh|welcome|www\.|http|@|\b(tn|mnt|pst|tee|maantee|tänav)\b|\d{2}[.:]\d{2}"""
    )

    /**
     * The shop name is usually the BIGGEST text at the very TOP of the receipt.
     * So we take the tallest text line in the top part (ML Kit gives each line's height),
     * skipping prices, totals, receipt numbers, addresses and greetings. Works for any country.
     * Without position info (heights are 0) it simply takes the first good line.
     */
    private fun findTitle(lines: List<Line>): String? {
        val top = lines.take(maxOf(6, lines.size / 3))

        return top
            .filter { looksLikeName(it.text.trim()) }
            .maxByOrNull { it.bottom - it.top }   // tallest = biggest font; ties keep the first line
            ?.text
            ?.let(::tidyName)
    }

    private fun looksLikeName(text: String): Boolean {
        val letters = text.count { it.isLetter() }
        val lower = text.lowercase()
        return letters >= 3 &&
            amountsIn(text).isEmpty() &&
            kindOf(text) == Kind.OTHER &&                  // not "KOKKU", "Summa", "Kaart", "Kassa 2", ...
            text.count { it.isDigit() } < letters &&      // not mostly digits (e.g. "Tšekk 2165671236")
            !ID_WORDS.containsMatchIn(lower) &&
            !NOT_A_NAME.containsMatchIn(lower)
    }

    /** "PIZZA PALACE" -> "Pizza Palace", max 60 characters. */
    private fun tidyName(raw: String): String {
        val text = raw.trim()
        val shouted = text.any { it.isLetter() } && text == text.uppercase()
        val tidy = if (shouted) {
            text.split(Regex("\\s+")).joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { it.uppercase() }
            }
        } else text
        return tidy.take(60)
    }

    private fun overlapsVertically(a: Line, b: Line): Boolean {
        if (a.bottom <= a.top || b.bottom <= b.top) return false
        return a.top < b.bottom && b.top < a.bottom
    }
}
