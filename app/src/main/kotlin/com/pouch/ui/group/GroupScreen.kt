package com.pouch.ui.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pouch.domain.Member
import com.pouch.domain.Receipt
import com.pouch.domain.formatMoney
import com.pouch.ui.common.BackTopBar
import com.pouch.ui.common.EmptyView
import com.pouch.ui.common.ErrorView
import com.pouch.ui.common.LoadingView
import com.pouch.ui.common.UiState
import com.pouch.ui.common.ValidatedTextField
import com.pouch.ui.common.pouchViewModel
import com.pouch.ui.theme.PouchColors
import java.text.DateFormat
import java.util.Date

private val TABS = listOf("Receipts", "Balances", "Members")

@Composable
fun GroupScreen(
    groupId: String,
    onBack: () -> Unit,
    onAddReceipt: () -> Unit,
    viewModel: GroupViewModel = pouchViewModel { GroupViewModel(groupId, it.groupRepository) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showAddMember by remember { mutableStateOf(false) }
    var receiptToDelete by remember { mutableStateOf<Receipt?>(null) }

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); viewModel.messageShown() } }

    val title = (state as? UiState.Success)?.data?.group?.let { "${it.icon} ${it.name}" } ?: "Group"

    Scaffold(
        topBar = { BackTopBar(title, onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (state is UiState.Success) {
                when (tab) {
                    0 -> ExtendedFloatingActionButton(
                        onClick = onAddReceipt,
                        icon = { Icon(Icons.Default.Add, null) },
                        text = { Text("Add receipt") },
                    )
                    2 -> ExtendedFloatingActionButton(
                        onClick = { showAddMember = true },
                        icon = { Icon(Icons.Default.Add, null) },
                        text = { Text("Add member") },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                UiState.Loading -> LoadingView()
                UiState.Empty -> LoadingView()
                is UiState.Error -> ErrorView(s.message, onRetry = onBack)
                is UiState.Success -> {
                    val details = s.data
                    TabRow(selectedTabIndex = tab) {
                        TABS.forEachIndexed { i, label ->
                            Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                        }
                    }
                    when (tab) {
                        0 -> ReceiptsTab(details, onAddReceipt, onDelete = { receiptToDelete = it })
                        1 -> BalancesTab(details)
                        else -> MembersTab(details.group.members, onRemove = viewModel::removeMember)
                    }
                }
            }
        }
    }

    if (showAddMember) {
        AddMemberDialog(
            onDismiss = { showAddMember = false },
            onAdd = { name ->
                val error = viewModel.addMember(name)
                if (error == null) showAddMember = false
                error
            },
        )
    }

    receiptToDelete?.let { receipt ->
        // Destructive actions need confirmation (design system rule).
        AlertDialog(
            onDismissRequest = { receiptToDelete = null },
            title = { Text("Delete receipt?") },
            text = { Text("\"${receipt.title}\" (${receipt.totalCents.formatMoney()}) will be removed for everyone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteReceipt(receipt); receiptToDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { receiptToDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ReceiptsTab(details: GroupDetails, onAddReceipt: () -> Unit, onDelete: (Receipt) -> Unit) {
    if (details.group.receipts.isEmpty()) {
        EmptyView(
            title = "No receipts yet",
            message = "Add one to get started.",
            actionLabel = "Add receipt",
            onAction = onAddReceipt,
        )
        return
    }
    LazyColumn(contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(details.group.receipts, key = { it.id }) { receipt ->
            ListCard {
                Column(Modifier.weight(1f)) {
                    Text(receipt.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Paid by ${details.memberName(receipt.paidByMemberId)} · " +
                            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(receipt.createdAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(receipt.totalCents.formatMoney(), style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = { onDelete(receipt) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete ${receipt.title}")
                }
            }
        }
    }
}

@Composable
private fun BalancesTab(details: GroupDetails) {
    LazyColumn(contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Who owes whom", style = MaterialTheme.typography.titleMedium) }
        if (details.settlements.isEmpty()) {
            item { Text("Everyone is settled up 🎉", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(details.settlements) { s ->
            ListCard {
                Text(
                    "${details.memberName(s.fromMemberId)} owes ${details.memberName(s.toMemberId)}",
                    modifier = Modifier.weight(1f),
                )
                Text(s.amountCents.formatMoney(), style = MaterialTheme.typography.titleSmall)
            }
        }
        item { Text("Balances", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp)) }
        items(details.group.members, key = { it.id }) { member ->
            val balance = details.balances[member.id] ?: 0L
            ListCard {
                Text(member.name, modifier = Modifier.weight(1f))
                Text(
                    balance.formatMoney(),
                    color = when {
                        balance > 0 -> PouchColors.Success
                        balance < 0 -> PouchColors.Destructive
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun MembersTab(members: List<Member>, onRemove: (Member) -> Unit) {
    LazyColumn(contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(members, key = { it.id }) { member ->
            ListCard {
                Text(member.name, modifier = Modifier.weight(1f))
                IconButton(onClick = { onRemove(member) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove ${member.name}")
                }
            }
        }
    }
}

@Composable
private fun AddMemberDialog(onDismiss: () -> Unit, onAdd: (String) -> String?) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add member") },
        text = {
            ValidatedTextField(value = name, onValueChange = { name = it; error = null }, label = "Name", error = error)
        },
        confirmButton = { TextButton(onClick = { error = onAdd(name) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val listPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp)

@Composable
private fun ListCard(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}
