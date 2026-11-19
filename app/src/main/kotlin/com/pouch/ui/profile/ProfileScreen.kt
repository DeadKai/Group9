
package com.pouch.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pouch.domain.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Profile") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
                actions = {
                    TextButton(onClick = viewModel::refresh) {
                        Text("Refresh")
                    }
                }
            )
        }
    ) { padding ->
        if (state.loading && state.profile == null) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        state.profile?.fullName ?: "My profile",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(state.profile?.email.orEmpty())
                }

                item {
                    OutlinedButton(
                        onClick = {
                            name = state.profile?.fullName.orEmpty()
                            editing = true
                        }
                    ) {
                        Text("Edit profile")
                    }
                }

                item {
                    HorizontalDivider()
                    Text(
                        "Find friends",
                        style = MaterialTheme.typography.titleLarge
                    )
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        label = { Text("Email address") },
                        supportingText = { Text("Enter the email they use to sign in.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        enabled = !state.searching,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { viewModel.search(search) },
                        enabled = search.isNotBlank() && !state.busy && !state.searching
                    ) {
                        Text(if (state.searching) "Searching…" else "Search")
                    }
                    state.searchMessage?.let { message ->
                        Text(
                            text = message,
                            color = if (state.searchFailed) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                items(state.results, key = { "result_${it.uid}" }) { user ->
                    PersonRow(
                        user = user,
                        action = "Add friend",
                        subtitle = user.email,
                        enabled = !state.busy &&
                                state.friends.none { it.uid == user.uid } &&
                                state.outgoing.none { it.receiverId == user.uid } &&
                                state.incoming.none { it.senderId == user.uid },
                        onAction = { viewModel.addFriend(user) }
                    )
                }

                item {
                    HorizontalDivider()
                    Text(
                        "Friend requests (${state.incoming.size})",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                items(state.incoming, key = { "incoming_${it.id}" }) { request ->
                    val sender = state.requestUsers[request.senderId]
                    Column {
                        Text(sender?.fullName ?: request.senderId)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = !state.busy,
                                onClick = { viewModel.accept(request) }
                            ) { Text("Accept") }
                            OutlinedButton(
                                enabled = !state.busy,
                                onClick = { viewModel.reject(request) }
                            ) { Text("Reject") }
                        }
                    }
                }

                item {
                    Text(
                        "Sent requests (${state.outgoing.size})",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                items(state.outgoing, key = { "outgoing_${it.id}" }) { request ->
                    val receiver = state.requestUsers[request.receiverId]
                    Column {
                        Text(receiver?.fullName ?: request.receiverId)
                        OutlinedButton(
                            enabled = !state.busy,
                            onClick = { viewModel.cancel(request) }
                        ) { Text("Cancel request") }
                    }
                }

                item {
                    HorizontalDivider()
                    Text(
                        "My friends (${state.friends.size})",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                items(state.friends, key = { "friend_${it.uid}" }) { friend ->
                    PersonRow(
                        user = friend,
                        action = "Remove",
                        enabled = !state.busy,
                        onAction = { viewModel.remove(friend) }
                    )
                }

                item {
                    state.message?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Edit profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Full name") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !state.busy,
                    onClick = {
                        viewModel.saveProfile(name)
                        editing = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PersonRow(
    user: User,
    action: String,
    enabled: Boolean,
    onAction: () -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(user.fullName.ifBlank { "Pouch user" })
            subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        OutlinedButton(
            enabled = enabled,
            onClick = onAction
        ) {
            Text(action)
        }
    }
}
