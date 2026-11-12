package com.pouch.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
@Dao
interface PouchDao {

    // user

    @Upsert
    suspend fun upsertUser(user: User)

    @Query("SELECT * FROM users WHERE uid = :uid")
    fun getUserById(uid: String): Flow<User?>

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): User?

    // friendship

    @Upsert
    suspend fun upsertFriendship(friendship: FriendshipEntity)

    @Query("DELETE FROM friendship WHERE id = :friendshipId")
    suspend fun deleteFriendship(friendshipId: String)

    @Query(
        """
    SELECT DISTINCT users.* FROM users
    INNER JOIN friendship ON (users.uid = friendship.friendUserId OR users.uid = friendship.userId)
    WHERE (friendship.userId = :userId OR friendship.friendUserId = :userId)
      AND users.uid != :userId
      AND friendship.status = 'accepted'
    """
    )
    fun getFriends(userId: String): Flow<List<User>>

    // group(s) and member(s)

    @Upsert
    suspend fun upsertGroup(group: GroupEntity)

    @Upsert
    suspend fun upsertMember(member: MemberEntity)

    @Upsert
    suspend fun upsertMembers(members: List<MemberEntity>)

    @Transaction
    suspend fun createGroupWithMembers(group: GroupEntity, members: List<MemberEntity>) {
        upsertGroup(group)
        upsertMembers(members)
    }

    @Query("SELECT * FROM expense_groups ORDER BY modifiedAt DESC")
    fun getAllGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM expense_groups WHERE id = :groupId LIMIT 1")
    fun getGroupById(groupId: String): Flow<GroupEntity?>

    @Query("DELETE FROM expense_groups WHERE id = :groupId")
    suspend fun deleteGroup(groupId: String)

    @Query("SELECT * FROM members WHERE groupId = :groupId AND isActive = 1")
    fun getActiveGroupMembers(groupId: String): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members WHERE groupId = :groupId")
    fun getAllGroupMembers(groupId: String): Flow<List<MemberEntity>>

    // use it after deleting a member so the receipts stay accurate
    @Query("UPDATE members SET isActive = 0 WHERE id = :memberId")
    suspend fun deactivateMember(memberId: String)

    // receipts

    @Query("SELECT * FROM receipts WHERE groupId = :groupId ORDER BY COALESCE(receiptDate, createdAt) DESC, createdAt DESC")
    fun getReceiptsForGroup(groupId: String): Flow<List<ReceiptEntity>>

    @Transaction
    @Query("SELECT * FROM receipts WHERE id = :receiptId LIMIT 1")
    fun getFullReceipt(receiptId: String): Flow<FullReceiptDetails?>

    @Upsert
    suspend fun upsertReceipt(receipt: ReceiptEntity)

    @Upsert
    suspend fun insertReceiptItems(items: List<ReceiptItemEntity>)

    @Upsert
    suspend fun insertItemAssignees(assignees: List<ReceiptItemAssigneeEntity>)

    @Upsert
    suspend fun insertReceiptPayers(payers: List<ReceiptPayerEntity>)

    @Upsert
    suspend fun insertReceiptSplits(splits: List<ReceiptSplitEntity>)

    @Query("DELETE FROM receipt_item_assignees WHERE receiptItemId IN (SELECT id FROM receipt_items WHERE receiptId = :receiptId)")
    suspend fun deleteItemAssigneesForReceipt(receiptId: String)

    @Query("DELETE FROM receipt_items WHERE receiptId = :receiptId")
    suspend fun deleteItemsForReceipt(receiptId: String)

    @Query("DELETE FROM receipt_payers WHERE receiptId = :receiptId")
    suspend fun deletePayersForReceipt(receiptId: String)

    @Query("DELETE FROM receipt_splits WHERE receiptId = :receiptId")
    suspend fun deleteSplitsForReceipt(receiptId: String)

    @Query("DELETE FROM receipts WHERE id = :receiptId")
    suspend fun deleteReceipt(receiptId: String)

    // use this single function to BOTH create a new receipt AND save edits to an existing receipt
    @Transaction
    suspend fun saveOrUpdateFullReceipt(
        receipt: ReceiptEntity,
        items: List<ReceiptItemEntity>,
        assignees: List<ReceiptItemAssigneeEntity>,
        payers: List<ReceiptPayerEntity>,
        splits: List<ReceiptSplitEntity>
    ) {
        upsertReceipt(receipt)

        // Clear old child rows if editing an existing receipt
        deleteItemAssigneesForReceipt(receipt.id)
        deleteItemsForReceipt(receipt.id)
        deletePayersForReceipt(receipt.id)
        deleteSplitsForReceipt(receipt.id)

        // Insert fresh state
        insertReceiptItems(items)
        insertItemAssignees(assignees)
        insertReceiptPayers(payers)
        insertReceiptSplits(splits)
    }

}
