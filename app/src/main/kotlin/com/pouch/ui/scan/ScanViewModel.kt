package com.pouch.ui.scan

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pouch.data.GroupRepository
import com.pouch.domain.Group
import com.pouch.domain.MemberAmount
import com.pouch.domain.SplitCalculator
import com.pouch.domain.SplitMode
import com.pouch.domain.formatMoney
import com.pouch.ocr.ReceiptScanner
import com.pouch.ui.common.SuccessController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job

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
    val payments: Map<String, Long> = emptyMap(),
    val shares: Map<String, Long> = emptyMap(),
    val splitMode: SplitMode = SplitMode.EQUAL,
    val allocationError: String? = null
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
    private val success: SuccessController,
    private var scanJob: Job? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ScanUiState(
            groupLocked = presetGroupId != null,
            selectedGroupId = presetGroupId
        )
    )
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
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
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
                    it.copy(
                        step = ScanStep.EDIT,
                        scanMessage = "Couldn't read this photo. Enter the details manually or try a sharper photo."
                    )
                }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _state.update { it.copy(step = ScanStep.CHOOSE_SOURCE) }
    }

    fun enterManually() = _state.update { it.copy(step = ScanStep.EDIT, scanMessage = null) }
    fun backToSource() = _state.update { it.copy(step = ScanStep.CHOOSE_SOURCE) }

    fun onTitleChange(v: String) = _state.update { it.copy(title = v, titleError = null) }
    fun onAmountChange(v: String) = _state.update { it.copy(amount = v, amountError = null) }
    fun onPaidByChange(id: String) =
        _state.update { it.copy(paidByMemberId = id, paidByError = null) }

    fun onGroupSelected(groupId: String) = _state.update { s ->
        val group = s.groups.firstOrNull { it.id == groupId }
        s.copy(
            selectedGroupId = groupId,
            groupError = null,
            paidByMemberId = group?.members?.firstOrNull()?.id
        )
    }

    fun onPaymentsChange(payments: Map<String, Long>) {
        _state.update {
            it.copy(
                payments = payments,
                allocationError = null
            )
        }
    }

    fun onSharesChange(shares: Map<String, Long>) {
        _state.update {
            it.copy(
                shares = shares,
                splitMode = SplitMode.CUSTOM,
                allocationError = null
            )
        }
    }


    fun onSplitModeChange(mode: SplitMode) {
        _state.update { s ->
            val total = parseAmountToCents(s.amount) ?: 0L
            val members = s.selectedGroup?.members.orEmpty()

            val equalShares = if (members.isNotEmpty()) {
                SplitCalculator.equalShares(total, members.size)
            } else {
                emptyList()
            }

            val equalMap = members.mapIndexed { index, member ->
                member.id to equalShares[index]
            }.toMap()

            s.copy(
                splitMode = mode,
                shares = if (mode == SplitMode.EQUAL ||
                    s.splitMode == SplitMode.EQUAL
                ) {
                    equalMap
                } else {
                    s.shares
                },
                allocationError = null
            )
        }
    }

    fun save() {
        val s = _state.value

        if (s.isSaving) return

        val cents = parseAmountToCents(s.amount)

        val titleError = when {
            s.title.isBlank() ->
                "What was it for? (e.g. Groceries)"

            s.title.trim().length > 60 ->
                "Keep it under 60 characters"

            else -> null
        }

        val amountError = when {
            s.amount.isBlank() ->
                "Enter the total amount"

            cents == null ->
                "Use a number like 12.50"

            cents <= 0L ->
                "Amount must be more than 0"

            else -> null
        }

        val group = s.selectedGroup

        val groupError = when {
            group == null -> "Choose a group"
            group.members.isEmpty() -> "Group has no members"
            else -> null
        }

        if (
            titleError != null ||
            amountError != null ||
            groupError != null
        ) {
            _state.update {
                it.copy(
                    titleError = titleError,
                    amountError = amountError,
                    groupError = groupError,
                    allocationError = null
                )
            }
            return
        }

        val validGroup = group ?: return
        val total = cents ?: return
        val members = validGroup.members
        val memberIds = members.map { it.id }.toSet()

        // Default payer is the first group member.
        val payerId = s.paidByMemberId
            ?.takeIf { it in memberIds }
            ?: members.first().id

        // Payments: how much each member actually paid.
        val paymentValues: Map<String, Long> =
            if (s.payments.isEmpty()) {
                mapOf(payerId to total)
            } else {
                s.payments
            }

        // Calculate equal shares as the default.
        val equalAmounts = SplitCalculator.equalShares(
            total,
            members.size
        )

        val equalShareValues: Map<String, Long> =
            members.mapIndexed { index, member ->
                member.id to equalAmounts[index]
            }.toMap()

        // Shares: how much each member owes.
        val shareValues: Map<String, Long> =
            if (s.splitMode == SplitMode.EQUAL) {
                equalShareValues
            } else if (s.shares.isEmpty()) {
                equalShareValues
            } else {
                s.shares
            }

        // Validate that every allocation belongs to a group member,
        // is nonnegative, and sums to the receipt total.
        fun validAllocation(values: Map<String, Long>): Boolean {
            if (values.isEmpty()) return false

            if (values.keys.any { it !in memberIds }) {
                return false
            }

            if (values.values.any { it < 0L }) {
                return false
            }

            val sum = try {
                values.values.fold(0L) { acc, value ->
                    Math.addExact(acc, value)
                }
            } catch (_: ArithmeticException) {
                return false
            }

            return sum == total
        }

        val allocationError = when {
            !validAllocation(paymentValues) ->
                "Payments must add up to the receipt total"

            !validAllocation(shareValues) ->
                "Shares must add up to the receipt total"

            else -> null
        }

        if (allocationError != null) {
            _state.update {
                it.copy(allocationError = allocationError)
            }
            return
        }

        // Convert maps into the lists required by GroupRepository.
        val payments = paymentValues.map { (memberId, amount) ->
            MemberAmount(
                memberId = memberId,
                amountCents = amount
            )
        }

        val shares = shareValues.map { (memberId, amount) ->
            MemberAmount(
                memberId = memberId,
                amountCents = amount
            )
        }

        _state.update {
            it.copy(
                isSaving = true,
                allocationError = null
            )
        }

        viewModelScope.launch {
            try {
                groupsRepo.addReceipt(
                    groupId = validGroup.id,
                    title = s.title.trim(),
                    totalCents = total,
                    payments = payments,
                    shares = shares,
                    splitMode = s.splitMode
                )

                success.show(
                    "Receipt added",
                    "${total.formatMoney()} added to ${validGroup.name}"
                )
                _state.update {
                    it.copy(
                        isSaving = false,
                        saved = true
                    )
                }

            } catch (e: CancellationException) {
                throw e

            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isSaving = false,
                        allocationError = e.message
                            ?: "Failed to save receipt"
                    )
                }
            }
        }
    }
}
