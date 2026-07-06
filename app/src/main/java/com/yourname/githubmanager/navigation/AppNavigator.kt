// File: app/src/main/java/com/yourname/githubmanager/navigation/AppNavigator.kt
package com.yourname.githubmanager.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yourname.githubmanager.ui.screens.settings.SettingsScreen
import com.yourname.githubmanager.ui.screens.workspace.MainWorkspaceScreen

/**
 * Top-level navigation graph: Workspace <-> Settings.
 *
 * Deliberately does NOT touch AppPreferences (or any of its keys) directly —
 * repo owner/repoName/branch are read where they're actually needed
 * (SettingsViewModel, MainWorkspaceViewModel) via
 * AppPreferences.getRepoInfoFlow(context), which returns the public
 * RepoConfig data class. Navigation only routes between screens; it has no
 * business reading or knowing about DataStore keys.
 */
@Composable
fun AppNavigator() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Workspace.route
    ) {
        composable(route = Screen.Workspace.route) {
            MainWorkspaceScreen(
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(route = Screen.Settings.route) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
