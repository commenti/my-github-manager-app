// File: app/src/main/java/com/yourname/githubmanager/ui/screens/editor/FileEditorViewModel.kt
package com.yourname.githubmanager.ui.screens.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.githubmanager.data.filesystem.FileSystemException
import com.yourname.githubmanager.data.filesystem.ProjectFileSystem
import com.yourname.githubmanager.domain.FileNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorUiState(
    val fileName: String = "",
    val content: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
    val error: String? = null
)

/**
 * ViewModel that manages the content loading, editing, and saving of a text file.
 *
 * It delegates the actual I/O to the provided [ProjectFileSystem] implementation
 * (SAF or local), keeping the UI completely storage-agnostic.
 *
 * TODO: The current constructor approach is simple but not typical for Android.
 * In a real project, the ViewModel would be provided by a Hilt module or a manual
 * factory that can resolve the correct [ProjectFileSystem] instance based on the
 * file source (SAF vs extracted zip). The caller (e.g., navigation) must supply
 * the right dependency.
 */
class FileEditorViewModel(
    private val fileNode: FileNode,
    private val fileSystem: ProjectFileSystem
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    init {
        loadContent()
    }

    private fun loadContent() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val text = fileSystem.readText(fileNode)
                _uiState.update {
                    it.copy(
                        fileName = fileNode.name,
                        content = text,
                        isLoading = false,
                        isDirty = false
                    )
                }
            } catch (e: FileSystemException) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to load file: ${e.message}")
                }
            }
        }
    }

    fun onContentChange(newContent: String) {
        _uiState.update {
            it.copy(content = newContent, isDirty = true, error = null)
        }
    }

    fun save() {
        val currentContent = _uiState.value.content
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                fileSystem.writeText(fileNode, currentContent)
                _uiState.update { it.copy(isSaving = false, isDirty = false) }
            } catch (e: FileSystemException) {
                _uiState.update { it.copy(isSaving = false, error = "Failed to save: ${e.message}") }
            }
        }
    }
}
