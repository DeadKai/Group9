package com.pouch.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pouch.domain.Group
import com.pouch.domain.Receipt
import com.pouch.domain.formatMoney
import com.pouch.ui.common.BackTopBar
import com.pouch.ui.common.EmptyView
import com.pouch.ui.common.LoadingView
import com.pouch.ui.common.UiState
import com.pouch.ui.common.pouchViewModel
import com.pouch.ui.home.HomeViewModel
import java.text.DateFormat
import java.util.Date

/** Pairs a receipt with the group that owns it. */
private data class HistoryEntry(val group: Group, val receipt: Receipt) {
    val date: Long get() = receipt.receiptDate ?: receipt.createdAt
}

/** Lists receipts from all visible groups, newest first. */
@Composable
fun BillHistoryScreen(
    onBack: () -> Unit,
    onOpenReceipt: (groupId: String, receiptId: String) -> Unit,
    viewModel: HomeViewModel = pouchViewModel { HomeViewModel(it.groupRepository) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { BackTopBar("Bill History", onBack) }) { padding ->
        val modifier = Modifier.padding(padding)
        when (val result = state) {
            UiState.Loading -> LoadingView(modifier)
            is UiState.Error -> EmptyView("Could not load bill history", result.message, modifier)
            else -> {
                val groups = (result as? UiState.Success)?.data.orEmpty()
                val entries = remember(groups) {
                    groups.flatMap { group -> group.receipts.map { HistoryEntry(group, it) } }
                        .sortedByDescending { it.date }
                }
                if (entries.isEmpty()) {
                    EmptyView(
                        title = "No receipts yet",
                        message = "Receipts from your groups will appear here.",
                        modifier = modifier,
                    )
                } else {
                    HistoryList(entries, onOpenReceipt, modifier)
                }
            }
        }
    }
}

/** Shows each receipt's title, group, date, and total. */
@Composable
private fun HistoryList(
    entries: List<HistoryEntry>,
    onOpenReceipt: (String, String) -> Unit,
    modifier: Modifier,
) {
    val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(entries, key = { "${it.group.id}:${it.receipt.id}" }) { entry ->
            Card(
                onClick = { onOpenReceipt(entry.group.id, entry.receipt.id) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.receipt.title, style = MaterialTheme.typography.titleSmall)
                        val subtitle = if (entry.date > 0) {
                            "${entry.group.name} · ${dateFormat.format(Date(entry.date))}"
                        } else {
                            entry.group.name
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(entry.receipt.totalCents.formatMoney(), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}
