// File: app/src/main/java/com/yourname/githubmanager/navigation/Screen.kt
package com.yourname.githubmanager.navigation

/**
 * Sealed class defining all navigation destinations in the app.
 *
 * Phase 1: Only [Workspace] exists.
 * Future phases will add Splash, Settings, etc. here without
 * modifying any other file.
 */
sealed class Screen(val route: String) {

    /** Main workspace screen — the landing screen for Phase 1. */
    data object Workspace : Screen("workspace")

    // ── Future routes (add here in later phases) ─────────────────────
    // data object Splash   : Screen("splash")
    // data object Settings : Screen("settings")
}
