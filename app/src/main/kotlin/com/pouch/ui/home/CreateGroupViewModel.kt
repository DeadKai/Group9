package com.pouch.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pouch.data.GroupRepository
import com.pouch.domain.GroupIcon
import com.pouch.ui.common.SuccessController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException


data class CreateGroupUiState(
    val name: String = "",
    val icon: GroupIcon = GroupIcon.entries.first(),
    val newMemberName: String = "",
    val members: List<String> = emptyList(),
    val nameError: String? = null,
    val memberError: String? = null,
    val saveError: String? = null,
    val isSaving: Boolean = false,
    /** Set when saving finished -> the sheet closes and opens the new group. */
    val createdGroupId: String? = null,
)

class CreateGroupViewModel(
    private val repository: GroupRepository,
    private val success: SuccessController,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateGroupUiState())
    val state: StateFlow<CreateGroupUiState> = _state.asStateFlow()

    fun onNameChange(v: String) = _state.update { it.copy(name = v, nameError = null) }
    fun onIconSelected(v: GroupIcon) = _state.update { it.copy(icon = v) }
    fun onNewMemberChange(v: String) = _state.update { it.copy(newMemberName = v, memberError = null) }

    fun addMember() {
        val s = _state.value
        val name = s.newMemberName.trim()
        val error = when {
            name.isEmpty() -> "Enter a name"
            name.equals("You", ignoreCase = true) -> "You are already in the group"
            s.members.any { it.equals(name, ignoreCase = true) } -> "$name is already in the group"
            else -> null
        }
        if (error != null) _state.update { it.copy(memberError = error) }
        else _state.update { it.copy(members = it.members + name, newMemberName = "") }
    }

    fun removeMember(name: String) = _state.update { it.copy(members = it.members - name) }

    fun save() {
        val s = _state.value
        if (s.isSaving) return
        val nameError = when {
            s.name.isBlank() -> "Give your group a name"
            s.name.trim().length > MAX_NAME -> "Keep it under $MAX_NAME characters"
            else -> null
        }
        val memberError = if (s.members.isEmpty() && !repository.hasAccount()) {
            "Add at least one person to split with"
        } else null
        if (nameError != null || memberError != null) {
            _state.update { it.copy(nameError = nameError, memberError = memberError) }
            return
        }

        _state.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            try {
                val id = repository.createGroup(s.name, s.icon, s.members)
                success.show("Group created", "\"${s.name.trim()}\" is ready.")
                _state.update { it.copy(isSaving = false, createdGroupId = id) }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                _state.update { it.copy(isSaving = false, saveError = failure.message ?: "Could not create group") }
            }
        }
    }

    /** Clears the form after the sheet closes. */
    fun reset() { _state.value = CreateGroupUiState() }

    private companion object { const val MAX_NAME = 40 }
}
