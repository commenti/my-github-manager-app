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
    onSettingsClick: () -> Unit = {}
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
                    FileTreeItem(node = tree)
                }
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
