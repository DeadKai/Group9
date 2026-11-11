package com.pouch.data.repository

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionRepository(context: Context) {

    private val prefs = context.getSharedPreferences("pouch_session_prefs", Context.MODE_PRIVATE)

    private val _isLoggedIn = MutableStateFlow(prefs.getBoolean(KEY_IS_LOGGED_IN, false))
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _userEmail = MutableStateFlow(prefs.getString(KEY_USER_EMAIL, "") ?: "")
    val userEmail: StateFlow<String> = _userEmail.asStateFlow()

    fun login(email: String) {
        prefs.edit {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_USER_EMAIL, email)
        }
        _isLoggedIn.value = true
        _userEmail.value = email
    }

    fun logout() {
        prefs.edit {
            putBoolean(KEY_IS_LOGGED_IN, false)
            putString(KEY_USER_EMAIL, "")
        }
        _isLoggedIn.value = false
        _userEmail.value = ""
    }

    companion object {
        private const val KEY_IS_LOGGED_IN = "key_is_logged_in"
        private const val KEY_USER_EMAIL = "key_user_email"
    }
}
