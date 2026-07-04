// File: app/src/main/java/com/yourname/githubmanager/ui/screens/workspace/MainWorkspaceScreen.kt
package com.yourname.githubmanager.ui.screens.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourname.githubmanager.ui.components.BottomAdBanner
import com.yourname.githubmanager.ui.components.GitHubManagerTopAppBar
import com.yourname.githubmanager.ui.theme.GitHubManagerTheme
import kotlinx.coroutines.launch

/**
 * Main workspace screen — the only screen in Phase 1.
 *
 * Layout:
 *  - [Scaffold] with a [GitHubManagerTopAppBar] at the top
 *  - A fixed [BottomAdBanner] at the bottom (rendered inside the bottomBar slot)
 *  - Two centred buttons ("Import File" / "Import Folder") in the content area
 *
 * Design decisions for future-proofing:
 *  - Both button callbacks arrive as lambda parameters ([onImportFile], [onImportFolder])
 *    so real SAF logic can be injected without touching this composable.
 *  - Default values are empty lambdas — the screen is safe to preview in isolation.
 *
 * @param viewModel       Injected via Compose ViewModel factory; can be replaced
 *                        with a fake in tests.
 * @param onImportFile    Called when "Import File" is tapped. Phase 1 default shows Snackbar.
 * @param onImportFolder  Called when "Import Folder" is tapped. Phase 1 default shows Snackbar.
 */
@Composable
fun MainWorkspaceScreen(
    viewModel: MainWorkspaceViewModel = viewModel(),
    onImportFile: () -> Unit = {},
    onImportFolder: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Message shown for any button press in Phase 1
    val phaseMessage = "Feature coming in Phase 2"

    val handleImportFile: () -> Unit = {
        onImportFile()
        coroutineScope.launch {
            snackbarHostState.showSnackbar(phaseMessage)
        }
    }

    val handleImportFolder: () -> Unit = {
        onImportFolder()
        coroutineScope.launch {
            snackbarHostState.showSnackbar(phaseMessage)
        }
    }

    Scaffold(
        topBar = {
            GitHubManagerTopAppBar()
        },
        bottomBar = {
            BottomAdBanner(
                modifier = Modifier.fillMaxWidth()
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { innerPadding ->
        WorkspaceContent(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            onImportFile = handleImportFile,
            onImportFolder = handleImportFolder
        )
    }
}

/**
 * The actual content area: two vertically centred, full-width buttons.
 *
 * Extracted into its own composable so it can be independently previewed
 * and reused or replaced in future phases without touching [MainWorkspaceScreen].
 */
@Composable
private fun WorkspaceContent(
    modifier: Modifier = Modifier,
    onImportFile: () -> Unit = {},
    onImportFolder: () -> Unit = {}
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Button(
                onClick = onImportFile,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Import File")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onImportFolder,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Import Folder")
            }
        }
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Workspace Content Light")
@Composable
private fun WorkspaceContentPreview() {
    GitHubManagerTheme {
        WorkspaceContent()
    }
}
