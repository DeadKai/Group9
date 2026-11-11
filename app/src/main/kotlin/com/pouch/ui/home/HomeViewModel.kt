package com.pouch.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pouch.data.local.dao.GroupDao
import com.pouch.data.local.entity.GroupEntity
import com.pouch.data.repository.SessionRepository
import com.pouch.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val groupDao: GroupDao,
    private val sessionRepository: SessionRepository
) : ViewModel() {

    val groupsState: StateFlow<UiState<List<GroupEntity>>> = groupDao.getGroups()
        .map { groups ->
            if (groups.isEmpty()) {
                UiState.Empty
            } else {
                UiState.Success(groups)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UiState.Loading
        )

    private val _newGroupName = MutableStateFlow("")
    val newGroupName: StateFlow<String> = _newGroupName.asStateFlow()

    fun onNewGroupNameChanged(name: String) {
        _newGroupName.value = name
    }

    fun addGroup() {
        val name = _newGroupName.value.trim()
        if (name.isNotEmpty()) {
            viewModelScope.launch {
                groupDao.insertGroup(GroupEntity(name = name))
                _newGroupName.value = ""
            }
        }
    }

    fun logout(onLogoutComplete: () -> Unit) {
        sessionRepository.logout()
        onLogoutComplete()
    }
}
