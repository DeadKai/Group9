package com.pouch.domain

import kotlinx.serialization.Serializable

/**
 * App data. @Serializable so it can be saved as JSON on the phone (see GroupRepository).
 * Money is always whole cents (Long) to avoid rounding errors.
 */

@Serializable
data class Member(
    val id: String,
    val name: String,
)

@Serializable
data class Receipt(
    val id: String,
    val groupId: String,
    val title: String,
    val totalCents: Long,
    val paidByMemberId: String,
    val createdAt: Long,
)

@Serializable
data class Group(
    val id: String,
    val name: String,
    val emoji: String,
    val createdAt: Long,
    val members: List<Member> = emptyList(),
    val receipts: List<Receipt> = emptyList(),
) {
    val totalSpentCents: Long get() = receipts.sumOf { it.totalCents }
}

/** "from owes to amountCents" */
data class Settlement(
    val fromMemberId: String,
    val toMemberId: String,
    val amountCents: Long,
)

fun Long.formatMoney(currency: String = "€"): String {
    val sign = if (this < 0) "-" else ""
    val abs = kotlin.math.abs(this)
    return "$sign$currency${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
}
