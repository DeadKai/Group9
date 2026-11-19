package com.pouch.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pouch.data.GroupRepository
import com.pouch.domain.Group
import com.pouch.domain.SplitCalculator
import com.pouch.ui.common.UiState
import com.pouch.ui.common.showErrorAndRetry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The account's positive and negative balances across its groups. */
data class HomeBalance(val owedToYou: Long, val youOwe: Long) {
    val net: Long get() = owedToYou - youOwe
}

class HomeViewModel(private val repository: GroupRepository) : ViewModel() {

    val state: StateFlow<UiState<List<Group>>> = repository.groups
        .map<List<Group>, UiState<List<Group>>> { groups ->
            if (groups.isEmpty()) UiState.Empty else UiState.Success(groups)
        }
        .showErrorAndRetry("Couldn't load your groups")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)
    /** Uses the same member selection and receipt splits as the group screens. */
    fun balanceFor(groups: List<Group>): HomeBalance {
        var owedToYou = 0L
        var youOwe = 0L
        for (group in groups) {
            val memberId = repository.currentMemberId(group) ?: continue
            val net = SplitCalculator.balances(group.members, group.receipts).getValue(memberId)
            owedToYou += net.coerceAtLeast(0L)
            youOwe += (-net).coerceAtLeast(0L)
        }
        return HomeBalance(owedToYou, youOwe)
    }
}
