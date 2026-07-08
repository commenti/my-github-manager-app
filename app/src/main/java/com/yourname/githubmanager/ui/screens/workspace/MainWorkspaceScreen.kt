package com.yourname.githubmanager.ui.screens.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourname.githubmanager.data.filesystem.rememberSafHelper
import com.yourname.githubmanager.domain.FileNode
import com.yourname.githubmanager.ui.components.FileTreeItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainWorkspaceScreen(
    onSettingsClick: () -> Unit = {},
    onFileClick: (FileNode) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val viewModel: MainWorkspaceViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // SAF helper – launches system pickers and feeds URIs to the ViewModel
    val safHelper = rememberSafHelper(
        snackbarHostState = snackbarHostState,
        onFilePicked = { uri -> viewModel.onFilePicked(uri, context) },
        onFolderPicked = { uri -> viewModel.onFolderPicked(uri, context) }
    )

    // Show error messages from ViewModel
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    // Show upload/commit success messages from ViewModel. Cleared right after
    // showing so a screen rotation / recomposition doesn't replay the same
    // Snackbar (clearSyncSuccessMessage() already exists on the ViewModel).
    LaunchedEffect(uiState.syncSuccessMessage) {
        uiState.syncSuccessMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSyncSuccessMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workspace") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        },
        bottomBar = {
            // Existing Phase 1 BottomAdBanner – unchanged
            // BottomAdBanner( ... )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Import buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { safHelper.launchFilePicker(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Import File")
                }
                Button(
                    onClick = { safHelper.launchFolderPicker() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Import Folder")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Extraction progress indicator
            if (uiState.isExtracting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }

            // File tree display
            uiState.fileTree?.let { tree ->
                Text(
                    text = "Files",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // TODO: FileTreeItem.kt needs to be updated to accept onFileClick before this compiles.
                    FileTreeItem(node = tree, onFileClick = onFileClick)
                }

                Spacer(Modifier.height(12.dp))

                // Upload / Commit Changes section — only ever shown once a
                // project is imported (we're inside fileTree?.let), driven
                // entirely by uiState.uploadButtonState / isSyncing.
                UploadSection(
                    uiState = uiState,
                    onUploadClick = { viewModel.onUploadClick(context) },
                    onCommitChangesClick = { viewModel.onCommitChangesClick(context) }
                )
            } ?: run {
                // Empty state
                Text(
                    text = "No folder imported yet. Use the buttons above to import a file or a folder.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Renders whichever of Upload / Commit Changes / syncing state currently
 * applies, based purely on [WorkspaceUiState.isSyncing] and
 * [WorkspaceUiState.uploadButtonState]. Kept as its own composable so the
 * three-way branching doesn't clutter [MainWorkspaceScreen].
 */
@Composable
private fun UploadSection(
    uiState: WorkspaceUiState,
    onUploadClick: () -> Unit,
    onCommitChangesClick: () -> Unit
) {
    if (uiState.isSyncing) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Syncing...")
        }
        return
    }

    when (val state = uiState.uploadButtonState) {
        is UploadButtonState.NotUploaded -> {
            Button(
                onClick = onUploadClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Upload to GitHub")
            }
        }

        is UploadButtonState.UploadedSameRepo -> {
            Button(
                onClick = onCommitChangesClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Commit Changes")
            }
        }

        is UploadButtonState.UploadedDifferentRepo -> {
            // Not currently produced by MainWorkspaceViewModel (sync state is
            // now looked up by the repo in Settings, not by folder identity —
            // see ProjectSyncStore), but handled here so this `when` stays
            // exhaustive and the warning UI is ready if that ever changes.
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "This project was previously linked to " +
                        "${state.previousOwner}/${state.previousRepoName}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onUploadClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Upload to GitHub")
                }
            }
        }
    }
}
