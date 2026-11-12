package com.pouch.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.pouch.ui.auth.AuthScreen
import com.pouch.ui.auth.AuthViewModel
import com.pouch.ui.group.GroupScreen
import com.pouch.ui.home.HomeScreen
import com.pouch.ui.scan.ScanScreen
import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

/** [groupId] = null when opened from Home (the user picks the group on the Scan screen). */
@Serializable
data class ScanRoute(val groupId: String? = null)

@Serializable
data class GroupRoute(val groupId: String)

@Composable
fun PouchNavHost(navController: NavHostController = rememberNavController(),
                 authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory)) {

    val currentUser by authViewModel.currentUser.collectAsState()

    // FIXME: REMOVE DEV LOGIN BYPASS BEFORE FINAL SUBMISSION
    if (currentUser == null && !authViewModel.isBypassed) {
        AuthScreen(viewModel = authViewModel)
        return
    }

    NavHost(navController = navController, startDestination = HomeRoute) {

        composable<HomeRoute> {
            HomeScreen(
                onOpenGroup = { groupId -> navController.navigate(GroupRoute(groupId)) },
                onScan = { navController.navigate(ScanRoute()) },
                onSignOut = { authViewModel.signOut() },
            )
        }

        composable<GroupRoute> { entry ->
            val groupId = entry.toRoute<GroupRoute>().groupId
            GroupScreen(
                groupId = groupId,
                onBack = { navController.popBackStack() },
                onAddReceipt = { navController.navigate(ScanRoute(groupId)) },
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
    }
}
