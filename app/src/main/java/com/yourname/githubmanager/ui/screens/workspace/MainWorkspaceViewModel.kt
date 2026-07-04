package com.yourname.githubmanager.ui.screens.workspace

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.githubmanager.data.filesystem.persistUriPermission
import com.yourname.githubmanager.data.zip.ZipExtractor
import com.yourname.githubmanager.ui.components.FileNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
     * Builds a simple tree with only that file.
     */
    fun onFilePicked(uri: Uri) {
        _uiState.value = _uiState.value.copy(
            selectedFolderUri = null,
            fileTree = FileNode(
                name = uri.lastPathSegment ?: "UnknownFile",
                isFolder = false,
                filePath = uri.toString()
            )
        )
    }

    /**
     * Called when the user picks a folder via the folder picker.
     * If the folder appears to be a zip file, starts extraction; otherwise
     * builds the file tree directly from the SAF folder.
     */
    fun onFolderPicked(uri: Uri, context: Context) {
        _uiState.value = _uiState.value.copy(selectedFolderUri = uri)

        // Persist the permission (in addition to what SafHelper already did)
        persistUriPermission(context, uri)

        // Determine if this is a zip file by checking the file name extension
        val fileName = uri.lastPathSegment ?: ""
        if (fileName.endsWith(".zip", ignoreCase = true)) {
            extractZip(uri, context)
        } else {
            buildTreeFromFolder(uri, context)
        }
    }

    /**
     * Clears any error message (usually after it has been shown in the UI).
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * Triggers extraction of a zip archive and builds the file tree on success.
     */
    private fun extractZip(uri: Uri, context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExtracting = true, errorMessage = null)
            try {
                val extractor = ZipExtractor() // Assumes default constructor; adjust if needed
                val extractedRootUri = extractor.extract(context, uri) // Returns the SAF tree URI of extracted folder
                buildTreeFromFolder(extractedRootUri, context)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExtracting = false,
                    errorMessage = "Extraction failed: ${e.localizedMessage}"
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
            isFolder = isFolder,
            children = children,
            filePath = doc.uri.toString()
        )
    }
}
