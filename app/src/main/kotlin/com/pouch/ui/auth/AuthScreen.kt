package com.pouch.ui.auth
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.pouch.R
import com.pouch.ui.common.LoadingButton
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    viewModel: AuthViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val webClientId = stringResource(id = R.string.default_web_client_id)

    var isSignUp by remember { mutableStateOf(false) }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isSignUp) "Create Pouch Account" else "WELCOME",
            style = MaterialTheme.typography.headlineMedium

        )

        Spacer(modifier = Modifier.height(24.dp))

        if (isSignUp) {
            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text("Full Name") },
                singleLine = true,
                enabled = !viewModel.isLoading,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            enabled = !viewModel.isLoading,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password (6+ chars)") },
            singleLine = true,
            enabled = !viewModel.isLoading,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        if (viewModel.errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = viewModel.errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Shows a spinner + "Signing in…" while Firebase works. The Google button
        // stays normal, so the user can see which action is running.
        LoadingButton(
            text = if (isSignUp) "Sign Up" else "Sign In",
            loadingText = if (isSignUp) "Creating account…" else "Signing in…",
            isLoading = viewModel.loadingAction == AuthAction.EMAIL,
            enabled = !viewModel.isLoading && email.isNotBlank() && password.length >= 6 &&
                    (!isSignUp || fullName.isNotBlank()),
            onClick = {
                if (isSignUp) {
                    viewModel.signUp(fullName, email, password)
                } else {
                    viewModel.signIn(email, password)
                }
            },
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = {
                viewModel.startGoogleSignIn()
                scope.launch {
                    requestGoogleIdToken(context, webClientId)
                        .onSuccess { token -> viewModel.signInWithGoogle(token) }
                        .onFailure { err ->
                            // Closing the account picker is not an error, so show no message then.
                            viewModel.googleSignInFailed(
                                if (err is GetCredentialCancellationException) null
                                else err.localizedMessage ?: "Google sign-in failed"
                            )
                        }
                }
            },
            enabled = !viewModel.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            if (viewModel.loadingAction == AuthAction.GOOGLE) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Connecting to Google…")
            } else {
                Text("Continue with Google")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = { isSignUp = !isSignUp }) {
            Text(if (isSignUp) "Already have an account? Sign In" else "Need an account? Sign Up")
        }

        // FIXME: REMOVE DEV LOGIN BYPASS
        TextButton(onClick = { viewModel.bypassLogin() }) {
            Text("Skip Login (Dev Bypass)")
        }
    }
}

private suspend fun requestGoogleIdToken(
    context: Context,
    webClientId: String
): Result<String> = runCatching {
    val credentialManager = CredentialManager.create(context)
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(webClientId)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    val result = credentialManager.getCredential(context, request)
    val credential = result.credential
    if (credential is CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
        GoogleIdTokenCredential.createFrom(credential.data).idToken
    } else {
        error("Unexpected credential type")
    }
}