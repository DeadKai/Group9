package com.pouch.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pouch.data.GroupRepository
import com.pouch.domain.Group
import com.pouch.domain.MemberAmount
import com.pouch.domain.SplitMode
import com.pouch.domain.Member
import com.pouch.domain.Receipt
import com.pouch.domain.Settlement
import com.pouch.domain.SplitCalculator
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

data class GroupDetails(
    val group: Group,
    val balances: Map<String, Long>,
    val settlements: List<Settlement>,
) {
    fun memberName(id: String) = group.members.firstOrNull { it.id == id }?.name ?: "Removed member"
}

class GroupViewModel(
    private val groupId: String,
    private val repository: GroupRepository,
    private val success: SuccessController,
) : ViewModel() {

    val state: StateFlow<UiState<GroupDetails>> = repository.observeGroup(groupId)
        .map<Group?, UiState<GroupDetails>> { group ->
            if (group == null) {
                UiState.Error("This group no longer exists")
            } else {
                val balances = SplitCalculator.balances(group.members, group.receipts)
                UiState.Success(GroupDetails(group, balances, SplitCalculator.settlements(balances)))
            }
        }
        .catch { emit(UiState.Error("Couldn't load this group")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    /** Error / warning text for the snackbar (null = nothing to show). Successes use [success]. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun messageShown() { _message.value = null }

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
        viewModelScope.launch {
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
            group.members.size <= 2 -> {
                _message.value =
                    "A group needs at least two members"
            }

            involvedInReceipt -> {
                _message.value =
                    "${member.name} is involved in an existing receipt. Delete or update those receipts first."
            }

            else -> {
                viewModelScope.launch {
                    repository.removeMember(groupId, member.id)
                    success.show("Member removed", "${member.name} left the group")
                }
            }
        }
    }



    fun deleteReceipt(receipt: Receipt) {
        viewModelScope.launch {
            repository.deleteReceipt(groupId, receipt.id)
            success.show("Receipt deleted", "\"${receipt.title}\" was removed")
        }
    }
    fun deleteGroup(onDeleted: () -> Unit) {
        val name = (state.value as? UiState.Success)?.data?.group?.name
        viewModelScope.launch {
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
        splitMode: SplitMode
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
                    splitMode = splitMode
                )

                success.show("Receipt updated", "\"${title.trim()}\" was saved")

            } catch (e: Exception) {
                _message.value =
                    e.message ?: "Failed to update expense"
            }
        }
    }

}
