package com.pouch.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pouch.ui.theme.PouchColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration.Companion.milliseconds

/** One success message, e.g. "Group created" + "Roommates is ready". */
data class SuccessMessage(
    val title: String,
    val detail: String? = null,
    val id: Long = System.nanoTime(),   // makes two identical messages in a row still show twice
)

/**
 * App-wide success feedback. Any ViewModel calls [show]; [SuccessHost] (in MainActivity)
 * draws the same checkmark overlay on top of whatever screen is open.
 *
 * Because it lives above the navigation, a screen can navigate away right after an action
 * (e.g. "Group deleted" -> back to Home) and the confirmation still stays visible.
 */
class SuccessController {
    private val _current = MutableStateFlow<SuccessMessage?>(null)
    val current: StateFlow<SuccessMessage?> = _current.asStateFlow()

    fun show(title: String, detail: String? = null) {
        _current.value = SuccessMessage(title, detail)
    }

    fun dismiss() {
        _current.value = null
    }
}

private val AUTO_HIDE = 1_600.milliseconds

/** Wrap the whole app in this once. Shows the success overlay above [content]. */
@Composable
fun SuccessHost(controller: SuccessController, content: @Composable () -> Unit) {
    val message by controller.current.collectAsStateWithLifecycle()

    // Keep the last message so its text stays visible during the fade-out animation.
    var lastMessage by remember { mutableStateOf<SuccessMessage?>(null) }
    LaunchedEffect(message) { if (message != null) lastMessage = message }

    // Hide automatically. A new message restarts the timer.
    LaunchedEffect(message?.id) {
        if (message != null) {
            delay(AUTO_HIDE)
            controller.dismiss()
        }
    }

    Box(Modifier.fillMaxSize()) {
        content()

        AnimatedVisibility(
            visible = message != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            // Registered only while the overlay is shown, so it takes priority over the
            // screen's own Back handling: Back closes the overlay instead of leaving the screen.
            BackHandler { controller.dismiss() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    // Tap anywhere to close early.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { controller.dismiss() },
                contentAlignment = Alignment.Center,
            ) {
                lastMessage?.let { SuccessCard(it) }
            }
        }
    }
}

@Composable
private fun SuccessCard(message: SuccessMessage) {
    // Checkmark "pops" in with a little bounce.
    val checkScale = remember(message.id) { Animatable(0.3f) }
    LaunchedEffect(message.id) {
        checkScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .padding(32.dp)
            .widthIn(max = 320.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 32.dp, vertical = 28.dp)
                // Screen readers announce the message.
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(PouchColors.Success.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = PouchColors.Success,
                    modifier = Modifier
                        .size(40.dp)
                        .graphicsLayer {
                            scaleX = checkScale.value
                            scaleY = checkScale.value
                        },
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(message.title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            message.detail?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
