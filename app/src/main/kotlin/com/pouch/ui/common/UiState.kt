package com.pouch.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pouch.AppContainer
import com.pouch.PouchApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

/** One state type for every screen that shows loaded data. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data object Empty : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

/**
 * Shows [message] when loading fails, then tries again with backoff (1s, 2s, 4s ... max 30s).
 * A plain catch would end the flow: one failed listener (e.g. a permission error while
 * signing out, or a dropped connection) would leave the screen stuck on the error.
 */
fun <T> Flow<UiState<T>>.showErrorAndRetry(message: String): Flow<UiState<T>> =
    retryWhen { cause, attempt ->
        if (cause is CancellationException) return@retryWhen false
        emit(UiState.Error(message))
        delay(minOf(30_000L, 1_000L shl attempt.coerceAtMost(5).toInt()).milliseconds)
        true
    }

/**
 * Creates a ViewModel with access to the app's shared objects ([AppContainer]).
 * The ViewModel lives as long as the screen is in the back stack and survives rotation.
 */
@Composable
inline fun <reified VM : ViewModel> pouchViewModel(crossinline create: (AppContainer) -> VM): VM {
    val app = LocalContext.current.applicationContext as PouchApp
    return viewModel(factory = viewModelFactory { initializer { create(app.container) } })
}
