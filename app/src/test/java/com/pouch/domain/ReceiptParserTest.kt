package com.pouch.domain

import com.pouch.domain.ReceiptParser.Line
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptParserTest {

    private fun total(vararg lines: String) = ReceiptParser.parse(lines.map { Line(it) }).totalCents

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
            listOf(Line("Rimi Hypermarket"), Line("Piim 1,29"), Line("Leib 2,10"), Line("KM 20% 0,57"), Line("KOKKU 3,39"))
        )
        assertEquals("Rimi Hypermarket", result.title)   // no legal-name line -> first good line
        assertEquals(339L, result.totalCents)
    }

    @Test
    fun `finds total on a separate line at the same height`() {
        val result = ReceiptParser.parse(
            listOf(Line("Pizza Place", 0, 20), Line("TOTAL", 100, 120), Line("45.80", 101, 121), Line("Cash 50.00", 140, 160))
        )
        assertEquals(4580L, result.totalCents)
    }

    // ---- Estonian Summa / Käibemaks / Kokku ----

    @Test
    fun `Kokku wins over Summa`() {
        assertEquals(1240L, total("Summa: 10,00", "Käibemaks: 2,40", "Kokku: 12,40"))
    }

    @Test
    fun `no Kokku line means Summa plus Kaibemaks`() {
        assertEquals(1240L, total("Summa: 10,00", "Käibemaks: 2,40"))
    }

    @Test
    fun `several VAT rates are added up`() {
        assertEquals(1170L, total("Summa: 10,00", "KM 9% 0,50", "KM 24% 1,20"))
    }

    @Test
    fun `Summa km-ga is the total, Summa km-ta is not`() {
        assertEquals(1240L, total("Summa km-ta 10,00", "KM 24% 2,40", "Summa km-ga 12,40"))
    }

    @Test
    fun `Kaibemaks kokku is VAT, not the total`() {
        assertEquals(1240L, total("Käibemaks kokku 2,40", "Kokku 12,40"))
    }

    @Test
    fun `card payment shows when Summa already includes VAT`() {
        assertEquals(1240L, total("Summa 12,40", "sh käibemaks 2,40", "Kaart 12,40"))
    }

    // ---- shop layouts: line order must not matter ----

    @Test
    fun `Maxima layout - Summa, KM, Kokku at the bottom`() {
        assertEquals(1240L, total("MAXIMA", "Piim 1,29", "Leib 8,71", "Summa 10,00", "KM 24% 2,40", "Kokku 12,40", "Kaart 12,40"))
    }

    @Test
    fun `Selver layout - Kokku at the top`() {
        assertEquals(1240L, total("SELVER", "Kokku 12,40", "Piim 1,29", "Leib 8,71", "Summa km-ta 10,00", "KM 24% 2,40"))
    }

    @Test
    fun `Selver layout - big Kokku at the top with the amount read as its own line`() {
        val result = ReceiptParser.parse(
            listOf(
                Line("SELVER", 0, 20),
                Line("KOKKU", 30, 60), Line("12,40", 31, 61),   // same height = same row
                Line("Piim 1,29", 80, 95), Line("Leib 8,71", 100, 115),
                Line("Summa 10,00", 120, 135), Line("Käibemaks 2,40", 140, 155),
            )
        )
        assertEquals("Selver", result.title)
        assertEquals(1240L, result.totalCents)
    }

    // ---- fallbacks ----

    @Test
    fun `falls back to the biggest amount`() {
        assertEquals(750L, total("Shop", "A 2,00", "B 7,50"))
    }

    @Test
    fun `cash given is never the total`() {
        assertEquals(210L, total("Shop", "Leib 2,10", "Sularaha 20,00"))
    }

    @Test
    fun `receipt numbers are not amounts`() {
        assertEquals(emptyList<Long>(), ReceiptParser.amountsIn("216567123678517"))
        assertEquals(emptyList<Long>(), ReceiptParser.amountsIn("Tšekk 2165671236785.17"))
        assertEquals(emptyList<Long>(), ReceiptParser.amountsIn("12345 678,50"))
        assertEquals(emptyList<Long>(), ReceiptParser.amountsIn("123456,00"))
    }

    @Test
    fun `fallback ignores receipt number, register and terminal lines`() {
        assertEquals(
            1240L,
            total("Shop", "Kviitung nr 216567123678,51", "Kassa 3 12,99", "Leib 12,40", "Terminal 1234,56"),
        )
    }

    @Test
    fun `receipt number line is never the title`() {
        val result = ReceiptParser.parse(listOf(Line("Tšekk 2165671236785"), Line("Rimi Kristiine"), Line("Kokku 5,00")))
        assertEquals("Rimi Kristiine", result.title)
    }

    // ---- shop name ----

    @Test
    fun `unknown shop - biggest text at the top wins`() {
        val result = ReceiptParser.parse(
            listOf(
                Line("Tere tulemast!", 0, 15),
                Line("PIZZA PALACE", 20, 70),          // big font
                Line("Narva mnt 5, Tallinn", 75, 90),
                Line("Kassa 2", 95, 108),
                Line("Kokku 23,50", 300, 315),
            )
        )
        assertEquals("Pizza Palace", result.title)
    }

    @Test
    fun `legal-name line gives the shop name`() {
        val result = ReceiptParser.parse(listOf(Line("AS Rimi Eesti Food", 0, 12), Line("Rimi Kristiine", 14, 30)))
        assertEquals("Rimi", result.title)
    }

    @Test
    fun `date and time at the top are not the name`() {
        val result = ReceiptParser.parse(listOf(Line("19.11.2026 14:35", 0, 30), Line("Väike Pood", 32, 80)))
        assertEquals("Väike Pood", result.title)
    }

    // ---- real receipts (text as ML Kit would read it) ----

    private fun receipt(vararg lines: String) = ReceiptParser.parse(lines.map { Line(it) })

    @Test
    fun `real Rimi receipt`() {
        val r = receipt(
            "Tark valik", "Rimi Eesti Food AS, reg. nr. 10263574", "KMKNR EE100062786",
            "Saue tee 10, Laagri, Harjumaa", "Rimi Tähe,", "Tähe 19, Tartu", "www.rimi.ee",
            "Estrella 240g 1,99 A", "Kohuke vanilli Karums 45g 0,49 A", "Protein Lab 200g 1,99 A",
            "Allah. -0,40 Uus hind 1,59", "Õllemops juustuga Wõro 500g 2,39 A",
            "Kampaania -0,40", "Oled säästnud 0,40", "KAARDIMAKSE", "VIIPEMAKSE KVIITUNG 418284",
            "KAUPMEHE ID 10263574 E53", "AEG 2026-08-18 13:22:04", "SUMMA: 6,46 EUR",
            "Sinu RIMI raha saldo 0,47", "KOKKU 6,46 EUR", "KAARDIMAKSE 6,46 EUR",
            "KM % KM-ga KM-ta KM", "A 24,00% 6,46 5,21 1,25", "KASSA: 0033 ARVE NR: 03306050",
        )
        assertEquals("Rimi", r.title)
        assertEquals(646L, r.totalCents)
    }

    @Test
    fun `real Maxima receipt`() {
        val r = receipt(
            "Maxima Eesti OÜ Reg. Nr. 10765896 Võru 55, Tartu", "Receipt nr. - 3530013010101992",
            "Õrrekanade munad M EXTRA LINE 10tk", "2,79 €", "Discount", "-0,80 €",
            "Majonees HELLMANN'S Originaal,", "855ml", "6,99 €", "Discount", "-2,20 €",
            "VAT% Without VAT VAT With VAT", "24% 11,22 € 2,69 € 13,91 €",
            "VAT 2,69 €", "Without VAT 11,22 €", "Amount: 13,91 €", "Paid 13,91 €",
            "Returned 0,00 €", "Amount of discounts obtained: 3,00 €",
        )
        assertEquals("Maxima", r.title)
        assertEquals(1391L, r.totalCents)
    }

    @Test
    fun `real Selver receipt - Kokku at the top, legal name at the bottom`() {
        val r = receipt(
            "VEERIKU SELVER", "Vitamiini 1, 51014 Tartu, tel 7336700", "Kokku 5,36 €", "Kassa SPS 5",
            "Tšeki nr 316345", "Toode Kogus Ühiku hind Kokku", "Karboniseeritud virs 1 1,59 1,59 €",
            "Kokku KM-ta 4,36 €", "KM 24% 1,00 €", "KM 0% 0,00 €", "KM kokku 1,00 €", "Kokku 5,36 €",
            "MAKSEKAART 5,36 €", "KOKKU 5,36 €", "Selver AS Pärnu mnt. 238, 11624 Tallinn",
        )
        assertEquals("Selver", r.title)
        assertEquals(536L, r.totalCents)
    }

    @Test
    fun `real Lidl receipt`() {
        val r = receipt(
            "Alati väärt.", "Lidl Eesti OÜ", "A. H. Tammsaare tee 47, Tallinn", "Reg. nr. 14131773",
            "0027547 Jäätis MINI MIX CL", "4,99 x 1 tk. 4,99 A", "KM KM-ta KM-ga",
            "A= 24% 5,31 22,10 27,41", "Tasuda 27,41", "Makstud (pangakaart) 27,41", "SUMMA: 27,41 EUR",
        )
        assertEquals("Lidl", r.title)
        assertEquals(2741L, r.totalCents)
    }

    @Test
    fun `English word as is not a company form`() {
        val r = receipt("Same price as before", "BIG BURGER", "Total 9,90")
        assertEquals("Same price as before", r.title)
    }

    @Test
    fun `nothing readable gives nulls`() {
        val result = ReceiptParser.parse(emptyList())
        assertNull(result.title)
        assertNull(result.totalCents)
    }
}
