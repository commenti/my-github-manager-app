package com.yourname.githubmanager.data.filesystem

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.launch

/**
 * Helper class that provides SAF launchers and handles permission denial gracefully.
 *
 * @property launchFilePicker Launches the file picker for a single file.
 * @property launchFolderPicker Launches the folder picker (document tree).
 */
class SafHelper(
    private val filePickerLauncher: (Array<String>) -> Unit,
    private val folderPickerLauncher: (Uri?) -> Unit,
    private val persistPermission: (Uri) -> Unit,
    private val onPermissionDenied: () -> Unit
) {
    /**
     * Opens the system file picker to select a single file.
     * @param mimeTypes MIME types to filter (e.g., arrayOf("*/*")).
     */
    fun launchFilePicker(mimeTypes: Array<String>) {
        filePickerLauncher(mimeTypes)
    }

    /**
     * Opens the system folder picker.
     */
    fun launchFolderPicker() {
        folderPickerLauncher(null)
    }

    /**
     * Persists read permission for the given URI.
     */
    fun persistUriPermission(uri: Uri) {
        persistPermission(uri)
    }

    /**
     * Invoked when the user cancels or denies the picker.
     */
    fun onPermissionDenied() {
        onPermissionDenied()
    }
}

/**
 * Creates and remembers a [SafHelper] that is tied to the Composition.
 * The helper automatically handles SAF launchers, persists permissions,
 * and shows a Snackbar when the user denies or cancels the picker.
 *
 * @param snackbarHostState The snackbar host state from the Scaffold.
 * @param onFilePicked Called with the URI when a file is selected.
 * @param onFolderPicked Called with the URI when a folder is selected.
 */
@Composable
fun rememberSafHelper(
    snackbarHostState: SnackbarHostState,
    onFilePicked: (Uri) -> Unit,
    onFolderPicked: (Uri) -> Unit
): SafHelper {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onFilePicked(uri)
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("File selection cancelled or permission denied")
            }
        }
    }

    // Folder picker launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            // Persist permission so the app can access the folder later
            persistUriPermission(context, uri)
            onFolderPicked(uri)
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Folder selection cancelled or permission denied")
            }
        }
    }

    // Function to persist permissions
    val persistPermission: (Uri) -> Unit = { uri ->
        persistUriPermission(context, uri)
    }

    // The denial callback (can be used separately if needed)
    val onDenied: () -> Unit = {
        scope.launch {
            snackbarHostState.showSnackbar("Permission denied")
        }
    }

    return remember {
        SafHelper(
            filePickerLauncher = filePickerLauncher::launch,
            folderPickerLauncher = folderPickerLauncher::launch,
            persistPermission = persistPermission,
            onPermissionDenied = onDenied
        )
    }
}

/**
 * Persists read/write URI permission so the user isn't asked repeatedly.
 */
fun persistUriPermission(context: Context, uri: Uri) {
    context.contentResolver.takePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION
    )
}
