package com.pouch.data

import com.google.firebase.firestore.FirebaseFirestore
import com.pouch.domain.Group
import com.pouch.domain.Member
import com.pouch.domain.Receipt
import com.pouch.domain.MemberAmount
import com.pouch.domain.SplitMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
private data class PouchData(val groups: List<Group> = emptyList())

class GroupRepository(
    private val file: File,
    firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val groupsCollection = firestore.collection("groups")

    private val state = MutableStateFlow<List<Group>?>(null)

    val groups: Flow<List<Group>> = state
        .onStart { ensureLoaded() }
        .filterNotNull()
        .map { list -> list.sortedByDescending { it.createdAt } }

    fun observeGroup(groupId: String): Flow<Group?> =
        groups.map { list -> list.firstOrNull { it.id == groupId } }

    suspend fun createGroup(name: String, icon: String, otherMembers: List<String>): String {
        val group = Group(
            id = newId(),
            name = name.trim(),
            icon = icon,
            createdAt = System.currentTimeMillis(),
            members = (listOf("You") + otherMembers).map { Member(newId(), it.trim()) },
        )
        update { it + group }
        saveGroupToCloud(group)
        return group.id
    }

    suspend fun addMember(groupId: String, name: String) = updateGroup(groupId) { group ->
        group.copy(members = group.members + Member(newId(), name.trim()))
    }

    suspend fun removeMember(groupId: String, memberId: String) = updateGroup(groupId) { group ->
        group.copy(members = group.members.filterNot { it.id == memberId })
    }

    suspend fun addReceipt(groupId: String, title: String, totalCents: Long, payments: List<MemberAmount>, shares: List<MemberAmount>, splitMode: SplitMode) = updateGroup(groupId) { group ->

        require(title.isNotBlank() && title.length <= 60)
        require(totalCents > 0)

        val memberIds = group.members.map { it.id }.toSet()

        fun valid(entries: List<MemberAmount>): Boolean =
            entries.isNotEmpty() &&
                    entries.all {
                        it.memberId in memberIds &&
                                it.amountCents >= 0
                    } &&
                    entries.map { it.memberId }.distinct().size ==
                    entries.size &&
                    entries.sumOf { it.amountCents } == totalCents

        require(valid(payments)) {
            "Payments must add up to the receipt total"
        }

        require(valid(shares)) {
            "Shares must add up to the receipt total"
        }

        val receipt = Receipt(
            id = newId(),
            groupId = groupId,
            title = title.trim(),
            totalCents = totalCents,
            paidByMemberId = payments.first {
                it.amountCents > 0
            }.memberId,
            createdAt = System.currentTimeMillis(),
            payments = payments.filter { it.amountCents > 0 },
            shares = shares.filter { it.amountCents > 0 },
            splitMode = splitMode
        )

        group.copy(
            receipts = listOf(receipt) + group.receipts
        )
    }
    suspend fun addReceipt(
        groupId: String,
        title: String,
        totalCents: Long,
        paidByMemberId: String
    ) =
        updateGroup(groupId) { group ->
            val receipt = Receipt(
                id = newId(),
                groupId = groupId,
                title = title.trim(),
                totalCents = totalCents,
                paidByMemberId = paidByMemberId,
                createdAt = System.currentTimeMillis(),
            )
            group.copy(receipts = listOf(receipt) + group.receipts)
        }

    suspend fun deleteReceipt(groupId: String, receiptId: String) = updateGroup(groupId) { group ->
        group.copy(receipts = group.receipts.filterNot { it.id == receiptId })
    }
    suspend fun deleteGroup(groupId: String) =
        update { list -> list.filterNot { it.id == groupId } }
    suspend fun updateReceipt(groupId: String, receiptId: String, title: String,totalCents: Long, payments: List<MemberAmount>, shares: List<MemberAmount>, splitMode: SplitMode) = updateGroup(groupId) { group ->

        require(title.isNotBlank() && title.length <= 60)
        require(totalCents > 0)
        require(group.receipts.any { it.id == receiptId })

        val memberIds = group.members.map { it.id }.toSet()

        fun valid(entries: List<MemberAmount>): Boolean =
            entries.isNotEmpty() &&
                    entries.all {
                        it.memberId in memberIds &&
                                it.amountCents >= 0
                    } &&
                    entries.map { it.memberId }.distinct().size ==
                    entries.size &&
                    entries.sumOf { it.amountCents } == totalCents

        require(valid(payments))
        require(valid(shares))

        group.copy(
            receipts = group.receipts.map { receipt ->
                if (receipt.id == receiptId) {
                    receipt.copy(
                        title = title.trim(),
                        totalCents = totalCents,
                        paidByMemberId = payments.first {
                            it.amountCents > 0
                        }.memberId,
                        payments = payments.filter {
                            it.amountCents > 0
                        },
                        shares = shares.filter {
                            it.amountCents > 0
                        },
                        splitMode = splitMode
                    )
                } else {
                    receipt
                }
            }
        )
    }

    private fun saveGroupToCloud(group: Group) {
        groupsCollection.document(group.id).set(group)
    }

    suspend fun syncFromCloud() {
        try {
            val snapshot = groupsCollection.get().await()
            val cloudGroups = snapshot.toObjects(Group::class.java)
            if (cloudGroups.isNotEmpty()) {
                update { cloudGroups }
            }
        } catch (e: Exception) {
            // offline or network error: keep using the local JSON data
        }
    }

    private fun newId() = UUID.randomUUID().toString()

    private suspend fun updateGroup(groupId: String, change: (Group) -> Group) {
        update { list ->
            list.map { group ->
                if (group.id == groupId) {
                    val updated = change(group)
                    saveGroupToCloud(updated)
                    updated
                } else {
                    group
                }
            }
        }
    }


    private suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        mutex.withLock { loadLocked() }
        syncFromCloud()
    }

    private fun loadLocked() {
        if (state.value != null) return
        state.value = try {
            if (file.exists()) json.decodeFromString<PouchData>(file.readText()).groups else emptyList()
        } catch (e: Exception) {
            file.copyTo(File(file.parentFile, "${file.name}.broken"), overwrite = true)
            emptyList()
        }
    }

    private suspend fun update(change: (List<Group>) -> List<Group>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadLocked()
            val newList = change(state.value.orEmpty())
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(json.encodeToString(PouchData.serializer(), PouchData(newList)))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
            state.value = newList
        }
    }
}
