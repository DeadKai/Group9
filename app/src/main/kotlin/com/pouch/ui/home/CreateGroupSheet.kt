package com.pouch.ui.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pouch.domain.GroupIcon
import com.pouch.ui.common.ValidatedTextField
import com.pouch.ui.common.pouchViewModel

/**
 * "New group" bottom sheet on the Home screen (design system: mobile-style bottom sheets).
 * Not a separate screen, so the app has exactly three screens: Home, Scan, Group.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupSheet(
    onDismiss: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: CreateGroupViewModel = pouchViewModel { CreateGroupViewModel(it.groupRepository, it.success) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(state.createdGroupId) {
        state.createdGroupId?.let { id ->
            viewModel.reset()
            onCreated(id)
        }
    }

    ModalBottomSheet(
        onDismissRequest = { viewModel.reset(); onDismiss() },
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text("New group", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))

            ValidatedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = "Group name",
                error = state.nameError,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )

            Text("Icon", style = MaterialTheme.typography.labelLarge)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GroupIcon.entries.forEach { icon ->
                    FilterChip(
                        selected = icon == state.icon,
                        onClick = { viewModel.onIconSelected(icon) },
                        label = { Text(icon.emoji) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Members", style = MaterialTheme.typography.labelLarge)
            Text(
                "You are added automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                ValidatedTextField(
                    value = state.newMemberName,
                    onValueChange = viewModel::onNewMemberChange,
                    label = "Name",
                    error = state.memberError,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                IconButton(onClick = viewModel::addMember, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Add member")
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.members.forEach { name ->
                    InputChip(
                        selected = false,
                        onClick = { viewModel.removeMember(name) },
                        label = { Text(name) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove $name") },
                    )
                }
            }

            state.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (state.isSaving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Create group")
            }
        }
    }
}
