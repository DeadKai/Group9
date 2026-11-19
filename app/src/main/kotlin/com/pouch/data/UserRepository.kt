package com.pouch.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.pouch.domain.User
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val users = db.collection("users")

    fun currentUid(): String =
        auth.currentUser?.uid ?: error("Please log in first")

    /** Creates the searchable profile or brings its email in sync with Firebase Auth. */
    suspend fun ensureProfile(firebaseUser: FirebaseUser = auth.currentUser ?: error("Not logged in")): User {
        val ref = users.document(firebaseUser.uid)
        val email = firebaseUser.email.orEmpty().trim().lowercase()
        val name = firebaseUser.displayName.orEmpty()
        val existing = ref.get().await().toObject(User::class.java)
        if (existing != null &&
            (email.isEmpty() || existing.email == email) &&
            (name.isBlank() || existing.fullName.isNotBlank())
        ) return existing

        // Sign-in and the profile screen can initialize the same account at once.
        return db.runTransaction { transaction ->
            val profile = transaction.get(ref).toObject(User::class.java)
            if (profile == null) {
                val created = User(
                    uid = firebaseUser.uid,
                    fullName = name,
                    email = email,
                    photoUrl = firebaseUser.photoUrl?.toString().orEmpty(),
                )
                transaction.set(ref, created)
                created
            } else {
                val updated = profile.copy(
                    fullName = profile.fullName.ifBlank { name },
                    email = email.ifBlank { profile.email },
                )
                val fields = buildMap<String, Any> {
                    if (profile.fullName != updated.fullName) put("fullName", updated.fullName)
                    if (profile.email != updated.email) put("email", updated.email)
                }
                if (fields.isNotEmpty()) transaction.update(ref, fields)
                updated
            }
        }.await()
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

    /** Looks up another account by its complete email address. */
    suspend fun searchByEmail(value: String): User? {
        val entered = value.trim()
        require(Regex("""[^\s@]+@[^\s@]+""").matches(entered)) { "Enter a full email address" }
        val uid = currentUid()
        val email = entered.lowercase()
        if (auth.currentUser?.email?.trim()?.lowercase() == email) return null

        // Support existing profiles that still have the original letter casing.
        val addresses = listOf(email, entered).distinct()
        return users.whereIn("email", addresses)
            .limit(2)
            .get().await().toObjects(User::class.java)
            .firstOrNull { it.uid.isNotBlank() && it.uid != uid }
    }
}
