package com.pouch.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pouch.domain.Group
import com.pouch.domain.formatMoney
import com.pouch.ui.common.ErrorView
import com.pouch.ui.common.LoadingView
import com.pouch.ui.common.UiState
import com.pouch.ui.common.pouchViewModel
import com.pouch.ui.theme.PouchColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenGroup: (String) -> Unit,
    onScan: () -> Unit,
    onOpenBillHistory: () -> Unit,
    viewModel: HomeViewModel = pouchViewModel { HomeViewModel(it.groupRepository) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCreateGroup by rememberSaveable { mutableStateOf(false) }

    Scaffold { padding ->
        val modifier = Modifier.padding(padding)
        when (val s = state) {
            UiState.Loading -> LoadingView(modifier)
            UiState.Empty -> GroupList(
                emptyList(), HomeBalance(0L, 0L), onOpenGroup, onScan, onOpenBillHistory, { showCreateGroup = true }, modifier,
            )
            is UiState.Error -> ErrorView(s.message, onRetry = {}, modifier = modifier)
            is UiState.Success -> GroupList(
                s.data, remember(s.data, viewModel) { viewModel.balanceFor(s.data) }, onOpenGroup, onScan, onOpenBillHistory, { showCreateGroup = true }, modifier,
            )
        }
    }

    if (showCreateGroup) {
        CreateGroupSheet(
            onDismiss = { showCreateGroup = false },
            onCreated = { groupId ->
                showCreateGroup = false
                onOpenGroup(groupId)
            },
        )
    }
}

/** Displays the spending overview and group cards in one scrollable list. */
@Composable
private fun GroupList(
    groups: List<Group>,
    balance: HomeBalance,
    onOpenGroup: (String) -> Unit,
    onScan: () -> Unit,
    onHistory: () -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            val colors = MaterialTheme.colorScheme
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Pouch", style = MaterialTheme.typography.headlineSmall)
                Column(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
                        .background(colors.primaryContainer)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Net balance", color = colors.onPrimaryContainer)
                    Text(
                        (if (balance.net > 0) "+" else "") + balance.net.formatMoney(),
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.onPrimaryContainer,
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        BalanceAmount("You're owed", balance.owedToYou, Modifier.weight(1f))
                        BalanceAmount("You owe", balance.youOwe, Modifier.weight(1f))
                    }
                }
                if (groups.isNotEmpty()) Card(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                    Text("Scan receipt", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Groups", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onHistory) { Text("Bill history") }
                }
            }
        }
        items(groups, key = { it.id }) { group ->
            Card(
                onClick = { onOpenGroup(group.id) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, PouchColors.Border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(group.icon, style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(group.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${group.members.size} members · ${group.receipts.size} receipts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(group.totalSpentCents.formatMoney(), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        item {
            TextButton(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("New group")
            }
        }
    }
}


/** Shows one side of the account's balance. */
@Composable
private fun BalanceAmount(label: String, cents: Long, modifier: Modifier) {
    val textColor = MaterialTheme.colorScheme.onPrimaryContainer
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = textColor)
        Text(cents.formatMoney(), style = MaterialTheme.typography.titleMedium, color = textColor)
    }
}
