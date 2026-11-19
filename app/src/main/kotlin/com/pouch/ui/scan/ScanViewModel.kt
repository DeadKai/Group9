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
import com.pouch.domain.ReceiptItem
import com.pouch.ocr.ReceiptScanner
import com.pouch.ui.common.centsText
import com.pouch.ui.common.SuccessController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlin.plus
import kotlin.text.any
import kotlin.text.orEmpty
import kotlin.time.Duration.Companion.milliseconds

/** Which part of the Scan screen is visible. */
enum class ScanStep { CHOOSE_SOURCE, READING, EDIT }

data class ScanUiState(
    val step: ScanStep = ScanStep.CHOOSE_SOURCE,
    val groupsLoaded: Boolean = false,
    val items: List<ReceiptItem> = emptyList(),
    val groupsLoadError: String? = null,
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


private fun defaultPaymentForTotal(
    s: ScanUiState,
    total: Long
): Map<String, Long> {

    val activePayers = s.payments.filterValues {
        it > 0L
    }

    // Preserve manually entered payments when
    // multiple people paid.
    if (activePayers.size > 1) {
        return s.payments
    }

    val payerId = activePayers.keys.firstOrNull()
        ?: s.paidByMemberId
        ?: s.selectedGroup?.members?.firstOrNull()?.id

    return if (payerId != null) {
        mapOf(payerId to total)
    } else {
        emptyMap()
    }
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


    fun saveItem(
        id: String?,
        name: String,
        priceCents: Long,
        memberIds: List<String>
    ) {
        _state.update { s ->
            val item = ReceiptItem(
                id = id ?: UUID.randomUUID().toString(),
                name = name.trim(),
                totalCents = priceCents,
                assignedMemberIds = memberIds
            )

            val updatedItems = if (id == null) {
                s.items + item
            } else {
                s.items.map { existing ->
                    if (existing.id == id) item else existing
                }
            }

            s.copy(
                items = updatedItems,
                allocationError = null
            )
        }

        recalculateItemSplit()
    }

    fun removeItem(id: String) {
        _state.update { s ->
            s.copy(items = s.items.filterNot { it.id == id })
        }
        recalculateItemSplit()
    }

    private fun recalculateItemSplit() {
        _state.update { s ->
            if (s.splitMode != SplitMode.BY_ITEM) return@update s

            val members = s.selectedGroup?.members.orEmpty()
            val total = SplitCalculator.itemTotal(s.items)

            val validItems = s.items.filter { item ->
                item.assignedMemberIds.isNotEmpty() &&
                        item.assignedMemberIds.all { id ->
                            members.any { it.id == id }
                        }
            }

            val shares = SplitCalculator.itemShares(validItems, members)

            s.copy(
                amount = centsText(total),
                shares = shares,
                payments = defaultPaymentForTotal(s, total),
                allocationError = null,
                amountError = null
            )
        }
    }

    init {
        viewModelScope.launch {
            groupsRepo.groups.retryWhen { failure, attempt ->
                if (failure is CancellationException) return@retryWhen false
                _state.update {
                    it.copy(groupsLoaded = true, groupsLoadError = failure.message ?: "Could not load groups")
                }
                // Keep listening instead of stopping at the first error.
                delay(minOf(30_000L, 1_000L shl attempt.coerceAtMost(5).toInt()).milliseconds)
                true
            }.collect { groups ->
                _state.update { s ->
                    // Only one group? Pick it automatically.
                    val selected = s.selectedGroupId ?: groups.singleOrNull()?.id
                    val group = groups.firstOrNull { it.id == selected }
                    s.copy(
                        groupsLoaded = true,
                        groupsLoadError = null,
                        groups = groups,
                        selectedGroupId = selected,
                        paidByMemberId = s.paidByMemberId?.takeIf { id -> group?.members?.any { it.id == id } == true }
                            ?: group?.let(groupsRepo::currentMemberId),
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
            paidByMemberId = group?.let(groupsRepo::currentMemberId),
            payments = emptyMap(),
            shares = emptyMap(),
            allocationError = null,
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
        _state.update { s ->
            val total = shares.values.fold(0L, Math::addExact)

            s.copy(
                shares = shares,
                amount = centsText(total),
                payments = defaultPaymentForTotal(s, total),
                splitMode = SplitMode.CUSTOM,
                amountError = null,
                allocationError = null
            )
        }
    }

    fun onSplitModeChange(mode: SplitMode) {
        _state.update { s ->
            val members = s.selectedGroup?.members.orEmpty()

            val total = when (mode) {
                SplitMode.BY_ITEM -> SplitCalculator.itemTotal(s.items)
                else -> parseAmountToCents(s.amount) ?: 0L
            }

            val shares = when (mode) {
                SplitMode.EQUAL -> {
                    if (members.isEmpty()) {
                        emptyMap()
                    } else {
                        members.mapIndexed { index, member ->
                            member.id to SplitCalculator.equalShares(
                                total, members.size
                            )[index]
                        }.toMap()
                    }
                }

                SplitMode.CUSTOM -> {
                    if (s.splitMode == SplitMode.EQUAL) {
                        members.mapIndexed { index, member ->
                            member.id to SplitCalculator.equalShares(
                                total, members.size
                            )[index]
                        }.toMap()
                    } else {
                        s.shares
                    }
                }

                SplitMode.BY_ITEM -> {
                    val validItems = s.items.filter { item ->
                        item.assignedMemberIds.isNotEmpty() &&
                                item.assignedMemberIds.all { id ->
                                    members.any { it.id == id }
                                }
                    }
                    SplitCalculator.itemShares(validItems, members)
                }
            }

            s.copy(
                splitMode = mode,
                amount = centsText(total),
                shares = shares,
                payments = defaultPaymentForTotal(s, total),
                allocationError = null,
                amountError = null
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

        if (s.splitMode == SplitMode.BY_ITEM) {

            val itemsValid =
                s.items.isNotEmpty() &&
                        s.items.all { item ->
                            item.name.isNotBlank() &&
                                    item.totalCents > 0L &&
                                    item.assignedMemberIds.isNotEmpty() &&
                                    item.assignedMemberIds.distinct().size ==
                                    item.assignedMemberIds.size &&
                                    item.assignedMemberIds.all { id ->
                                        id in memberIds
                                    }
                        }

            if (
                !itemsValid ||
                SplitCalculator.itemTotal(s.items) != total
            ) {
                _state.update {
                    it.copy(
                        allocationError =
                            "Check item names, prices and assigned members"
                    )
                }
                return
            }
        }

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
                    splitMode = s.splitMode,
                    items = if (s.splitMode == SplitMode.BY_ITEM) {
                        s.items
                    } else {
                        emptyList()
                    }
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
