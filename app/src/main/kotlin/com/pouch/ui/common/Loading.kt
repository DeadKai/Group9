package com.pouch.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Full-screen loading state, e.g. "Reading receipt…" or "Signing in…".
 * [steps] rotate every 1.5 s so the user can see that something is happening.
 */
@Composable
fun LoadingScreen(
    title: String,
    modifier: Modifier = Modifier,
    steps: List<String> = emptyList(),
    onCancel: (() -> Unit)? = null,
) {
    var stepIndex by remember { mutableIntStateOf(0) }
    if (steps.size > 1) {
        LaunchedEffect(steps) {
            while (true) {
                delay(1_500.milliseconds)
                stepIndex = (stepIndex + 1) % steps.size
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(Modifier.size(56.dp), strokeWidth = 4.dp)
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)

        if (steps.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = steps[stepIndex],
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                // Screen readers announce the step changes.
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        if (onCancel != null) {
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

/** Primary button that shows a small spinner and is disabled while [isLoading] (or when [enabled] is false). */
@Composable
fun LoadingButton(
    text: String,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loadingText: String = text,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = LocalContentColor.current,
            )
            Spacer(Modifier.width(12.dp))
            Text(loadingText)
        } else {
            Text(text)
        }
    }
}