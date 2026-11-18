package com.pouch.data

import com.google.firebase.firestore.FirebaseFirestore
import com.pouch.domain.Group
import com.pouch.domain.Member
import com.pouch.domain.Receipt
import com.pouch.domain.MemberAmount
import com.pouch.domain.SplitMode
import com.pouch.domain.SplitCalculator
import com.pouch.domain.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
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

/** Keeps signed-in groups in Firestore and demo groups on the device. */
class GroupRepository(
    private val file: File,
    private val auth: AuthRepository,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val groupsCollection = firestore.collection("groups")

    private val state = MutableStateFlow<List<Group>?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val groups: Flow<List<Group>> = auth.currentUser
        .map { it?.uid }
        .distinctUntilChanged()
        .flatMapLatest { uid ->
            if (uid == null) {
                state.onStart { ensureLoaded() }.filterNotNull()
            } else {
                observeAccountGroups(uid)
            }
        }
        .map { list -> list.sortedByDescending { it.createdAt } }

    /** Listens only to groups the signed-in account belongs to. */
    private fun observeAccountGroups(uid: String): Flow<List<Group>> = callbackFlow {
        val listener = groupsCollection.whereArrayContains("memberUserIds", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else if (snapshot != null) {
                    try {
                        trySend(snapshot.toObjects(Group::class.java))
                    } catch (failure: RuntimeException) {
                        close(failure)
                    }
                }
            }
        awaitClose { listener.remove() }
    }

    fun observeGroup(groupId: String): Flow<Group?> =
        groups.map { list -> list.firstOrNull { it.id == groupId } }

    suspend fun createGroup(name: String, icon: String, otherMembers: List<String>): String {
        val user = auth.user()
        val owner = Member(
            id = newId(),
            name = user?.displayName?.takeIf { it.isNotBlank() } ?: if (user == null) "You" else "Group owner",
            userId = user?.uid,
        )
        val group = Group(
            id = newId(),
            createdByUserId = user?.uid.orEmpty(),
            name = name.trim(),
            icon = icon,
            createdAt = System.currentTimeMillis(),
            members = listOf(owner) + otherMembers.map { Member(newId(), it.trim()) },
            memberUserIds = listOfNotNull(user?.uid),
        )
        if (user == null) update { it + group }
        else groupsCollection.document(group.id).set(group).await()
        return group.id
    }

    /** Checks membership-management privileges for the current account or a demo group. */
    fun canManage(group: Group): Boolean = auth.user()?.uid.orEmpty() == group.createdByUserId

    /** Indicates whether a group can be shared using the signed-in account. */
    fun hasAccount(): Boolean = auth.user() != null

    /** Returns the current account's member ID for the default receipt payer. */
    fun currentMemberId(group: Group): String? {
        val uid = auth.user()?.uid ?: return group.members.firstOrNull()?.id
        return group.members.firstOrNull { it.userId == uid }?.id
    }

    /** Adds a manually entered participant without granting account access. */
    suspend fun addMember(groupId: String, name: String) = updateGroup(groupId) { group ->
        require(canManage(group)) { "Only the group owner can add members" }
        val trimmed = name.trim()
        require(trimmed.length in 1..40 && group.members.none { it.name.equals(trimmed, ignoreCase = true) }) {
            "Enter a unique member name of up to 40 characters"
        }
        group.withNewMember(Member(newId(), trimmed))
    }

    /** Gives a selected friend access without assigning them another participant's expenses. */
    suspend fun addFriend(groupId: String, friend: User) = updateGroup(groupId) { group ->
        require(group.createdByUserId.isNotEmpty() && canManage(group)) {
            "Only the group owner can share this group"
        }
        require(friend.uid.isNotBlank() && friend.uid !in group.memberUserIds) {
            "This account is already in the group"
        }
        val name = friend.fullName.ifBlank { friend.username.ifBlank { friend.email } }
        require(name.isNotBlank()) { "This friend has no display name" }
        val memberId = "account:${friend.uid}"
        val existing = group.members.firstOrNull { it.id == memberId }
        val updated = if (existing == null) {
            group.withNewMember(Member(memberId, name, friend.uid))
        } else {
            group.copy(members = group.members.map {
                if (it.id == memberId) it.copy(userId = friend.uid) else it
            })
        }
        updated.copy(memberUserIds = group.memberUserIds + friend.uid)
    }

    /** Preserves older equal splits before changing the set of participants. */
    private fun Group.withNewMember(member: Member): Group = copy(
        members = members + member,
        receipts = receipts.map { receipt ->
            if (receipt.shares.isEmpty()) receipt.copy(shares = SplitCalculator.effectiveShares(receipt, members))
            else receipt
        },
    )

    suspend fun removeMember(groupId: String, memberId: String) = updateGroup(groupId) { group ->
        require(canManage(group)) { "Only the group owner can remove members" }
        val member = group.members.first { it.id == memberId }
        require(memberId != group.members.first().id) { "The group owner cannot be removed" }
        require(group.createdByUserId.isNotEmpty() || group.members.size > 2) {
            "A group needs at least two members"
        }
        val hasExpenses = group.receipts.any { receipt ->
            SplitCalculator.effectivePayments(receipt).any { it.memberId == memberId } ||
                SplitCalculator.effectiveShares(receipt, group.members).any { it.memberId == memberId }
        }
        require(!hasExpenses || member.userId != null) { "This member is involved in an existing receipt" }
        // Keep paid and owed amounts attached to the same participant after revoking access.
        val remainingMembers = if (hasExpenses) group.members.map {
            if (it.id == memberId) it.copy(userId = null) else it
        } else group.members.filterNot { it.id == memberId }
        group.copy(
            members = remainingMembers,
            memberUserIds = group.memberUserIds.filterNot { it == member.userId },
        )
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
    suspend fun deleteGroup(groupId: String) {
        if (auth.user() == null) {
            update { list -> list.filterNot { it.id == groupId } }
        } else {
            groupsCollection.document(groupId).delete().await()
        }
    }
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

    private fun newId() = UUID.randomUUID().toString()

    /** Applies cloud edits against the latest group so simultaneous edits can be retried. */
    private suspend fun updateGroup(groupId: String, change: (Group) -> Group) {
        val uid = auth.user()?.uid
        if (uid == null) {
            update { list ->
                check(list.any { it.id == groupId }) { "This group no longer exists" }
                list.map { group -> if (group.id == groupId) change(group) else group }
            }
            return
        }
        val ref = groupsCollection.document(groupId)
        firestore.runTransaction { transaction ->
            check(auth.user()?.uid == uid) { "The signed-in account changed" }
            val group = transaction.get(ref).toObject(Group::class.java)
                ?: error("This group no longer exists")
            check(uid in group.memberUserIds) { "You no longer have access to this group" }
            transaction.set(ref, change(group))
            Unit
        }.await()
    }


    private suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        mutex.withLock { loadLocked() }
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
