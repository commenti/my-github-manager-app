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
     * Builds a simple tree with only that file.
     */
    fun onFilePicked(uri: Uri) {
        _uiState.value = _uiState.value.copy(
            selectedFolderUri = null,
            fileTree = FileNode(
                name = uri.lastPathSegment ?: "UnknownFile",
                filePath = uri.toString(),
                isFolder = false
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

        persistUriPermission(context, uri)

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
     * Triggers extraction of a zip archive via WorkManager and builds the file tree on success.
     */
    private fun extractZip(uri: Uri, context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExtracting = true, errorMessage = null)
            try {
                val destDir = File(context.filesDir, "extracted_${System.currentTimeMillis()}").apply { mkdirs() }

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
                    val extractedUriStr = workInfo.outputData.getString("extractedFolderUri")
                    if (extractedUriStr != null) {
                        buildTreeFromFolder(Uri.parse(extractedUriStr), context)
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isExtracting = false,
                            errorMessage = "Extraction succeeded but no output URI found."
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
            filePath = doc.uri.toString(),
            isFolder = isFolder,
            children = children
        )
    }
}
