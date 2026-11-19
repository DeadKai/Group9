package com.pouch.ui.auth

import android.util.Log
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
import com.pouch.data.UserRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** Manages authentication and registers signed-in accounts for friend search. */
class AuthViewModel(
    private val authRepository: AuthRepository,
    private val users: UserRepository = UserRepository(),
) : ViewModel() {
    val currentUser: StateFlow<FirebaseUser?> = authRepository.currentUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), authRepository.user())

    /** True while a sign-in or sign-up request is running. */
    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                syncSearchProfile(authRepository.signIn(email, password))
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Sign-in failed"
            } finally {
                isLoading = false
            }
        }
    }

    fun signUp(fullName: String, email: String, password: String) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                syncSearchProfile(authRepository.signUp(fullName, email, password))
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Sign-up failed"
            } finally {
                isLoading = false
            }
        }
    }

    /** Registers the account for friend search without delaying sign-in. */
    private fun syncSearchProfile(user: FirebaseUser) {
        viewModelScope.launch {
            try {
                users.ensureProfile(user)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("PouchAuth", "Could not sync profile after sign-in", e)
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
