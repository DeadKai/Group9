package com.pouch.domain

import com.google.firebase.firestore.Exclude
import kotlinx.serialization.Serializable

// money is always whole cents (Long) to avoid rounding errors

@Serializable
data class User(
    val uid: String = "",
    val fullName: String = "",
    val email: String = "",
)

@Serializable
data class Member(
    val id: String = "",
    val name: String = "",
    val userId: String? = null,
    val active: Boolean = true, // preserves receipts if a member leaves
)

@Serializable
data class ReceiptItem(
    val id: String = "",
    val name: String = "",
    val totalCents: Long = 0L,
    val assignedMemberIds: List<String> = emptyList(),
)

@Serializable
data class ReceiptPayer(
    val memberId: String = "",
    val amountPaidCents: Long = 0L,
)

@Serializable
data class ReceiptSplit(
    val memberId: String = "",
    val amountOwedCents: Long = 0L,
)

@Serializable
data class Receipt(
    val id: String = "",
    val groupId: String = "",
    val title: String = "",
    val totalCents: Long = 0L,
    val paidByMemberId: String = "",
    val splitMethod: String = "equal", // "equal", "custom", "by_item"
    val createdAt: Long = 0L,
    val modifiedAt: Long = createdAt,
    val receiptDate: Long? = null,
    val items: List<ReceiptItem> = emptyList(),
    val payers: List<ReceiptPayer> = emptyList(),
    val splits: List<ReceiptSplit> = emptyList(),
)

@Serializable
data class Group(
    val id: String = "",
    val createdByUserId: String = "",
    val name: String = "",
    val emoji: String = "🧾",
    val color: String = "#4ECDC4",
    val createdAt: Long = 0L,
    val modifiedAt: Long = createdAt,
    val members: List<Member> = emptyList(),
    val receipts: List<Receipt> = emptyList(),
) {
    @get:Exclude
    val totalSpentCents: Long get() = receipts.sumOf { it.totalCents }
}

@Serializable
data class Settlement(
    val fromMemberId: String = "",
    val toMemberId: String = "",
    val amountCents: Long = 0L,
)

fun Long.formatMoney(currency: String = "€"): String {
    val sign = if (this < 0) "-" else ""
    val abs = kotlin.math.abs(this)
    return "$sign$currency${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
}