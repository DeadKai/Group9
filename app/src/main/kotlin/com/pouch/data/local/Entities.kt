package com.pouch.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Each @Entity represents a table in the local database.
 */

@Entity(tableName = "users")
data class User(
    @PrimaryKey val uid: String,
    @ColumnInfo(name = "full_name") val fullName: String,
    @ColumnInfo(name = "email") val email: String,
)


@Entity(
    tableName = "groups",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["uid"],
            childColumns = ["created_by_user_id"],
            onDelete = ForeignKey.NO_ACTION,
        )
    ],
    indices = [Index("created_by_user_id")],
)
data class GroupEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "created_by_user_id") val createdByUserId: String,
    @ColumnInfo(name = "group_name") val name: String,
    @ColumnInfo(name = "group_icon_id") val icon: String,
    @ColumnInfo(name = "group_color") val color: String,
    @ColumnInfo(name = "group_created_at_timestamp") val createdAt: Long,
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
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "user_id") val userId: String?,
    @ColumnInfo(name = "member_name") val name: String,
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
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "expense_name") val expenseName: String,
    @ColumnInfo(name = "receipt_total_amount_cents") val totalCents: Long,
    @ColumnInfo(name = "paid_by_member_id") val paidByMemberId: String,
    @ColumnInfo(name = "receipt_created_at_timestamp") val createdAt: Long,
)
