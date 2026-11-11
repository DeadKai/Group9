package com.pouch.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pouch.data.GroupRepository
import com.pouch.domain.Group
import com.pouch.ui.common.UiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(repository: GroupRepository) : ViewModel() {

    val state: StateFlow<UiState<List<Group>>> = repository.groups
        .map<List<Group>, UiState<List<Group>>> { groups ->
            if (groups.isEmpty()) UiState.Empty else UiState.Success(groups)
        }
        .catch { emit(UiState.Error("Couldn't load your groups")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)
}
