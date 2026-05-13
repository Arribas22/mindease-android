package com.bravosix.mindease

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bravosix.mindease.ui.*

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result handled by OS */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Request POST_NOTIFICATIONS on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            MindEaseTheme {
                MindEaseApp()
            }
        }
    }
}

private sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home      : Screen("home",      "Inicio",      Icons.Default.Home)
    object Chat      : Screen("chat",      "Chat IA",     Icons.Default.Chat)
    object Journal   : Screen("journal",   "Diario",      Icons.Default.MenuBook)
    object Insights  : Screen("insights",  "Tendencias",  Icons.Default.Insights)
    object Exercises : Screen("exercises", "Ejercicios",  Icons.Default.FitnessCenter)
    object Tokens    : Screen("tokens",    "Mi cuenta",   Icons.Default.AccountCircle)
}

private val bottomNavItems = listOf(
    Screen.Home, Screen.Chat, Screen.Journal, Screen.Insights, Screen.Tokens
)

@Composable
private fun MindEaseApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        icon    = { Icon(screen.icon, contentDescription = screen.label) },
                        label   = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = Screen.Home.route,
            modifier         = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route)      { HomeScreen(navController) }
            composable(Screen.Chat.route)      { ChatScreen() }
            composable(Screen.Journal.route)   { JournalScreen() }
            composable(Screen.Insights.route)  { InsightsScreen() }
            composable(Screen.Exercises.route) { ExercisesScreen() }
            composable(Screen.Tokens.route)    { TokensScreen() }
        }
    }
}
