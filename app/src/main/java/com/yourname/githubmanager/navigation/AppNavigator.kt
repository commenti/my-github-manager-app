package com.yourname.githubmanager.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yourname.githubmanager.ui.screens.settings.SettingsScreen
import com.yourname.githubmanager.ui.screens.workspace.MainWorkspaceScreen

@Composable
fun AppNavigator() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Workspace.route
    ) {
        composable(route = Screen.Workspace.route) {
            MainWorkspaceScreen()
        }

        composable(route = Screen.Workspace.route) {
    MainWorkspaceScreen(
        onSettingsClick = {
            navController.navigate(Screen.Settings.route)
        }
    )
        }

        // Future routes here
        // composable(route = Screen.Splash.route) { SplashScreen(navController) }
    }
}
