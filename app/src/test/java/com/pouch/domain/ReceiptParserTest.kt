package com.pouch.domain

import com.pouch.domain.ReceiptParser.Line
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptParserTest {

    @Test
    fun `reads amounts with comma or dot`() {
        assertEquals(listOf(1250L), ReceiptParser.amountsIn("Piim 12,50"))
        assertEquals(listOf(399L, 1000L), ReceiptParser.amountsIn("3.99 x 10.00"))
        assertEquals(listOf(123450L), ReceiptParser.amountsIn("1 234,50"))
    }

    @Test
    fun `ignores dates`() {
        assertEquals(emptyList<Long>(), ReceiptParser.amountsIn("11.11.2026"))
    }

    @Test
    fun `finds total on the same line`() {
        val result = ReceiptParser.parse(
            listOf(
                Line("Rimi Hypermarket"),
                Line("Piim 1,29"),
                Line("Leib 2,10"),
                Line("KM 20% 0,57"),
                Line("KOKKU 3,39"),
            )
        )
        assertEquals("Rimi Hypermarket", result.title)
        assertEquals(339L, result.totalCents)
    }

    @Test
    fun `finds total on a separate line at the same height`() {
        val result = ReceiptParser.parse(
            listOf(
                Line("Pizza Place", 0, 20),
                Line("TOTAL", 100, 120),
                Line("45.80", 101, 121),
                Line("Cash 50.00", 140, 160),
            )
        )
        assertEquals(4580L, result.totalCents)
    }

    @Test
    fun `falls back to the biggest amount`() {
        val result = ReceiptParser.parse(listOf(Line("Shop"), Line("A 2,00"), Line("B 7,50")))
        assertEquals(750L, result.totalCents)
    }

    @Test
    fun `nothing readable gives nulls`() {
        val result = ReceiptParser.parse(emptyList())
        assertNull(result.title)
        assertNull(result.totalCents)
    }
}
