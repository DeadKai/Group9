package com.pouch.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.pouch.domain.User
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val users = db.collection("users")
    private val usernames = db.collection("usernames")

    fun currentUid(): String =
        auth.currentUser?.uid ?: error("Please log in first")

    suspend fun ensureProfile(): User {
        val firebaseUser = auth.currentUser ?: error("Not logged in")
        val ref = users.document(firebaseUser.uid)
        val snapshot = ref.get().await()

        if (snapshot.exists()) {
            return snapshot.toObject(User::class.java)
                ?: error("Could not read profile")
        }

        val profile = User(
            uid = firebaseUser.uid,
            fullName = firebaseUser.displayName.orEmpty(),
            email = firebaseUser.email.orEmpty(),
            photoUrl = firebaseUser.photoUrl?.toString().orEmpty()
        )
        // Merge protects an existing profile if two devices initialize it.
        ref.set(
            profile,
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
        return ref.get().await().toObject(User::class.java) ?: profile
    }

    suspend fun getUser(uid: String): User? =
        users.document(uid).get().await().toObject(User::class.java)

    suspend fun updateName(name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotBlank() && trimmed.length <= 40) {
            "Name must be 1–40 characters"
        }
        users.document(currentUid())
            .update("fullName", trimmed)
            .await()
    }

    suspend fun setUsername(value: String) {
        val username = value.trim().lowercase()
        require(username.matches(Regex("[a-z0-9_]{3,20}"))) {
            "Use 3–20 lowercase letters, numbers or underscores"
        }

        val uid = currentUid()
        val userRef = users.document(uid)
        val newRef = usernames.document(username)

        db.runTransaction { transaction ->
            val userSnap = transaction.get(userRef)
            val oldUsername =
                userSnap.getString("username").orEmpty()
            val reserved = transaction.get(newRef)

            if (reserved.exists() &&
                reserved.getString("uid") != uid
            ) {
                error("Username is already taken")
            }

            val oldRef = if (oldUsername.isNotBlank() &&
                oldUsername != username
            ) usernames.document(oldUsername) else null

            if (oldRef != null) {
                // Firestore transaction reads must precede writes.
                val oldReservation = transaction.get(oldRef)
                if (oldReservation.getString("uid") == uid) {
                    transaction.delete(oldRef)
                }
            }

            transaction.set(newRef, mapOf("uid" to uid))
            transaction.update(userRef, "username", username)
            null
        }.await()
    }

    suspend fun searchByUsername(value: String): List<User> {
        val username = value.trim().lowercase()
        if (username.isBlank()) return emptyList()

        return users
            .orderBy("username")
            .startAt(username)
            .endAt(username + "\uf8ff")
            .limit(20)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(User::class.java) }
            .filter { it.uid != auth.currentUser?.uid &&
                    it.username.isNotBlank() }
    }
}