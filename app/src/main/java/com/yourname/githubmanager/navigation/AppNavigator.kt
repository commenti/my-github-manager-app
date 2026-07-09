// File: app/src/main/java/com/yourname/githubmanager/navigation/AppNavigator.kt
package com.yourname.githubmanager.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yourname.githubmanager.data.filesystem.LocalFileSystem
import com.yourname.githubmanager.data.filesystem.ProjectFileSystem
import com.yourname.githubmanager.data.filesystem.SafFileSystem
import com.yourname.githubmanager.domain.FileNode
import com.yourname.githubmanager.ui.screens.editor.FileEditorScreen
import com.yourname.githubmanager.ui.screens.editor.FileEditorViewModel
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
                },
                onFileClick = { node ->
                    // TODO: Verify exact field name for full path; currently using node.path
                    val encodedPath = Uri.encode(node.path)
                    navController.navigate(Screen.Editor.createRoute(encodedPath))
                }
            )
        }

        composable(route = Screen.Settings.route) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        // Editor screen
        composable(
            route = Screen.Editor.route,
            arguments = listOf(
                navArgument("filePath") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val filePath = backStackEntry.arguments?.getString("filePath") ?: return@composable
            val decodedPath = filePath

            // TODO: Implement a proper shared repository/cache to resolve FileNode from path.
            // Currently using a placeholder that returns a minimal dummy node.
            val fileNode = resolveFileNode(decodedPath)

            val context = LocalContext.current

            // TODO: Determine the correct ProjectFileSystem implementation based on project state.
            val fileSystem: ProjectFileSystem =
                if (decodedPath.startsWith("content://")) SafFileSystem(context)
                else LocalFileSystem()

            // ViewModel factory because FileEditorViewModel requires constructor parameters
            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(FileEditorViewModel::class.java)) {
                        return FileEditorViewModel(fileNode, fileSystem) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }

            val editorViewModel: FileEditorViewModel = viewModel(factory = factory)

            FileEditorScreen(
                viewModel = editorViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}

/**
 * Placeholder function to resolve a [FileNode] from its file path.
 *
 * TODO: Replace this with a real implementation that retrieves the full
 * [FileNode] tree from a shared ViewModel or repository once available.
 * The implementation below creates a dummy leaf node and is only meant
 * to let the navigation compile and run temporarily.
 */
private fun resolveFileNode(path: String): FileNode {
    // Minimal dummy: assume it's a file, no children, no metadata.
    return FileNode(
        name = path.substringAfterLast('/'),
        path = path,
        isFolder = false,
        children = emptyList()
    )
}
