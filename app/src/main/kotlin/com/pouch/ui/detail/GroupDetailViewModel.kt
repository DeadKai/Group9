package com.pouch.ui.detail

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pouch.data.local.dao.ExpenseDao
import com.pouch.data.local.dao.GroupDao
import com.pouch.data.local.entity.ExpenseEntity
import com.pouch.data.local.entity.GroupEntity
import com.pouch.data.remote.ExchangeRateResponse
import com.pouch.data.repository.CurrencyRepository
import com.pouch.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CurrencyRateState(
    val rates: ExchangeRateResponse? = null,
    val isOffline: Boolean = false,
    val selectedCurrency: String = "USD"
)

class GroupDetailViewModel(
    val groupId: Long,
    private val groupDao: GroupDao,
    private val expenseDao: ExpenseDao,
    private val currencyRepository: CurrencyRepository
) : ViewModel() {

    val groupState: StateFlow<GroupEntity?> = groupDao.getGroupById(groupId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val expensesState: StateFlow<UiState<List<ExpenseEntity>>> = expenseDao.getExpensesForGroup(groupId)
        .map { expenses ->
            if (expenses.isEmpty()) {
                UiState.Empty
            } else {
                UiState.Success(expenses)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UiState.Loading
        )

    private val _currencyState = MutableStateFlow(CurrencyRateState())
    val currencyState: StateFlow<CurrencyRateState> = _currencyState.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _amountText = MutableStateFlow("")
    val amountText: StateFlow<String> = _amountText.asStateFlow()

    private val _receiptBitmap = MutableStateFlow<Bitmap?>(null)
    val receiptBitmap: StateFlow<Bitmap?> = _receiptBitmap.asStateFlow()

    init {
        fetchCurrencyRates()
    }

    fun fetchCurrencyRates() {
        viewModelScope.launch {
            val (rates, isOffline) = currencyRepository.getExchangeRates("EUR")
            _currencyState.value = CurrencyRateState(
                rates = rates,
                isOffline = isOffline,
                selectedCurrency = "USD"
            )
        }
    }

    fun onTitleChanged(title: String) {
        _title.value = title
    }

    fun onAmountChanged(amount: String) {
        _amountText.value = amount
    }

    fun onReceiptCaptured(bitmap: Bitmap?) {
        _receiptBitmap.value = bitmap
    }

    fun addExpense() {
        val title = _title.value.trim()
        val amountDouble = _amountText.value.toDoubleOrNull()
        if (title.isNotEmpty() && amountDouble != null && amountDouble > 0) {
            val amountCents = (amountDouble * 100).toLong()
            viewModelScope.launch {
                expenseDao.insertExpense(
                    ExpenseEntity(
                        groupId = groupId,
                        payerMemberId = 1L,
                        title = title,
                        amountCents = amountCents
                    )
                )
                _title.value = ""
                _amountText.value = ""
                _receiptBitmap.value = null
            }
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            expenseDao.deleteExpense(expense)
        }
    }
}
