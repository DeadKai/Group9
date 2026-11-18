package com.pouch.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pouch.data.AuthRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SettingsUiState(
    val name: String? = null,
    val email: String? = null,
)

class SettingsViewModel(authRepository: AuthRepository) : ViewModel() {

    val state: StateFlow<SettingsUiState> = authRepository.currentUser
        .map { user -> SettingsUiState(name = user?.displayName, email = user?.email) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            authRepository.user().let { SettingsUiState(it?.displayName, it?.email) },
        )
}