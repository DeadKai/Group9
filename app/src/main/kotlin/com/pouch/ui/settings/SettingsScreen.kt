package com.pouch.ui.settings

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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pouch.ui.common.BackTopBar
import com.pouch.ui.common.pouchViewModel
import com.pouch.ui.theme.PouchColors

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    onProfile: () -> Unit,
    viewModel: SettingsViewModel = pouchViewModel { SettingsViewModel(it.authRepository) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showSignOutDialog by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }

    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) {
            null
        } ?: "1.0.0"
    }

    Scaffold(topBar = { BackTopBar("Settings", onBack) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SectionLabel("Account")
            SettingsCard(
                icon = Icons.Outlined.Person,
                title = "Personal information",
                subtitle = "View and update your details",
                onClick = onProfile,
                trailing = { Chevron() },
            )

            SectionLabel("Support")
            SettingsCard(
                icon = Icons.Outlined.Info,
                title = "Help & FAQ",
                subtitle = "Get assistance",
                onClick = { showHelp = true },
                trailing = { Chevron() },
            )

            Spacer(Modifier.height(24.dp))
            SettingsCard(
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                title = "Log out",
                subtitle = null,
                titleColor = PouchColors.Destructive,
                iconTint = PouchColors.Destructive,
                iconBackground = PouchColors.Destructive.copy(alpha = 0.15f),
                onClick = { showSignOutDialog = true },
            )

            Spacer(Modifier.height(24.dp))
            Text(
                "Pouch v$versionName · Made with care",
                style = MaterialTheme.typography.bodySmall,
                color = PouchColors.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
    if (showHelp) {
        HelpSheet(onDismiss = { showHelp = false })
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Log out?") },
            text = { Text("You will need to sign in again to see your groups.") },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutDialog = false
                    onSignOut()
                }) { Text("Log out", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = PouchColors.TextMuted,
        modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun Chevron() {
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = PouchColors.TextMuted,
    )
}

/** One rounded card row: icon box + title/subtitle + optional trailing content (chevron, switch). */
@Composable
private fun SettingsCard(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    iconBackground: Color = MaterialTheme.colorScheme.surfaceVariant,
    trailing: @Composable () -> Unit = {},
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val border = BorderStroke(1.dp, PouchColors.Border)
    val shape = MaterialTheme.shapes.large

    val content: @Composable () -> Unit = {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(iconBackground, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = titleColor)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            trailing()
        }
    }

    if (onClick != null) {
        Card(
            onClick = onClick,
            colors = colors,
            border = border,
            shape = shape,
            modifier = modifier.fillMaxWidth(),
        ) { content() }
    } else {
        Card(
            colors = colors,
            border = border,
            shape = shape,
            modifier = modifier.fillMaxWidth(),
        ) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonalInfoSheet(name: String?, email: String?, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text("Personal information", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            InfoLine("Name", name ?: "Not set")
            Spacer(Modifier.height(12.dp))
            InfoLine("Email", email ?: "Not signed in")
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = PouchColors.TextMuted)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HelpSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Help & FAQ", style = MaterialTheme.typography.titleMedium)
            Faq(
                "How do I add a receipt?",
                "Tap \"Scan receipt\" on the Home screen, take a photo or enter the details manually, then choose who paid and how it is split.",
            )
            Faq(
                "How are expenses split?",
                "Equally by default. Switch to \"Custom split\" to set an exact amount for each member.",
            )
            Faq(
                "Who can manage group members?",
                "The group owner can manage members. Removing a friend's access preserves their existing expenses.",
            )
        }
    }
}

@Composable
private fun Faq(question: String, answer: String) {
    Column {
        Text(question, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            answer,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
