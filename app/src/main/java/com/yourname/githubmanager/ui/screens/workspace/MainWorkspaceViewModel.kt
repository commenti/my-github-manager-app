package com.yourname.githubmanager.ui.screens.workspace

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.yourname.githubmanager.data.filesystem.ZipExtractor
import com.yourname.githubmanager.data.filesystem.getDisplayName
import com.yourname.githubmanager.data.filesystem.persistUriPermission
import com.yourname.githubmanager.domain.FileNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

data class WorkspaceUiState(
    val selectedFolderUri: Uri? = null,
    val isExtracting: Boolean = false,
    val fileTree: FileNode? = null,
    val errorMessage: String? = null
)

class MainWorkspaceViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    /**
     * Called when the user picks a single file using the file picker.
     *
     * BUG FIX (zip detection never triggered):
     * This function previously always built a plain, non-extracted FileNode,
     * even when the picked file was a .zip archive imported via "Import File".
     * It now routes through [handleImportedUri], which checks the *real*
     * display name (case-insensitive) for a ".zip" suffix and, if found,
     * kicks off extraction instead of listing the zip itself.
     */
    fun onFilePicked(uri: Uri, context: Context) {
        persistUriPermission(context, uri)
        handleImportedUri(uri, context, isFolder = false)
    }

    /**
     * Called when the user picks a folder via the folder picker.
     * If the picked item appears to be a zip file, starts extraction; otherwise
     * builds the file tree directly from the SAF folder.
     */
    fun onFolderPicked(uri: Uri, context: Context) {
        _uiState.value = _uiState.value.copy(selectedFolderUri = uri)

        persistUriPermission(context, uri)

        handleImportedUri(uri, context, isFolder = true)
    }

    /**
     * Single place where "what do we do with this imported Uri?" is decided.
     * This is the decision point requested: it resolves the true display name
     * (via [getDisplayName], fixing Bug 2) and checks its extension
     * case-insensitively for ".zip" (fixing Bug 1) before branching into
     * either extraction or the normal file/folder tree build.
     */
    private fun handleImportedUri(uri: Uri, context: Context, isFolder: Boolean) {
        val displayName = getDisplayName(context, uri)

        if (displayName.endsWith(".zip", ignoreCase = true)) {
            extractZip(uri, context, displayName)
        } else if (isFolder) {
            buildTreeFromFolder(uri, context)
        } else {
            _uiState.value = _uiState.value.copy(
                selectedFolderUri = null,
                fileTree = FileNode(
                    name = displayName,
                    path = uri.toString(),
                    isFolder = false
                )
            )
        }
    }

    /**
     * Clears any error message (usually after it has been shown in the UI).
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * Triggers extraction of a zip archive via WorkManager and builds the file tree on success.
     *
     * BUG FIX notes:
     *  - destDir is now named after the zip's real display name (not just "extracted_<time>"),
     *    which makes it easier to identify in logs/filesystem.
     *  - On success we now read [ZipExtractor.KEY_EXTRACTED_PATH] (a plain filesystem path
     *    that ZipExtractor actually outputs) instead of a non-existent "extractedFolderUri"
     *    key, and we build the tree with [fileToNode] by walking the real java.io.File
     *    directory on disk — the extracted content lives in app-internal storage, so it is
     *    a plain File tree, not a SAF DocumentFile tree.
     *  - On failure, errorMessage is set (never thrown/crashed) so the UI can show a Snackbar.
     */
    private fun extractZip(uri: Uri, context: Context, zipDisplayName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExtracting = true, errorMessage = null)
            try {
                val baseName = zipDisplayName
                    .removeSuffix(".zip")
                    .ifBlank { "extracted" }
                val destDir = File(context.filesDir, "${baseName}_${System.currentTimeMillis()}").apply { mkdirs() }

                val workRequest = OneTimeWorkRequestBuilder<ZipExtractor>()
                    .setInputData(
                        workDataOf(
                            ZipExtractor.KEY_ZIP_URI_PATH to uri.toString(),
                            ZipExtractor.KEY_DEST_DIR_PATH to destDir.absolutePath
                        )
                    )
                    .build()

                WorkManager.getInstance(context).enqueue(workRequest)

                val workInfo = WorkManager.getInstance(context)
                    .getWorkInfoByIdFlow(workRequest.id)
                    .first { it?.state?.isFinished == true }

                if (workInfo?.state == WorkInfo.State.SUCCEEDED) {
                    val extractedPath = workInfo.outputData.getString(ZipExtractor.KEY_EXTRACTED_PATH)
                    if (extractedPath != null) {
                        val rootNode = fileToNode(File(extractedPath))
                        _uiState.value = _uiState.value.copy(
                            isExtracting = false,
                            fileTree = rootNode,
                            errorMessage = null
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isExtracting = false,
                            errorMessage = "Extraction succeeded but no output path found."
                        )
                    }
                } else {
                    val errorMsg = workInfo?.outputData?.getString(ZipExtractor.KEY_ERROR_MESSAGE)
                    _uiState.value = _uiState.value.copy(
                        isExtracting = false,
                        errorMessage = errorMsg ?: "Extraction failed."
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExtracting = false,
                    errorMessage = "Extraction error: ${e.localizedMessage}"
                )
            }
        }
    }

    /**
     * Builds the FileNode tree from a given SAF folder URI and updates the state.
     */
    private fun buildTreeFromFolder(folderUri: Uri, context: Context) {
        try {
            val rootNode = documentFileToNode(DocumentFile.fromTreeUri(context, folderUri))
            _uiState.value = _uiState.value.copy(
                isExtracting = false,
                fileTree = rootNode
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isExtracting = false,
                errorMessage = "Failed to read folder: ${e.localizedMessage}"
            )
        }
    }

    /**
     * Recursively converts a plain [File] (used for zip-extraction output living in
     * app-internal storage) into a [FileNode] tree. Unlike [documentFileToNode], this
     * does not go through SAF since extracted files are regular filesystem files.
     */
    private fun fileToNode(file: File): FileNode {
        val isFolder = file.isDirectory
        val children = if (isFolder) {
            file.listFiles()
                ?.sortedBy { it.name.lowercase() }
                ?.map { fileToNode(it) }
                ?: emptyList()
        } else {
            emptyList()
        }

        return FileNode(
            name = file.name,
            path = file.absolutePath,
            isFolder = isFolder,
            children = children
        )
    }

    /**
     * Recursively converts a [DocumentFile] into a [FileNode] tree.
     */
    private fun documentFileToNode(doc: DocumentFile?): FileNode? {
        if (doc == null) return null

        val name = doc.name ?: "Unknown"
        val isFolder = doc.isDirectory
        val children = if (isFolder) {
            doc.listFiles().mapNotNull { documentFileToNode(it) }
        } else {
            emptyList()
        }

        return FileNode(
            name = name,
            path = doc.uri.toString(),
            isFolder = isFolder,
            children = children
        )
    }
}
