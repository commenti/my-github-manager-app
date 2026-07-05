// File: app/src/main/java/com/yourname/githubmanager/navigation/AppNavigator.kt
package com.yourname.githubmanager.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yourname.githubmanager.ui.screens.workspace.MainWorkspaceScreen

/**
 * Root NavHost for the application.
 *
 * Phase 1: A single route — [Screen.Workspace] — is registered.
 * Adding a new screen in future phases only requires:
 *   1. Adding a new [Screen] subclass.
 *   2. Adding a new `composable { }` block below.
 */
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

    composable(route = Screen.Settings.route) {
        SettingsScreen(
            onBackClick = {
                navController.popBackStack()
            }
        )
    }

    // ── Future routes ─────────────────────────────────────────────
    // composable(route = Screen.Splash.route) { SplashScreen(navController) }
    }
