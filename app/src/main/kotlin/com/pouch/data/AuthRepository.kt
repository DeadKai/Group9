package com.pouch.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    val currentUser: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    fun user(): FirebaseUser? = auth.currentUser

    suspend fun signUp(fullName: String, email: String, password: String): FirebaseUser {
        val fbUser = auth.createUserWithEmailAndPassword(email.trim(), password).await().user
            ?: error("Sign-up failed")

        fbUser.updateProfile(userProfileChangeRequest { displayName = fullName.trim() }).await()
        return fbUser
    }

    suspend fun signIn(email: String, password: String): FirebaseUser {
        return auth.signInWithEmailAndPassword(email.trim(), password).await().user
            ?: error("Sign-in failed")
    }

    fun signOut() = auth.signOut()

    // FIXME: REMOVE DEV LOGIN BYPASS BEFORE FINAL SUBMISSION
    @Deprecated("Temporary dev bypass - remove before submitting!")
    suspend fun loginAsDev() {
        // Bypass marker for AuthViewModel
    }
}