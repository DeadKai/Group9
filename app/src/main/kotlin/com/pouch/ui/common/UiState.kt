package com.pouch.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pouch.AppContainer
import com.pouch.PouchApp

/** One state type for every screen that shows loaded data. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data object Empty : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
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
