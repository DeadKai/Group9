package com.pouch.ui.login

import androidx.lifecycle.ViewModel
import com.pouch.data.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val isSubmitEnabled: Boolean = false
)

class LoginViewModel(
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChanged(email: String) {
        _uiState.update { state ->
            val error = if (email.isNotEmpty() && !email.contains("@")) {
                "Email must contain '@'"
            } else null
            val newState = state.copy(email = email, emailError = error)
            newState.copy(isSubmitEnabled = validate(newState))
        }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { state ->
            val error = if (password.isNotEmpty() && password.length < 6) {
                "Password must be at least 6 characters"
            } else null
            val newState = state.copy(password = password, passwordError = error)
            newState.copy(isSubmitEnabled = validate(newState))
        }
    }

    private fun validate(state: LoginUiState): Boolean {
        return state.email.contains("@") && state.password.length >= 6
    }

    fun login(onSuccess: () -> Unit) {
        if (validate(_uiState.value)) {
            sessionRepository.login(_uiState.value.email)
            onSuccess()
        }
    }
}
