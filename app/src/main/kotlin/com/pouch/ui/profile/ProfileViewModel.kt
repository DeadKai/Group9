
package com.pouch.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pouch.data.FriendsRepository
import com.pouch.data.UserRepository
import com.pouch.domain.FriendRequest
import com.pouch.domain.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val loading: Boolean = true,
    val profile: User? = null,
    val friends: List<User> = emptyList(),
    val incoming: List<FriendRequest> = emptyList(),
    val outgoing: List<FriendRequest> = emptyList(),
    val results: List<User> = emptyList(),
    val requestUsers: Map<String, User> = emptyMap(),
    val busy: Boolean = false,
    val message: String? = null
)

class ProfileViewModel(
    private val users: UserRepository = UserRepository(),
    private val friendsRepo: FriendsRepository = FriendsRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    private fun change(transform: (ProfileUiState) -> ProfileUiState) {
        _state.value = transform(_state.value)
    }

    fun refresh() {
        viewModelScope.launch {
            change { it.copy(loading = true) }
            try {
                val profile = users.ensureProfile()
                val friends = friendsRepo.friends()
                val incoming = friendsRepo.incomingRequests()
                val outgoing = friendsRepo.outgoingRequests()

                val ids = (incoming + outgoing)
                    .flatMap { listOf(it.senderId, it.receiverId) }
                    .filter { it != profile.uid }
                    .distinct()

                val requestUsers = ids.mapNotNull { id ->
                    users.getUser(id)?.let { id to it }
                }.toMap()

                change {
                    it.copy(
                        loading = false,
                        profile = profile,
                        friends = friends,
                        incoming = incoming,
                        outgoing = outgoing,
                        requestUsers = requestUsers
                    )
                }
            } catch (e: Exception) {
                change {
                    it.copy(
                        loading = false,
                        message = e.localizedMessage ?: "Could not load profile"
                    )
                }
            }
        }
    }

    private fun perform(action: suspend () -> Unit) {
        viewModelScope.launch {
            change { it.copy(busy = true, message = null) }
            try {
                action()
                refresh()
            } catch (e: Exception) {
                change {
                    it.copy(message = e.localizedMessage ?: "Operation failed")
                }
            } finally {
                change { it.copy(busy = false) }
            }
        }
    }

    fun saveProfile(name: String, username: String) = perform {
        val current = state.value.profile ?: error("Profile not loaded")
        if (current.username != username.trim().lowercase()) {
            users.setUsername(username)
        }
        if (current.fullName != name.trim()) {
            users.updateName(name)
        }
    }

    fun search(query: String) {
        viewModelScope.launch {
            try {
                val results = users.searchByUsername(query)
                change { it.copy(results = results, message = null) }
            } catch (e: Exception) {
                change {
                    it.copy(message = e.localizedMessage ?: "Search failed")
                }
            }
        }
    }

    fun addFriend(user: User) = perform {
        friendsRepo.sendRequest(user.uid)
    }

    fun accept(request: FriendRequest) = perform {
        friendsRepo.acceptRequest(request)
    }

    fun reject(request: FriendRequest) = perform {
        friendsRepo.rejectRequest(request)
    }

    fun cancel(request: FriendRequest) = perform {
        friendsRepo.cancelRequest(request)
    }

    fun remove(user: User) = perform {
        friendsRepo.removeFriend(user.uid)
    }

    fun clearMessage() {
        change { it.copy(message = null) }
    }
}
