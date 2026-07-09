// File: app/src/main/java/com/yourname/githubmanager/data/filesystem/SafHelper.kt
package com.yourname.githubmanager.data.filesystem

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
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
 * Persists read/write URI permission so the user isn't asked repeatedly.
 */
fun persistUriPermission(context: Context, uri: Uri) {
    context.contentResolver.takePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    )
}

/**
 * Resolves the human-readable display name for a SAF [Uri] (works for both
 * single-document Uris from OpenDocument and tree-child Uris from OpenDocumentTree).
 *
 * BUG FIX (name showing full path/URI instead of filename):
 * Previously call sites used `uri.lastPathSegment` or `uri.toString()` directly,
 * which for content:// Uris returns something like
 * "primary:Download/Service_Worker/Service_Worker.zip" instead of just
 * "Service_Worker.zip". The correct approach is to query the
 * OpenableColumns.DISPLAY_NAME column via the ContentResolver, falling back to
 * the last path segment only if the query fails or returns nothing.
 */
fun getDisplayName(context: Context, uri: Uri): String {
    var name: String? = null

    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        name = cursor.getString(index)
                    }
                }
            }
        } catch (e: Exception) {
            // Fall through to the lastPathSegment fallback below.
        }
    }

    if (name.isNullOrBlank()) {
        // Fallback: strip any directory-like prefix (e.g. "primary:Download/foo.zip" -> "foo.zip")
        name = uri.lastPathSegment?.substringAfterLast('/')
    }

    return name ?: "Unknown"
}
