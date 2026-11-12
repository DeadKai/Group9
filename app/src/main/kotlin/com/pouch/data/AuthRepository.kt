package com.pouch.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import com.pouch.data.local.PouchDao
import com.pouch.data.local.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val pouchDao: PouchDao,
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
        saveUserToRoom(fbUser, fullName.trim())
        return fbUser
    }

    suspend fun signIn(email: String, password: String): FirebaseUser {
        val fbUser = auth.signInWithEmailAndPassword(email.trim(), password).await().user
            ?: error("Sign-in failed")

        saveUserToRoom(fbUser)
        return fbUser
    }

    suspend fun signInWithGoogle(idToken: String): FirebaseUser {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val fbUser = auth.signInWithCredential(credential).await().user
            ?: error("Google sign-in failed")

        saveUserToRoom(fbUser)
        return fbUser
    }

    fun signOut() = auth.signOut()

    private suspend fun saveUserToRoom(fbUser: FirebaseUser, nameOverride: String? = null) {
        val email = fbUser.email.orEmpty()
        val name = nameOverride ?: fbUser.displayName ?: email.substringBefore("@")

        pouchDao.upsertUser(
            User(
                uid = fbUser.uid,
                fullName = name,
                email = email
            )
        )
    }
    // FIXME: REMOVE DEV LOGIN BYPASS BEFORE FINAL SUBMISSION
    @Deprecated("Temporary dev bypass - remove before submitting!")
    suspend fun loginAsDev() {
        pouchDao.upsertUser(
            User(
                uid = "dev-user",
                fullName = "Dev User",
                email = "dev@pouch.app"
            )
        )
    }
}