package com.pouch.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pouch.data.GroupRepository
import com.pouch.data.FriendsRepository
import com.pouch.domain.Group
import com.pouch.domain.MemberAmount
import com.pouch.domain.SplitMode
import com.pouch.domain.Member
import com.pouch.domain.Receipt
import com.pouch.domain.Settlement
import com.pouch.domain.SplitCalculator
import com.pouch.domain.User
import com.pouch.domain.ReceiptItem
import com.pouch.ui.common.SuccessController
import com.pouch.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

data class GroupDetails(
    val group: Group,
    val balances: Map<String, Long>,
    val settlements: List<Settlement>,
    val canManage: Boolean,
) {
    fun memberName(id: String) = group.members.firstOrNull { it.id == id }?.name ?: "Removed member"
}

class GroupViewModel(
    private val groupId: String,
    private val repository: GroupRepository,
    private val success: SuccessController,
    private val friendsRepository: FriendsRepository = FriendsRepository(),
) : ViewModel() {

    val state: StateFlow<UiState<GroupDetails>> = repository.observeGroup(groupId)
        .map<Group?, UiState<GroupDetails>> { group ->
            if (group == null) {
                UiState.Error("This group no longer exists")
            } else {
                val balances = SplitCalculator.balances(group.members, group.receipts)
                UiState.Success(GroupDetails(group, balances, SplitCalculator.settlements(balances), repository.canManage(group)))
            }
        }
        .catch { emit(UiState.Error("Couldn't load this group")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    /** Error / warning text for the snackbar (null = nothing to show). Successes use [success]. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun messageShown() { _message.value = null }

    private val _friends = MutableStateFlow<UiState<List<User>>>(UiState.Loading)
    val friends = _friends.asStateFlow()
    private val _sharing = MutableStateFlow(false)
    val sharing = _sharing.asStateFlow()

    /** Loads the accounts available in the friend picker. */
    fun loadFriends() {
        _friends.value = UiState.Loading
        viewModelScope.launch {
            try {
                val friends = friendsRepository.friends()
                _friends.value = if (friends.isEmpty()) UiState.Empty else UiState.Success(friends)
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                _friends.value = UiState.Error(failure.message ?: "Could not load friends")
            }
        }
    }

    /** Shares the group and closes the picker once Firestore confirms the change. */
    fun addFriend(friend: User, onAdded: () -> Unit) {
        if (_sharing.value) return
        _sharing.value = true
        viewModelScope.launch {
            try {
                repository.addFriend(groupId, friend)
                success.show("Group shared", "Your friend can now see this group")
                onAdded()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                _friends.value = UiState.Error(failure.message ?: "Could not share this group")
            } finally {
                _sharing.value = false
            }
        }
    }

    /** Returns an error text for the dialog, or null when the member was added. */
    fun addMember(name: String): String? {
        val details = (state.value as? UiState.Success)?.data ?: return "Group not loaded yet"
        val trimmed = name.trim()
        when {
            trimmed.isEmpty() -> return "Enter a name"
            trimmed.length > 40 -> return "Keep it under 40 characters"
            details.group.members.any { it.name.equals(trimmed, ignoreCase = true) } ->
                return "$trimmed is already in the group"
        }
        runGroupAction("Could not add this member") {
            repository.addMember(groupId, trimmed)
            success.show("Member added", "$trimmed joined the group")
        }
        return null
    }

    fun removeMember(member: Member) {
        val details =
            (state.value as? UiState.Success)?.data ?: return

        val group = details.group

        val involvedInReceipt = group.receipts.any { receipt ->
            SplitCalculator.effectivePayments(receipt).any {
                it.memberId == member.id
            } ||
                    SplitCalculator.effectiveShares(
                        receipt,
                        group.members
                    ).any {
                        it.memberId == member.id
                    }
        }

        when {
            group.createdByUserId.isEmpty() && group.members.size <= 2 -> {
                _message.value =
                    "A group needs at least two members"
            }

            involvedInReceipt && member.userId == null -> {
                _message.value =
                    "${member.name} is involved in an existing receipt. Delete or update those receipts first."
            }

            else -> {
                runGroupAction("Could not remove this member") {
                    repository.removeMember(groupId, member.id)
                    success.show(
                        if (member.userId != null) "Group access removed" else "Member removed",
                        if (involvedInReceipt) "Existing receipts still include ${member.name}" else null,
                    )
                }
            }
        }
    }



    fun deleteReceipt(receipt: Receipt) {
        runGroupAction("Could not delete this receipt") {
            repository.deleteReceipt(groupId, receipt.id)
            success.show("Receipt deleted", "\"${receipt.title}\" was removed")
        }
    }
    fun deleteGroup(onDeleted: () -> Unit) {
        val name = (state.value as? UiState.Success)?.data?.group?.name
        runGroupAction("Could not delete this group") {
            repository.deleteGroup(groupId)
            success.show("Group deleted", name?.let { "\"$it\" and its receipts are gone" })
            onDeleted() //navigate back only after the delete is saved
        }
    }

    fun updateReceipt(
        receipt: Receipt,
        title: String,
        amountCents: Long,
        payments: List<MemberAmount>,
        shares: List<MemberAmount>,
        splitMode: SplitMode,
        items: List<ReceiptItem>,
        onSaved: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.updateReceipt(
                    groupId = groupId,
                    receiptId = receipt.id,
                    title = title,
                    totalCents = amountCents,
                    payments = payments,
                    shares = shares,
                    splitMode = splitMode,
                    items = items
                )

                success.show(
                    "Receipt updated",
                    "\"${title.trim()}\" was saved"
                )

                onSaved()

            } catch (failure: CancellationException) {
                throw failure
            } catch (e: Exception) {
                _message.value =
                    e.message ?: "Failed to update expense"
            }
        }
    }


    /** Shows repository failures in the current group's snackbar. */
    private fun runGroupAction(errorMessage: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                _message.value = failure.message ?: errorMessage
            }
        }
    }
}
