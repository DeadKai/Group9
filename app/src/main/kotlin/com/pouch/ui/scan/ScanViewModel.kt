package com.pouch.ui.scan

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pouch.data.GroupRepository
import com.pouch.domain.Group
import com.pouch.domain.SplitCalculator
import com.pouch.ocr.ReceiptScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import kotlin.coroutines.cancellation.CancellationException

/** Which part of the Scan screen is visible. */
enum class ScanStep { CHOOSE_SOURCE, READING, EDIT }

data class ScanUiState(
    val step: ScanStep = ScanStep.CHOOSE_SOURCE,
    val groupsLoaded: Boolean = false,
    val groups: List<Group> = emptyList(),
    val groupLocked: Boolean = false,          // true when opened from a Group screen
    val selectedGroupId: String? = null,
    val title: String = "",
    val amount: String = "",
    val paidByMemberId: String? = null,
    val scanMessage: String? = null,           // info / error from the OCR step
    val titleError: String? = null,
    val amountError: String? = null,
    val groupError: String? = null,
    val paidByError: String? = null,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
) {
    val selectedGroup: Group? get() = groups.firstOrNull { it.id == selectedGroupId }

    /** Live preview of each person's share. */
    val sharePreview: List<Long>?
        get() {
            val members = selectedGroup?.members.orEmpty()
            val cents = parseAmountToCents(amount) ?: return null
            if (cents <= 0 || members.isEmpty()) return null
            return SplitCalculator.equalShares(cents, members.size)
        }
}

/** "12,5" / "12.50" -> 1250. Returns null if the text is not a valid amount. */
fun parseAmountToCents(text: String): Long? {
    val normalized = text.trim().replace(',', '.')
    if (!Regex("""^\d{1,6}(\.\d{1,2})?$""").matches(normalized)) return null
    return BigDecimal(normalized).movePointRight(2).toLong()
}

private fun Long.toAmountText(): String = "${this / 100}.${(this % 100).toString().padStart(2, '0')}"

class ScanViewModel(
    private val presetGroupId: String?,
    private val groupsRepo: GroupRepository,
    private val scanner: ReceiptScanner,
) : ViewModel() {

    private val _state = MutableStateFlow(ScanUiState(groupLocked = presetGroupId != null, selectedGroupId = presetGroupId))
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            groupsRepo.groups.collect { groups ->
                _state.update { s ->
                    // Only one group? Pick it automatically.
                    val selected = s.selectedGroupId ?: groups.singleOrNull()?.id
                    val group = groups.firstOrNull { it.id == selected }
                    s.copy(
                        groupsLoaded = true,
                        groups = groups,
                        selectedGroupId = selected,
                        // Default payer = first member (the group creator, "you").
                        paidByMemberId = s.paidByMemberId?.takeIf { id -> group?.members?.any { it.id == id } == true }
                            ?: group?.members?.firstOrNull()?.id,
                    )
                }
            }
        }
    }

    /** Called with the photo from the camera or gallery. */
    fun onImagePicked(uri: Uri) {
        _state.update { it.copy(step = ScanStep.READING, scanMessage = null) }
        viewModelScope.launch {
            try {
                val result = scanner.scan(uri)
                _state.update {
                    it.copy(
                        step = ScanStep.EDIT,
                        title = result.title ?: it.title,
                        amount = result.totalCents?.toAmountText() ?: it.amount,
                        scanMessage = if (result.totalCents == null)
                            "Couldn't find the total. Please type it in."
                        else
                            "Check the details below. You can fix anything the scan got wrong.",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(step = ScanStep.EDIT, scanMessage = "Couldn't read this photo. Enter the details manually or try a sharper photo.")
                }
            }
        }
    }

    fun enterManually() = _state.update { it.copy(step = ScanStep.EDIT, scanMessage = null) }
    fun backToSource() = _state.update { it.copy(step = ScanStep.CHOOSE_SOURCE) }

    fun onTitleChange(v: String) = _state.update { it.copy(title = v, titleError = null) }
    fun onAmountChange(v: String) = _state.update { it.copy(amount = v, amountError = null) }
    fun onPaidByChange(id: String) = _state.update { it.copy(paidByMemberId = id, paidByError = null) }

    fun onGroupSelected(groupId: String) = _state.update { s ->
        val group = s.groups.firstOrNull { it.id == groupId }
        s.copy(selectedGroupId = groupId, groupError = null, paidByMemberId = group?.members?.firstOrNull()?.id)
    }

    fun save() {
        val s = _state.value
        val cents = parseAmountToCents(s.amount)
        val titleError = when {
            s.title.isBlank() -> "What was it for? (e.g. Groceries)"
            s.title.length > 60 -> "Keep it under 60 characters"
            else -> null
        }
        val amountError = when {
            s.amount.isBlank() -> "Enter the total amount"
            cents == null -> "Use a number like 12.50"
            cents <= 0 -> "Amount must be more than 0"
            else -> null
        }
        val groupError = if (s.selectedGroup == null) "Choose a group" else null
        val paidByError = if (s.selectedGroup != null && s.paidByMemberId == null) "Choose who paid" else null

        if (listOf(titleError, amountError, groupError, paidByError).any { it != null }) {
            _state.update {
                it.copy(titleError = titleError, amountError = amountError, groupError = groupError, paidByError = paidByError)
            }
            return
        }

        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            groupsRepo.addReceipt(s.selectedGroupId!!, s.title, cents!!, s.paidByMemberId!!)
            _state.update { it.copy(isSaving = false, saved = true) }
        }
    }
}
