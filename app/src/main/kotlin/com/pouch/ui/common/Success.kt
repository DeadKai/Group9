package com.pouch.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pouch.ui.theme.PouchColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A single successful action and its optional details. */
internal class SuccessNotice(val title: String, val details: String?)

/** Publishes success notices that remain visible across screen changes. */
class SuccessController {
    private val current = MutableStateFlow<SuccessNotice?>(null)
    internal val notice = current.asStateFlow()

    /** Displays a confirmation for a completed action. */
    fun show(title: String, details: String? = null) {
        current.value = SuccessNotice(title, details)
    }

    /** Clears the displayed notice if a newer one has not replaced it. */
    internal fun dismiss(notice: SuccessNotice) {
        current.compareAndSet(notice, null)
    }
}

/** Displays a temporary confirmation above the app's navigation content. */
@Composable
fun SuccessHost(controller: SuccessController, content: @Composable () -> Unit) {
    val notice by controller.notice.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        content()
        notice?.let { currentNotice ->
            LaunchedEffect(controller, currentNotice) {
                delay(3_000)
                controller.dismiss(currentNotice)
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 4.dp,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = currentNotice.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = PouchColors.Success,
                    )
                    currentNotice.details?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}
