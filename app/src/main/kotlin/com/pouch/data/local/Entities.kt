package com.pouch.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room SQLite Tables for Pouch.
 *
 * Each @Entity represents a table in the local SQLite database.
 */

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String,
    val createdAt: Long,
)

@Entity(
    tableName = "members",
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
data class MemberEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val name: String,
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
    val title: String,
    val totalCents: Long,
    val paidByMemberId: String,
    val createdAt: Long,
)
