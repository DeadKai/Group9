package com.pouch.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.auth.FirebaseUser
import com.pouch.PouchApp
import com.pouch.data.AuthRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which login action is running, so only that button shows a spinner. */
enum class AuthAction { EMAIL, GOOGLE }

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {
    val currentUser: StateFlow<FirebaseUser?> = authRepository.currentUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), authRepository.user())

    /** null = nothing is loading. */
    var loadingAction by mutableStateOf<AuthAction?>(null)
        private set

    val isLoading: Boolean
        get() = loadingAction != null

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            loadingAction = AuthAction.EMAIL
            errorMessage = null
            try {
                authRepository.signIn(email, password)
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Sign-in failed"
            } finally {
                loadingAction = null
            }
        }
    }

    fun signUp(fullName: String, email: String, password: String) {
        viewModelScope.launch {
            loadingAction = AuthAction.EMAIL
            errorMessage = null
            try {
                authRepository.signUp(fullName, email, password)
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Sign-up failed"
            } finally {
                loadingAction = null
            }
        }
    }

    fun setError(message: String?) {
        errorMessage = message
    }

    var isBypassed by mutableStateOf(false)
        private set

    // FIXME: REMOVE DEV LOGIN BYPASS BEFORE FINAL SUBMISSION
    @Deprecated("Temporary dev bypass - remove before submitting!")
    fun bypassLogin() {
        isBypassed = true
    }

    fun signOut() {
        isBypassed = false
        authRepository.signOut()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as PouchApp
                AuthViewModel(app.container.authRepository)
            }
        }
    }
}
