package com.pouch.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.pouch.ui.common.LoadingButton

/** Signs in to Pouch or creates an account with a display name. */
@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    var isSignUp by rememberSaveable { mutableStateOf(false) }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Column(Modifier.widthIn(max = 440.dp).fillMaxWidth().padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = MaterialTheme.shapes.medium, color = colors.primary) {
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { Text("P") }
                }
                Text("Pouch", style = MaterialTheme.typography.titleMedium)
            }

        }

        Surface(
            modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = colors.surface,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(!isSignUp, {
                        isSignUp = false
                        viewModel.setError(null)
                    }, SegmentedButtonDefaults.itemShape(0, 2), enabled = !viewModel.isLoading, icon = {}) {
                        Text("Log in")
                    }
                    SegmentedButton(isSignUp, {
                        isSignUp = true
                        viewModel.setError(null)
                    }, SegmentedButtonDefaults.itemShape(1, 2), enabled = !viewModel.isLoading, icon = {}) {
                        Text("Sign up")
                    }
                }

                if (isSignUp) {
                    AuthField("Full name", fullName, { fullName = it }, !viewModel.isLoading)
                }
                AuthField("Email", email, { email = it }, !viewModel.isLoading, KeyboardType.Email)
                AuthField(
                    "Password", password, { password = it }, !viewModel.isLoading, KeyboardType.Password,
                    transformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailing = {
                        TextButton(onClick = { showPassword = !showPassword }) {
                            Text(if (showPassword) "Hide" else "Show")
                        }
                    },
                )

                viewModel.errorMessage?.let {
                    Text(it, color = colors.error, style = MaterialTheme.typography.bodySmall)
                }
                LoadingButton(
                    text = if (isSignUp) "Create account" else "Log in",
                    loadingText = if (isSignUp) "Creating account…" else "Logging in…",
                    isLoading = viewModel.isLoading,
                    enabled = email.isNotBlank() && password.length >= 6 && (!isSignUp || fullName.isNotBlank()),
                    onClick = {
                        if (isSignUp) viewModel.signUp(fullName, email, password)
                        else viewModel.signIn(email, password)
                    },
                )
                HorizontalDivider()
                // Keep the existing bypass available for development.
                TextButton(onClick = viewModel::bypassLogin, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Skip login (dev bypass)")
                }
            }
        }
    }
}

/** Shows a labeled input using the authentication form palette. */
@Composable
private fun AuthField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    transformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = transformation,
        trailingIcon = trailing,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceVariant,
            unfocusedContainerColor = colors.surfaceVariant,
        ),
    )
}
