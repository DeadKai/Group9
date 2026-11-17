package com.pouch.ui.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.material.icons.filled.Edit
import com.pouch.domain.Member
import com.pouch.domain.Receipt
import com.pouch.domain.formatMoney
import com.pouch.domain.MemberAmount
import com.pouch.domain.SplitMode
import com.pouch.domain.SplitCalculator
import com.pouch.ui.common.BackTopBar
import com.pouch.ui.common.ReceiptSplitEditor
import com.pouch.ui.common.parseCents
import com.pouch.ui.common.centsText
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
    var receiptToEdit by remember { mutableStateOf<Receipt?>(null) }

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
                        0 -> ReceiptsTab(
                            details,
                            onAddReceipt,
                            onDelete = { receiptToDelete = it },
                            onEdit = { receiptToEdit = it })

                        1 -> BalancesTab(details)
                        else -> MembersTab(
                            details.group.members,
                            onRemove = viewModel::removeMember
                        )
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

    receiptToEdit?.let { receipt ->
        val details = (state as? UiState.Success)?.data

        if (details != null) {
            EditReceiptDialog(
                receipt = receipt,
                members = details.group.members,
                onDismiss = {
                    receiptToEdit = null
                },
                onSave = { title, cents, payments, shares, mode ->
                    viewModel.updateReceipt(
                        receipt = receipt,
                        title = title,
                        amountCents = cents,
                        payments = payments,
                        shares = shares,
                        splitMode = mode
                    )

                    receiptToEdit = null
                }
            )
        }
    }
}
@Composable
private fun ReceiptsTab(details: GroupDetails, onAddReceipt: () -> Unit, onDelete: (Receipt) -> Unit, onEdit: (Receipt) -> Unit){
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
                IconButton(onClick = { onEdit(receipt) }) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit ${receipt.title}"
                    )
                }
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

@Composable
private fun EditReceiptDialog(
    receipt: Receipt,
    members: List<Member>,
    onDismiss: () -> Unit,
    onSave: (
        String,
        Long,
        List<MemberAmount>,
        List<MemberAmount>,
        SplitMode
    ) -> Unit
) {
    var title by remember(receipt.id) {
        mutableStateOf(receipt.title)
    }

    var amount by remember(receipt.id) {
        mutableStateOf(centsText(receipt.totalCents))
    }

    var payments by remember(receipt.id) {
        mutableStateOf(
            SplitCalculator.effectivePayments(receipt)
                .associate { it.memberId to it.amountCents }
        )
    }

    var shares by remember(receipt.id) {
        mutableStateOf(
            SplitCalculator.effectiveShares(receipt, members)
                .associate { it.memberId to it.amountCents }
        )
    }

    var splitMode by remember(receipt.id) {
        mutableStateOf(receipt.splitMode)
    }

    var error by remember(receipt.id) {
        mutableStateOf<String?>(null)
    }

    val total = parseCents(amount) ?: 0L

    val equalShares = if (members.isNotEmpty()) {
        SplitCalculator.equalShares(total, members.size)
    } else {
        emptyList()
    }

    val equalShareValues =
        members.mapIndexed { index, member ->
            member.id to equalShares[index]
        }.toMap()

    val displayedShares =
        if (splitMode == SplitMode.EQUAL)
            equalShareValues
        else
            shares

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit expense") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ValidatedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        error = null
                    },
                    label = "Expense name",
                    error = null
                )

                ValidatedTextField(
                    value = amount,
                    onValueChange = {
                        amount = it
                        error = null
                    },
                    label = "Amount (€)",
                    error = null
                )

                ReceiptSplitEditor(
                    members = members,
                    totalCents = total,
                    payments = payments,
                    shares = displayedShares,
                    splitMode = splitMode,
                    onSplitModeChange = { mode ->
                        if (mode == SplitMode.EQUAL) {
                            shares = equalShareValues
                        } else if (splitMode == SplitMode.EQUAL) {
                            shares = equalShareValues
                        }

                        splitMode = mode
                        error = null
                    },
                    onPaymentsChange = {
                        payments = it
                        error = null
                    },
                    onSharesChange = {
                        shares = it
                        splitMode = SplitMode.CUSTOM
                        error = null
                    }
                )

                if (error != null) {
                    Text(
                        text = error.orEmpty(),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cents = parseCents(amount)
                    val memberIds = members.map { it.id }.toSet()

                    fun valid(values: Map<String, Long>): Boolean {
                        if (cents == null || cents <= 0L)
                            return false
                        if (values.isEmpty())
                            return false
                        if (values.keys.any { it !in memberIds })
                            return false
                        if (values.values.any { it < 0L })
                            return false

                        val sum = try {
                            values.values.fold(0L) { acc, value ->
                                Math.addExact(acc, value)
                            }
                        } catch (_: ArithmeticException) {
                            return false
                        }

                        return sum == cents
                    }

                    when {
                        title.isBlank() ->
                            error = "Enter expense name"

                        title.trim().length > 60 ->
                            error = "Keep it under 60 characters"

                        cents == null || cents <= 0L ->
                            error = "Enter a valid amount"

                        !valid(payments) ->
                            error = "Payments must equal the total"

                        !valid(displayedShares) ->
                            error = "Shares must equal the total"

                        else -> {
                            onSave(
                                title.trim(),
                                cents,
                                payments.map { (id, value) ->
                                    MemberAmount(id, value)
                                },
                                displayedShares.map { (id, value) ->
                                    MemberAmount(id, value)
                                },
                                splitMode
                            )
                        }
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
