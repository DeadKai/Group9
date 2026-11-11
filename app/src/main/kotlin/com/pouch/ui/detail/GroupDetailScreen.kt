package com.pouch.ui.detail

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pouch.data.local.entity.ExpenseEntity
import com.pouch.ui.common.UiState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    viewModel: GroupDetailViewModel,
    onBackClick: () -> Unit
) {
    val group by viewModel.groupState.collectAsStateWithLifecycle()
    val expensesState by viewModel.expensesState.collectAsStateWithLifecycle()
    val currencyState by viewModel.currencyState.collectAsStateWithLifecycle()
    val title by viewModel.title.collectAsStateWithLifecycle()
    val amountText by viewModel.amountText.collectAsStateWithLifecycle()
    val receiptBitmap by viewModel.receiptBitmap.collectAsStateWithLifecycle()

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        viewModel.onReceiptCaptured(bitmap)
    }

    val isValidAmount = amountText.toDoubleOrNull()?.let { it > 0 } ?: false
    val isFormValid = title.isNotBlank() && isValidAmount

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.name ?: "Group Expenses") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Currency Conversion Banner
            val rate = currencyState.rates?.rates?.get("USD")
            val offlineTag = if (currencyState.isOffline) " (Offline Cache)" else " (Live FX)"
            val rateText = if (rate != null) {
                "Exchange Rate: 1 EUR = %.2f USD%s".format(Locale.US, rate, offlineTag)
            } else {
                "Exchange Rate: Loading...$offlineTag"
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = rateText,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Add Expense Form
            Text(
                text = "Add New Expense",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { viewModel.onTitleChanged(it) },
                label = { Text("Expense Title (e.g. Dinner)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = amountText,
                onValueChange = { viewModel.onAmountChanged(it) },
                label = { Text("Amount (€ EUR)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = amountText.isNotEmpty() && !isValidAmount,
                supportingText = {
                    if (amountText.isNotEmpty() && !isValidAmount) {
                        Text("Enter a valid positive amount", color = MaterialTheme.colorScheme.error)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { takePictureLauncher.launch(null) }
                ) {
                    Text(if (receiptBitmap != null) "Retake Receipt" else "📷 Capture Receipt")
                }

                receiptBitmap?.let { bitmap ->
                    Spacer(modifier = Modifier.width(16.dp))
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Receipt Thumbnail",
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = { viewModel.addExpense() },
                    enabled = isFormValid
                ) {
                    Text("Add")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Expenses List",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            when (val state = expensesState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Empty -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No expenses logged for this group yet.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                is UiState.Success -> {
                    val usdRate = currencyState.rates?.rates?.get("USD") ?: 1.0
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.data, key = { it.id }) { expense ->
                            ExpenseCard(
                                expense = expense,
                                usdRate = usdRate,
                                onDelete = { viewModel.deleteExpense(expense) }
                            )
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
fun ExpenseCard(
    expense: ExpenseEntity,
    usdRate: Double,
    onDelete: () -> Unit
) {
    val eurAmount = expense.amountCents / 100.0
    val usdAmount = eurAmount * usdRate

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = expense.title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "€%.2f  (≈ $%.2f USD)".format(Locale.US, eurAmount, usdAmount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onDelete) {
                Text("✕", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
