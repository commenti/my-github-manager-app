// File: app/src/main/java/com/yourname/githubmanager/navigation/Screen.kt
package com.yourname.githubmanager.navigation

import android.net.Uri

/**
 * Sealed class representing all navigation destinations.
 * Currently only contains the Workspace screen for Phase 1.
 * Will be extended in later phases (e.g., Settings, etc.).
 */
sealed class Screen(val route: String) {
    object Workspace : Screen("workspace")
    object Settings : Screen("settings")
    object Editor : Screen("editor/{filePath}") {
        fun createRoute(filePath: String): String {
            // filePath is already Uri-encoded by the caller (AppNavigator);
            // do NOT re-encode here to avoid double-encoding.
            return "editor/$filePath"
        }
    }
}
