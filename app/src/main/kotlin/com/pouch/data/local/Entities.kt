package com.pouch.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

// user login

@Entity(
    tableName = "users",
    indices = [Index(value = ["email"], unique = true)]
)
data class User(
    @PrimaryKey val uid: String,
    val fullName: String,
    val email: String,
)

@Entity(
    tableName = "expense_groups",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["uid"],
            childColumns = ["createdByUserId"],
            onDelete = ForeignKey.NO_ACTION,
        )
    ],
    indices = [Index("createdByUserId")],
)
data class GroupEntity(
    @PrimaryKey val id: String,
    val createdByUserId: String,
    val name: String,
    val icon: String, // emoji
    val color: String,
    val createdAt: Long,
    val modifiedAt: Long = createdAt,
)

@Entity(
    tableName = "members",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = User::class,
            parentColumns = ["uid"],
            childColumns = ["userId"],
            onDelete = ForeignKey.SET_NULL,
        )
    ],
    indices = [
        Index("groupId"),
        Index("userId"),
    ],
)
data class MemberEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val userId: String?,
    val name: String,
    val isActive: Boolean = true, // to preserve historical expense records
)

@Entity(
    tableName = "friendship",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["uid"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = User::class,
            parentColumns = ["uid"],
            childColumns = ["friendUserId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["userId", "friendUserId"], unique = true),
        Index("friendUserId"),
    ],
)
data class FriendshipEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val friendUserId: String,
    val status: String,
    val createdAt: Long,
)

@Entity(
    tableName = "receipts",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("groupId")],
)
data class ReceiptEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val receiptName: String,
    val totalCents: Long,
    val splitMethod: String, // "equal", "custom", "by_item"
    val createdAt: Long, // in-app creation timestamp
    val modifiedAt: Long = createdAt,
    val receiptDate: Long?, // actual date on the receipt
)

@Entity(
    tableName = "receipt_items",
    foreignKeys = [
        ForeignKey(
            entity = ReceiptEntity::class,
            parentColumns = ["id"],
            childColumns = ["receiptId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("receiptId")],
)
data class ReceiptItemEntity(
    @PrimaryKey val id: String,
    val receiptId: String,
    val name: String,
    val totalCents: Long,
)

// table linking a ReceiptItemEntity to the MemberEntities who shared it.

@Entity(
    tableName = "receipt_item_assignees",
    primaryKeys = ["receiptItemId", "memberId"],
    foreignKeys = [
        ForeignKey(
            entity = ReceiptItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["receiptItemId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["memberId"],
            onDelete = ForeignKey.NO_ACTION,
        )
    ],
    indices = [
        Index("receiptItemId"),
        Index("memberId"),
    ],
)
data class ReceiptItemAssigneeEntity(
    val receiptItemId: String,
    val memberId: String,
)

// who paid at the register for a given receipt.

@Entity(
    tableName = "receipt_payers",
    foreignKeys = [
        ForeignKey(
            entity = ReceiptEntity::class,
            parentColumns = ["id"],
            childColumns = ["receiptId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["memberId"],
            onDelete = ForeignKey.NO_ACTION,
        )
    ],
    indices = [
        Index(value = ["receiptId", "memberId"], unique = true),
        Index("memberId"),
    ],
)
data class ReceiptPayerEntity(
    @PrimaryKey val id: String,
    val receiptId: String,
    val memberId: String,
    val amountPaidCents: Long,
)

// final calculated share each participating member owes for a receipt

@Entity(
    tableName = "receipt_splits",
    foreignKeys = [
        ForeignKey(
            entity = ReceiptEntity::class,
            parentColumns = ["id"],
            childColumns = ["receiptId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["memberId"],
            onDelete = ForeignKey.NO_ACTION,
        )
    ],
    indices = [
        Index(value = ["receiptId", "memberId"], unique = true),
        Index("memberId"),
    ],
)
data class ReceiptSplitEntity(
    @PrimaryKey val id: String,
    val receiptId: String,
    val memberId: String,
    val amountOwedCents: Long,
)


// join the receipt item with a member

data class ReceiptItemWithAssignees(
    @Embedded val item: ReceiptItemEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ReceiptItemAssigneeEntity::class,
            parentColumn = "receiptItemId",
            entityColumn = "memberId"
        )
    )
    val assignedMembers: List<MemberEntity>
)

// with one request get the whole receipt information

data class FullReceiptDetails(
    @Embedded val receipt: ReceiptEntity,
    @Relation(
        entity = ReceiptItemEntity::class,
        parentColumn = "id",
        entityColumn = "receiptId"
    )
    val items: List<ReceiptItemWithAssignees>,
    @Relation(
        parentColumn = "id",
        entityColumn = "receiptId"
    )
    val payers: List<ReceiptPayerEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "receiptId"
    )
    val splits: List<ReceiptSplitEntity>
)

data class MemberNetBalance(
    val memberId: String,
    val memberName: String,
    val netBalanceCents: Long
)