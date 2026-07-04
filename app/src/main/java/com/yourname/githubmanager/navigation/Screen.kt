// File: app/src/main/java/com/yourname/githubmanager/navigation/Screen.kt
package com.yourname.githubmanager.navigation

/**
 * Sealed class representing all navigation destinations.
 * Currently only contains the Workspace screen for Phase 1.
 * Will be extended in later phases (e.g., Settings, etc.).
 */
sealed class Screen(val route: String) {
    object Workspace : Screen("workspace")
}
