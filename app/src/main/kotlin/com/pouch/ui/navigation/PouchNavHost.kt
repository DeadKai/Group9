package com.pouch.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import com.pouch.ui.auth.AuthScreen
import com.pouch.ui.auth.AuthViewModel
import com.pouch.ui.group.GroupScreen
import com.pouch.ui.profile.ProfileScreen
import com.pouch.ui.history.BillHistoryScreen
import com.pouch.ui.home.HomeScreen
import com.pouch.ui.scan.ScanScreen
import com.pouch.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
object SettingsRoute
/** [groupId] = null when opened from Home (the user picks the group on the Scan screen). */
@Serializable
data class ScanRoute(val groupId: String? = null)

@Serializable
data class GroupRoute(val groupId: String, val receiptId: String? = null)
@Serializable
object ProfileRoute

/** Opens the combined receipt history for the groups shown on Home. */
@Serializable
object BillHistoryRoute

/** Creates a fresh set of destinations when the active account changes. */
@Composable
fun PouchNavHost(authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory)) {

    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()

    // FIXME: REMOVE DEV LOGIN BYPASS BEFORE FINAL SUBMISSION
    if (currentUser == null && !authViewModel.isBypassed) {
        AuthScreen(viewModel = authViewModel)
        return
    }

    key(currentUser?.uid) {
        PouchDestinations(authViewModel)
    }
}

/** Hosts the screens available to the active account. */
@Composable
private fun PouchDestinations(authViewModel: AuthViewModel) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val destination = entry?.destination
    val homeSelected = destination?.hasRoute<HomeRoute>() == true
    val profileSelected = destination?.hasRoute<ProfileRoute>() == true
    val settingsSelected = destination?.hasRoute<SettingsRoute>() == true

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (homeSelected || profileSelected || settingsSelected) {
                NavigationBar {
                    NavigationBarItem(
                        selected = homeSelected,
                        onClick = { navController.popBackStack<HomeRoute>(inclusive = false) },
                        icon = { Icon(Icons.Filled.Home, null) },
                        label = { Text("Home") },
                    )
                    NavigationBarItem(
                        selected = profileSelected,
                        onClick = {
                            if (!profileSelected) {
                                navController.navigate(ProfileRoute) {
                                    popUpTo<HomeRoute>()
                                    launchSingleTop = true
                                }
                            }
                        },
                        icon = { Icon(Icons.Filled.AccountCircle, null) },
                        label = { Text("Profile") },
                    )
                    NavigationBarItem(
                        selected = settingsSelected,
                        onClick = {
                            if (!settingsSelected) {
                                navController.navigate(SettingsRoute) {
                                    popUpTo<HomeRoute>()
                                    launchSingleTop = true
                                }
                            }
                        },
                        icon = { Icon(Icons.Filled.Settings, null) },
                        label = { Text("Settings") },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    onOpenGroup = { groupId -> navController.navigate(GroupRoute(groupId)) },
                    onScan = { navController.navigate(ScanRoute()) },
                    onOpenBillHistory = { navController.navigate(BillHistoryRoute) },
                )
            }

            composable<BillHistoryRoute> {
                BillHistoryScreen(
                    onBack = { navController.popBackStack() },
                    onOpenReceipt = { groupId, receiptId ->
                        navController.navigate(GroupRoute(groupId, receiptId))
                    },
                )
            }

            composable<GroupRoute> { entry ->
                val route = entry.toRoute<GroupRoute>()
                GroupScreen(
                    groupId = route.groupId,
                    initialReceiptId = route.receiptId,
                    onBack = { navController.popBackStack() },
                    onAddReceipt = { navController.navigate(ScanRoute(route.groupId)) },
                )
            }

            composable<ScanRoute> { entry ->
                ScanScreen(
                    presetGroupId = entry.toRoute<ScanRoute>().groupId,
                    // Back and "saved" both return to where Scan was opened from (Home or Group).
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }

            composable<ProfileRoute> {
                ProfileScreen(
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable<SettingsRoute> {
                SettingsScreen(
                    onBack = {
                        navController.popBackStack()
                    },
                    onProfile = {
                        navController.navigate(ProfileRoute)
                    },
                    onSignOut = {
                        // Return to Home before signing out
                        navController.popBackStack(HomeRoute, inclusive = false)
                        authViewModel.signOut()
                    }
                )
            }
        }
    }
}
