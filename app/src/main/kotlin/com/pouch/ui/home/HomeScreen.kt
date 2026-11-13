package com.pouch.ui.home

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pouch.domain.Group
import com.pouch.domain.formatMoney
import com.pouch.ui.common.EmptyView
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
    onSignOut: () -> Unit = {},
    viewModel: HomeViewModel = pouchViewModel { HomeViewModel(it.groupRepository) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCreateGroup by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pouch") },
                actions = {
                    IconButton(onClick = { showCreateGroup = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "New group")
                    }
                    TextButton(onClick = onSignOut) {
                        Text("Log out")
                    }
                },
            )
        },
        floatingActionButton = {
            if (state is UiState.Success) {
                // Scanning a receipt is the main action of the app, so it gets the big button.
                ExtendedFloatingActionButton(
                    onClick = onScan,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Scan receipt") },
                )
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (val s = state) {
            UiState.Loading -> LoadingView(modifier)
            UiState.Empty -> EmptyView(
                title = "No groups yet",
                message = "Create a group for your roommates, trip or dinner and start adding receipts.",
                actionLabel = "Create your first group",
                onAction = { showCreateGroup = true },
                modifier = modifier,
            )
            is UiState.Error -> ErrorView(s.message, onRetry = {}, modifier = modifier)
            is UiState.Success -> GroupList(s.data, onOpenGroup, modifier)
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

@Composable
private fun GroupList(groups: List<Group>, onOpenGroup: (String) -> Unit, modifier: Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(groups, key = { it.id }) { group ->
            Card(
                onClick = { onOpenGroup(group.id) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, PouchColors.Border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(group.icon, style = MaterialTheme.typography.headlineSmall)
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
    }
}
