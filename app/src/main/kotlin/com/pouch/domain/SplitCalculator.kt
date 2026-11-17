package com.pouch.domain

/**
 * Pure Kotlin split logic (no Android dependencies) so it can be unit tested.
 *
 * V1 rule: every receipt is split equally between all group members.
 * TODO(stage 3+): per-item assignment when receipt line items / OCR are added.
 */
object SplitCalculator {

    /**
     * Splits [totalCents] into [parts] shares that add up exactly to the total.
     * Leftover cents go to the first shares: 1000 / 3 -> [334, 333, 333].
     */
    fun equalShares(totalCents: Long, parts: Int): List<Long> {
        require(parts > 0) { "parts must be > 0" }
        val base = totalCents / parts
        val remainder = (totalCents % parts).toInt()
        return List(parts) { index -> if (index < remainder) base + 1 else base }
    }


    /**
     * Calculates net balance for every member.
     *
     * Positive = others owe this member.
     * Negative = this member owes others.
     *
     * Balance = amount paid - amount owed.
     */
    fun balances(
        members: List<Member>,
        receipts: List<Receipt>
    ): Map<String, Long> {

        val balance = members.associate {
            it.id to 0L
        }.toMutableMap()

        for (receipt in receipts) {

            for (payment in effectivePayments(receipt)) {
                if (payment.memberId in balance) {
                    balance[payment.memberId] =
                        balance.getValue(payment.memberId) +
                                payment.amountCents
                }
            }

            for (share in effectiveShares(receipt, members)) {
                if (share.memberId in balance) {
                    balance[share.memberId] =
                        balance.getValue(share.memberId) -
                                share.amountCents
                }
            }
        }

        return balance
    }
    fun effectivePayments(receipt: Receipt): List<MemberAmount> =
        receipt.payments.ifEmpty {
            listOf(
                MemberAmount(
                    receipt.paidByMemberId,
                    receipt.totalCents
                )
            )
        }

    fun effectiveShares(
        receipt: Receipt,
        members: List<Member>
    ): List<MemberAmount> {
        if (receipt.shares.isNotEmpty()) {
            return receipt.shares
        }

        if (members.isEmpty()) return emptyList()

        val amounts = equalShares(receipt.totalCents, members.size)

        return members.zip(amounts) { member, amount ->
            MemberAmount(member.id, amount)
        }
    }
    /**
     * Turns balances into a short list of payments ("Alex owes Sarah €14.50").
     * Greedy: biggest debtor pays biggest creditor until everyone is at 0.
     */
    fun settlements(balances: Map<String, Long>): List<Settlement> {
        val debtors = balances.filterValues { it < 0 }.map { it.key to -it.value }.toMutableList()
        val creditors = balances.filterValues { it > 0 }.map { it.key to it.value }.toMutableList()
        val result = mutableListOf<Settlement>()

        debtors.sortByDescending { it.second }
        creditors.sortByDescending { it.second }

        var d = 0
        var c = 0
        while (d < debtors.size && c < creditors.size) {
            val (debtor, owes) = debtors[d]
            val (creditor, gets) = creditors[c]
            val amount = minOf(owes, gets)
            result += Settlement(debtor, creditor, amount)
            debtors[d] = debtor to owes - amount
            creditors[c] = creditor to gets - amount
            if (debtors[d].second == 0L) d++
            if (creditors[c].second == 0L) c++
        }
        return result
    }
}
