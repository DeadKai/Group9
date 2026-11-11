package com.pouch.data

import com.pouch.domain.Group
import com.pouch.domain.Member
import com.pouch.domain.Receipt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
private data class PouchData(val groups: List<Group> = emptyList())

/**
 * Single source of truth for groups, members and receipts.
 *
 * For now the data is saved as a JSON file in the app's private storage, so the app works
 * fully offline. The rest of the app only talks to this class, so it can later be switched
 * to Room + a backend API without touching the screens.
 */
class GroupRepository(private val file: File) {

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    /** null = not loaded from disk yet. */
    private val state = MutableStateFlow<List<Group>?>(null)

    /** All groups, newest first. Updates automatically after every change. */
    val groups: Flow<List<Group>> = state
        .onStart { ensureLoaded() }
        .filterNotNull()
        .map { list -> list.sortedByDescending { it.createdAt } }

    fun observeGroup(groupId: String): Flow<Group?> =
        groups.map { list -> list.firstOrNull { it.id == groupId } }

    /** Creates a group. "You" is always the first member. Returns the new group id. */
    suspend fun createGroup(name: String, emoji: String, otherMembers: List<String>): String {
        val group = Group(
            id = newId(),
            name = name.trim(),
            emoji = emoji,
            createdAt = System.currentTimeMillis(),
            members = (listOf("You") + otherMembers).map { Member(newId(), it.trim()) },
        )
        update { it + group }
        return group.id
    }

    suspend fun addMember(groupId: String, name: String) = updateGroup(groupId) { group ->
        group.copy(members = group.members + Member(newId(), name.trim()))
    }

    suspend fun removeMember(groupId: String, memberId: String) = updateGroup(groupId) { group ->
        group.copy(members = group.members.filterNot { it.id == memberId })
    }

    suspend fun addReceipt(groupId: String, title: String, totalCents: Long, paidByMemberId: String) =
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

    // ---- internals ----

    private fun newId() = UUID.randomUUID().toString()

    private suspend fun updateGroup(groupId: String, change: (Group) -> Group) =
        update { list -> list.map { if (it.id == groupId) change(it) else it } }

    private suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        mutex.withLock { loadLocked() }
    }

    private fun loadLocked() {
        if (state.value != null) return
        state.value = try {
            if (file.exists()) json.decodeFromString<PouchData>(file.readText()).groups else emptyList()
        } catch (e: Exception) {
            // Damaged file: keep a copy for debugging and start fresh instead of crashing.
            file.copyTo(File(file.parentFile, "${file.name}.broken"), overwrite = true)
            emptyList()
        }
    }

    private suspend fun update(change: (List<Group>) -> List<Group>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadLocked()
            val newList = change(state.value.orEmpty())
            // Write to a temp file first so a crash mid-write can't corrupt the real file.
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
