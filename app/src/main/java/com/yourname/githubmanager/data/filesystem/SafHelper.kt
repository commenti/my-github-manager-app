package com.yourname.githubmanager.data.filesystem

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
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
    fun launchFilePicker(mimeTypes: Array<String>) {
        filePickerLauncher(mimeTypes)
    }

    fun launchFolderPicker() {
        folderPickerLauncher(null)
    }

    fun persistUriPermission(uri: Uri) {
        persistPermission(uri)
    }

    fun onPermissionDenied() {
        onPermissionDenied.invoke()
    }
}

/**
 * Creates and remembers a [SafHelper] that is tied to the Composition.
 * The helper automatically handles SAF launchers, persists permissions,
 * and shows a Snackbar when the user denies or cancels the picker.
 */
@Composable
fun rememberSafHelper(
    snackbarHostState: SnackbarHostState,
    onFilePicked: (Uri) -> Unit,
    onFolderPicked: (Uri) -> Unit
): SafHelper {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            persistUriPermission(context, uri)
            onFolderPicked(uri)
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Folder selection cancelled or permission denied")
            }
        }
    }

    val persistPermission: (Uri) -> Unit = { uri ->
        persistUriPermission(context, uri)
    }

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
 * Persists read URI permission so the user isn't asked repeatedly.
 */
fun persistUriPermission(context: Context, uri: Uri) {
    context.contentResolver.takePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION
    )
}
