package com.pouch.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
@Dao
interface PouchDao {

    // --- Groups ---

    @Query("SELECT * FROM `groups` ORDER BY createdAt DESC")
    fun observeAllGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM `groups` WHERE id = :groupId")
    fun observeGroupById(groupId: String): Flow<GroupEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity)

    @Query("DELETE FROM `groups` WHERE id = :groupId")
    suspend fun deleteGroup(groupId: String)

    // --- Members ---

    @Query("SELECT * FROM members WHERE groupId = :groupId")
    fun observeMembersForGroup(groupId: String): Flow<List<MemberEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<MemberEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: MemberEntity)

    @Query("DELETE FROM members WHERE id = :memberId")
    suspend fun deleteMember(memberId: String)

    // --- Receipts ---

    @Query("SELECT * FROM receipts WHERE groupId = :groupId ORDER BY createdAt DESC")
    fun observeReceiptsForGroup(groupId: String): Flow<List<ReceiptEntity>>

    @Query("SELECT * FROM receipts ORDER BY createdAt DESC")
    fun observeAllReceipts(): Flow<List<ReceiptEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipt(receipt: ReceiptEntity)

    @Query("DELETE FROM receipts WHERE id = :receiptId")
    suspend fun deleteReceipt(receiptId: String)

    // --- Transaction Helper ---

    @Transaction
    suspend fun insertGroupWithMembers(group: GroupEntity, members: List<MemberEntity>) {
        insertGroup(group)
        insertMembers(members)
    }
}
