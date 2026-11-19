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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.pouch.domain.User
import com.pouch.domain.formatMoney
import com.pouch.domain.MemberAmount
import com.pouch.domain.SplitMode
import com.pouch.domain.SplitCalculator
import com.pouch.domain.ReceiptItem
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
import java.util.UUID

private val TABS = listOf("Receipts", "Balances", "Members")

/** Displays a group's receipts, balances, and members, with an optional receipt to edit. */
@Composable
fun GroupScreen(
    groupId: String,
    onBack: () -> Unit,
    onAddReceipt: () -> Unit,
    initialReceiptId: String? = null,
    viewModel: GroupViewModel = pouchViewModel { GroupViewModel(groupId, it.groupRepository, it.success) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val sharing by viewModel.sharing.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showAddMember by remember { mutableStateOf(false) }
    var receiptToDelete by remember { mutableStateOf<Receipt?>(null) }
    var receiptToEditId by rememberSaveable { mutableStateOf(initialReceiptId) }
    var confirmDeleteGroup by remember { mutableStateOf(false) }

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); viewModel.messageShown() } }

    val details = (state as? UiState.Success)?.data
    val title = details?.group?.let { "${it.icon} ${it.name}" } ?: "Group"

    Scaffold(
        topBar = {
            BackTopBar(title, onBack) {
                if (details?.canManage == true) {
                    IconButton(onClick = { confirmDeleteGroup = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete group")
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (state is UiState.Success) {
                when (tab) {
                    0 -> ExtendedFloatingActionButton(
                        onClick = onAddReceipt,
                        icon = { Icon(Icons.Default.Add, null) },
                        text = { Text("Add receipt") },
                    )

                    2 -> if (details?.canManage == true) ExtendedFloatingActionButton(
                        onClick = {
                            showAddMember = true
                            if (details.group.createdByUserId.isNotEmpty()) viewModel.loadFriends()
                        },
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
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GroupStat("Total spent", details.group.totalSpentCents.formatMoney(), Modifier.weight(1.4f))
                        GroupStat("Members", details.group.members.size.toString(), Modifier.weight(1f))
                        GroupStat("Receipts", details.group.receipts.size.toString(), Modifier.weight(1f))
                    }
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        TABS.forEachIndexed { index, label ->
                            SegmentedButton(
                                selected = tab == index,
                                onClick = { tab = index },
                                shape = SegmentedButtonDefaults.itemShape(index, TABS.size),
                                icon = {},
                            ) { Text(label) }
                        }
                    }
                    when (tab) {
                        0 -> ReceiptsTab(
                            details,
                            onAddReceipt,
                            onDelete = { receiptToDelete = it },
                            onEdit = { receiptToEditId = it.id })

                        1 -> BalancesTab(details)
                        else -> MembersTab(
                            details.group.members,
                            ownerId = details.group.members.firstOrNull()?.id,
                            onRemove = if (details.canManage) viewModel::removeMember else null,
                        )
                    }
                }
            }
        }
    }

    if (showAddMember && details?.canManage == true) {
        AddMemberDialog(
            friendsState = if (details.group.createdByUserId.isNotEmpty()) friends else null,
            memberUserIds = details.group.memberUserIds,
            sharing = sharing,
            onDismiss = { showAddMember = false },
            onRetry = viewModel::loadFriends,
            onAddFriend = { friend -> viewModel.addFriend(friend) { showAddMember = false } },
            onAddName = { name ->
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

    if (confirmDeleteGroup) {
        AlertDialog(
            onDismissRequest = { confirmDeleteGroup = false },
            title = { Text("Delete group?") },
            text = { Text("The group and all its receipts will be deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteGroup = false
                    viewModel.deleteGroup(onDeleted = onBack)
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteGroup = false }) { Text("Cancel") }
            },
        )
    }

    if (details != null) {
        details.group.receipts.firstOrNull { it.id == receiptToEditId }?.let { receipt ->
            EditReceiptDialog(
                receipt = receipt,
                members = details.group.members,
                onDismiss = {
                    receiptToEditId = null
                },

                onSave = { title, cents, payments, shares, mode, items ->
                    viewModel.updateReceipt(
                        receipt = receipt,
                        title = title,
                        amountCents = cents,
                        payments = payments,
                        shares = shares,
                        splitMode = mode,
                        items = items,
                        onSaved = {
                            receiptToEditId = null
                        }
                    )
                }
            )
        }
    }
}
/** Displays one group statistic above the tabs. */
@Composable
private fun GroupStat(label: String, value: String, modifier: Modifier) {
    Card(modifier = modifier) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleSmall)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun MembersTab(
    members: List<Member>,
    ownerId: String?,
    onRemove: ((Member) -> Unit)?,
) {
    var memberToRemove by remember { mutableStateOf<Member?>(null) }
    LazyColumn(contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(members, key = { it.id }) { member ->
            ListCard {
                Text(member.name, modifier = Modifier.weight(1f))
                if (onRemove != null && member.id != ownerId) {
                    IconButton(onClick = { memberToRemove = member }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove ${member.name}")
                    }
                }
            }
        }
    }
    memberToRemove?.let { member ->
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            title = { Text("Remove ${member.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    memberToRemove = null
                    onRemove?.invoke(member)
                }) { Text("Remove member", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }) { Text("Cancel") }
            },
        )
    }
}

/**
 * Lets the group owner add a member: pick one of their friends (friends already in the group are
 * greyed out) or type a name for a participant without an account.
 * [friendsState] is null for groups that cannot be shared (no signed-in owner).
 */
@Composable
private fun AddMemberDialog(
    friendsState: UiState<List<User>>?,
    memberUserIds: List<String>,
    sharing: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onAddFriend: (User) -> Unit,
    onAddName: (String) -> String?,
) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedUid by rememberSaveable { mutableStateOf<String?>(null) }
    val friends = (friendsState as? UiState.Success)?.data.orEmpty().filter { it.uid.isNotBlank() }
    val selectedFriend = friends.firstOrNull { it.uid == selectedUid && it.uid !in memberUserIds }

    AlertDialog(
        onDismissRequest = { if (!sharing) onDismiss() },
        title = { Text("Add member") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (friendsState != null) {
                    Text("Friends", style = MaterialTheme.typography.titleSmall)
                    when (friendsState) {
                        UiState.Loading -> CircularProgressIndicator()
                        UiState.Empty -> Text("Add a friend in Profile first.")
                        is UiState.Error -> Column {
                            Text(friendsState.message)
                            TextButton(onClick = onRetry) { Text("Try again") }
                        }
                        is UiState.Success -> LazyColumn(Modifier.heightIn(max = 240.dp)) {
                            items(friends, key = { it.uid }) { friend ->
                                val inGroup = friend.uid in memberUserIds
                                val label = friend.fullName.ifBlank { friend.email.ifBlank { "Pouch user" } }
                                FilterChip(
                                    selected = friend.uid == selectedUid && !inGroup,
                                    onClick = {
                                        selectedUid = if (selectedUid == friend.uid) null else friend.uid
                                        name = ""
                                        error = null
                                    },
                                    enabled = !inGroup && !sharing,
                                    label = { Text(if (inGroup) "$label · in group" else label) },
                                )
                            }
                        }
                    }
                    Text(
                        "Friends can see and edit this group's receipts. Or add someone without an account:",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                ValidatedTextField(
                    value = name,
                    onValueChange = { name = it; error = null; if (it.isNotEmpty()) selectedUid = null },
                    label = "Name",
                    error = error,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val friend = selectedFriend
                    if (friend != null) onAddFriend(friend) else error = onAddName(name)
                },
                enabled = !sharing,
            ) {
                Text(if (sharing) "Adding…" else "Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !sharing) { Text("Cancel") } },
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
        SplitMode,
        List<ReceiptItem>
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
    var receiptItems by remember(receipt.id) {
        mutableStateOf(receipt.items.toList())
    }

    var error by remember(receipt.id) {
        mutableStateOf<String?>(null)
    }

    fun recalculateItems() {
        val newTotal = SplitCalculator.itemTotal(receiptItems)

        amount = centsText(newTotal)

        val validMemberIds = members.map { it.id }.toSet()

        val validItems = receiptItems.filter { item ->
            item.assignedMemberIds.isNotEmpty() &&
                    item.assignedMemberIds.all { it in validMemberIds }
        }

        shares = SplitCalculator.itemShares(validItems, members)

        if (payments.size <= 1) {
            val payerId = payments.keys.firstOrNull()
                ?: receipt.paidByMemberId

            payments = mapOf(payerId to newTotal)
        }
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
                if (splitMode == SplitMode.BY_ITEM) {
                    Text(
                        text = "Total (€): ${centsText(total)}",
                        style = MaterialTheme.typography.titleMedium
                    )
                } else {
                    ValidatedTextField(
                        value = amount,
                        onValueChange = {
                            amount = it
                            error = null
                        },
                        label = "Amount (€)",
                        error = null
                    )
                }

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

                        if (mode == SplitMode.BY_ITEM) {
                            recalculateItems()
                        }

                        error = null
                    },
                    onPaymentsChange = {
                        payments = it
                        error = null
                    },
                    onSharesChange = {
                        shares = it
                        splitMode = SplitMode.CUSTOM
                        amount = centsText(it.values.sum())
                        error = null
                    },

                    items = receiptItems,

                    onSaveItem = { id, name, price, memberIds ->

                        val item = ReceiptItem(
                            id = id ?: UUID.randomUUID().toString(),
                            name = name,
                            totalCents = price,
                            assignedMemberIds = memberIds
                        )

                        receiptItems = if (id == null) {
                            // Add a new item
                            receiptItems + item
                        } else {
                            // Update the existing item
                            receiptItems.map { existing ->
                                if (existing.id == id) {
                                    item
                                } else {
                                    existing
                                }
                            }
                        }

                        recalculateItems()
                        error = null
                    },

                    onRemoveItem = { id ->
                        receiptItems = receiptItems.filterNot {
                            it.id == id
                        }

                        recalculateItems()
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

                        splitMode == SplitMode.BY_ITEM &&
                                (
                                        receiptItems.isEmpty() ||
                                                receiptItems.any { item ->
                                                    item.name.isBlank() ||
                                                            item.totalCents <= 0L ||
                                                            item.assignedMemberIds.isEmpty() ||
                                                            item.assignedMemberIds.distinct().size !=
                                                            item.assignedMemberIds.size ||
                                                            item.assignedMemberIds.any { id ->
                                                                id !in memberIds
                                                            }
                                                } ||
                                                SplitCalculator.itemTotal(receiptItems) != cents
                                        ) ->
                            error = "Check all items before saving"

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
                                splitMode,
                                if (splitMode == SplitMode.BY_ITEM) {
                                    receiptItems
                                } else {
                                    emptyList()
                                }
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
