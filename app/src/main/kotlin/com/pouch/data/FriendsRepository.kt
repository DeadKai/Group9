package com.pouch.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.pouch.domain.FriendRequest
import com.pouch.domain.FriendRequestStatus
import com.pouch.domain.User
import kotlinx.coroutines.tasks.await

class FriendsRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val requests = db.collection("friendRequests")
    private val friendships = db.collection("friendships")
    private val users = db.collection("users")

    private fun uid(): String =
        auth.currentUser?.uid ?: error("Please log in first")

    private fun friendshipId(a: String, b: String) =
        listOf(a, b).sorted().joinToString("_")

    suspend fun sendRequest(receiverId: String) {
        val sender = uid()
        require(receiverId != sender) {
            "You cannot add yourself"
        }

        val friendshipRef =
            friendships.document(friendshipId(sender, receiverId))
        val requestRef =
            requests.document("${sender}_$receiverId")
        val reverseRef =
            requests.document("${receiverId}_$sender")

        db.runTransaction { tx ->
            val friendship = tx.get(friendshipRef)
            val existing = tx.get(requestRef)
            val reverse = tx.get(reverseRef)

            require(!friendship.exists()) {
                "Already friends"
            }
            require(!existing.exists()) {
                "Request already sent"
            }
            require(!reverse.exists()) {
                "This user has already sent you a request"
            }

            tx.set(requestRef, FriendRequest(
                id = requestRef.id,
                senderId = sender,
                receiverId = receiverId,
                createdAt = System.currentTimeMillis()
            ))
            null
        }.await()
    }

    suspend fun incomingRequests(): List<FriendRequest> =
        requests.whereEqualTo("receiverId", uid())
            .get().await().toObjects(FriendRequest::class.java)
            .filter { it.status == FriendRequestStatus.PENDING }

    suspend fun outgoingRequests(): List<FriendRequest> =
        requests.whereEqualTo("senderId", uid())
            .get().await().toObjects(FriendRequest::class.java)
            .filter { it.status == FriendRequestStatus.PENDING }

    suspend fun acceptRequest(request: FriendRequest) {
        val me = uid()
        require(request.receiverId == me)

        val requestRef = requests.document(request.id)
        val friendRef = friendships.document(
            friendshipId(request.senderId, me)
        )

        db.runTransaction { tx ->
            val existing = tx.get(requestRef)
            val friendship = tx.get(friendRef)

            require(existing.exists() &&
                    existing.getString("senderId") == request.senderId &&
                    existing.getString("receiverId") == me &&
                    existing.getString("status") == FriendRequestStatus.PENDING.value
            ) { "Request no longer available" }

            require(!friendship.exists()) { "Already friends" }

            tx.set(friendRef, mapOf(
                "userIds" to listOf(request.senderId, me).sorted(),
                "createdAt" to System.currentTimeMillis()
            ))
            tx.delete(requestRef)
            null
        }.await()
    }

    suspend fun rejectRequest(request: FriendRequest) {
        require(request.receiverId == uid())
        requests.document(request.id).delete().await()
    }

    suspend fun cancelRequest(request: FriendRequest) {
        require(request.senderId == uid())
        requests.document(request.id).delete().await()
    }

    suspend fun friends(): List<User> {
        val me = uid()
        val snapshots = friendships
            .whereArrayContains("userIds", me)
            .get().await()

        val friendIds = snapshots.documents.mapNotNull { doc ->
            val ids = doc.get("userIds") as? List<*>
            ids?.filterIsInstance<String>()?.firstOrNull { it != me }
        }.distinct()

        return friendIds.mapNotNull { friendId ->
            users.document(friendId).get().await()
                .toObject(User::class.java)
        }
    }

    suspend fun removeFriend(friendId: String) {
        friendships.document(
            friendshipId(uid(), friendId)
        ).delete().await()
    }
}
