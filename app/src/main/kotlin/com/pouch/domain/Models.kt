package com.pouch.domain

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// money is always whole cents (Long) to avoid rounding errors

/** Account profile used for friend search and group membership. */
@Serializable
@IgnoreExtraProperties
data class User(
    val uid: String = "",
    val fullName: String = "",
    val email: String = "",
    val photoUrl: String = ""
)

/**
 * Lifecycle of a friend request. @PropertyName keeps the lowercase values
 * already stored in Firestore (and checked by security rules).
 */
enum class FriendRequestStatus(val value: String) {
    @PropertyName("pending") PENDING("pending"),
    @PropertyName("accepted") ACCEPTED("accepted"),
    @PropertyName("rejected") REJECTED("rejected"),
}

/** Stored friendship invitation between two Firebase accounts. */
data class FriendRequest(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val createdAt: Long = 0L,
    val status: FriendRequestStatus = FriendRequestStatus.PENDING,
)

/**
 * Icons a group can have. The emoji is the stored value (Firestore and the
 * local JSON file), so existing groups keep loading.
 */
@Serializable
enum class GroupIcon(val emoji: String) {
    @SerialName("🏠") @PropertyName("🏠") HOME("🏠"),
    @SerialName("✈️") @PropertyName("✈️") TRAVEL("✈️"),
    @SerialName("🍕") @PropertyName("🍕") FOOD("🍕"),
    @SerialName("🛒") @PropertyName("🛒") GROCERIES("🛒"),
    @SerialName("🎉") @PropertyName("🎉") PARTY("🎉"),
    @SerialName("🚗") @PropertyName("🚗") CAR("🚗"),
    @SerialName("🏕️") @PropertyName("🏕️") CAMPING("🏕️"),
    @SerialName("💼") @PropertyName("💼") WORK("💼"),
}

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

/** How much one member paid for, or owes on, a receipt. */
@Serializable
data class MemberAmount(
    val memberId: String = "",
    val amountCents: Long = 0L
)

@Serializable
enum class SplitMode {
    EQUAL,
    CUSTOM
}

@Serializable
data class Receipt(
    val id: String = "",
    val groupId: String = "",
    val title: String = "",
    val totalCents: Long = 0L,
    /** First member in [payments]; kept for display and for receipts saved before [payments] existed. */
    val paidByMemberId: String = "",
    val createdAt: Long = 0L,

    // Who paid and who owes. Each list adds up to totalCents. Empty = an older receipt,
    // see SplitCalculator.effectivePayments / effectiveShares.
    val payments: List<MemberAmount> = emptyList(),
    val shares: List<MemberAmount> = emptyList(),

    // Split configuration
    val splitMode: SplitMode = SplitMode.EQUAL,

    // Dates
    val modifiedAt: Long = createdAt,
    val receiptDate: Long? = null,

    // Line items for per-item splitting (not used yet)
    val items: List<ReceiptItem> = emptyList(),
)

@Serializable
data class Group(
    val id: String = "",
    val createdByUserId: String = "",
    val name: String = "",
    val icon: GroupIcon = GroupIcon.HOME,
    val createdAt: Long = 0L,
    val modifiedAt: Long = createdAt,
    val members: List<Member> = emptyList(),
    val receipts: List<Receipt> = emptyList(),
    val memberUserIds: List<String> = emptyList(),
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
