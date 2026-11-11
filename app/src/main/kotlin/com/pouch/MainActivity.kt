package com.pouch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pouch.data.local.AppDatabase
import com.pouch.data.repository.CurrencyRepository
import com.pouch.data.repository.SessionRepository
import com.pouch.ui.detail.GroupDetailScreen
import com.pouch.ui.detail.GroupDetailViewModel
import com.pouch.ui.home.HomeScreen
import com.pouch.ui.home.HomeViewModel
import com.pouch.ui.login.LoginScreen
import com.pouch.ui.login.LoginViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = AppDatabase.getDatabase(applicationContext)
        val groupDao = db.groupDao()
        val expenseDao = db.expenseDao()
        val sessionRepository = SessionRepository(applicationContext)
        val currencyRepository = CurrencyRepository(applicationContext)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val isLoggedIn by sessionRepository.isLoggedIn.collectAsStateWithLifecycle()
                    val startDestination = if (isLoggedIn) "home" else "login"

                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
                        composable("login") {
                            val loginViewModel = LoginViewModel(sessionRepository)
                            LoginScreen(
                                viewModel = loginViewModel,
                                onLoginSuccess = {
                                    navController.navigate("home") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("home") {
                            val homeViewModel = HomeViewModel(groupDao, sessionRepository)
                            HomeScreen(
                                viewModel = homeViewModel,
                                onGroupClick = { groupId ->
                                    navController.navigate("group_detail/$groupId")
                                },
                                onLogout = {
                                    navController.navigate("login") {
                                        popUpTo("home") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(
                            route = "group_detail/{groupId}",
                            arguments = listOf(navArgument("groupId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val groupId = backStackEntry.arguments?.getLong("groupId") ?: 0L
                            val detailViewModel = GroupDetailViewModel(
                                groupId = groupId,
                                groupDao = groupDao,
                                expenseDao = expenseDao,
                                currencyRepository = currencyRepository
                            )
                            GroupDetailScreen(
                                viewModel = detailViewModel,
                                onBackClick = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
