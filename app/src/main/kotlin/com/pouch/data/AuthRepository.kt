package com.pouch.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Thin wrapper around Firebase Auth.
 *
 * TODO: Wire this into a LoginScreen and gate navigation in PouchNavHost.
 *       - Observe [currentUser] to decide whether to show Login or Home.
 *       - Call [signUp] / [signIn] from a LoginViewModel.
 *       - Call [signOut] from a Profile/Settings screen.
 */
class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    /** Emits the current user (or null) every time auth state changes. */
    val currentUser: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /** Returns the signed-in user, or null if not signed in. */
    fun user(): FirebaseUser? = auth.currentUser

    /** Creates a new account. Throws on failure (show the message to the user). */
    suspend fun signUp(email: String, password: String): FirebaseUser {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        return result.user ?: error("Sign-up succeeded but user is null")
    }

    /** Signs in with email + password. Throws on failure. */
    suspend fun signIn(email: String, password: String): FirebaseUser {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        return result.user ?: error("Sign-in succeeded but user is null")
    }

    /** Signs out the current user. */
    fun signOut() = auth.signOut()
}
