package com.pouch.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SplitCalculatorTest {

    private val alex = Member("a", "Alex")
    private val sarah = Member("s", "Sarah")
    private val kim = Member("k", "Kim")

    private fun receipt(total: Long, paidBy: String) =
        Receipt(id = "r$total$paidBy", groupId = "g", title = "Test", totalCents = total,
            paidByMemberId = paidBy, createdAt = 0)

    @Test
    fun `equal shares always add up to the total`() {
        assertEquals(listOf(334L, 333L, 333L), SplitCalculator.equalShares(1000, 3))
        assertEquals(1001L, SplitCalculator.equalShares(1001, 4).sum())
    }

    @Test
    fun `payer is owed by everyone else`() {
        val balances = SplitCalculator.balances(listOf(alex, sarah, kim), listOf(receipt(3000, "s")))
        assertEquals(-1000L, balances["a"])
        assertEquals(2000L, balances["s"])
        assertEquals(-1000L, balances["k"])
        assertEquals(0L, balances.values.sum())
    }

    @Test
    fun `settlements clear all balances`() {
        val balances = SplitCalculator.balances(
            listOf(alex, sarah, kim),
            listOf(receipt(3000, "s"), receipt(1500, "a")),
        )
        val after = balances.toMutableMap()
        SplitCalculator.settlements(balances).forEach {
            after[it.fromMemberId] = after.getValue(it.fromMemberId) + it.amountCents
            after[it.toMemberId] = after.getValue(it.toMemberId) - it.amountCents
        }
        after.values.forEach { assertEquals(0L, it) }
    }
}
