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
import com.yourname.githubmanager.data.github.GitHubRepository
import com.yourname.githubmanager.data.local.AppPreferences
import com.yourname.githubmanager.data.local.ProjectSyncStore
import com.yourname.githubmanager.data.local.SecureTokenStore
import com.yourname.githubmanager.domain.FileNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/**
 * Where the currently-imported folder currently stands relative to GitHub,
 * used by MainWorkspaceScreen to decide which button to show.
 */
sealed class UploadButtonState {
    /** Never pushed from this app before -> show "Upload". */
    object NotUploaded : UploadButtonState()

    /** Already pushed, and Settings still points at that same repo -> show "Commit Changes". */
    object UploadedSameRepo : UploadButtonState()

    /**
     * Already pushed, but to a *different* repo than what Settings now
     * points at (e.g. the user changed owner/repoName since). The UI
     * should warn before letting the user hit "Upload" again, since that
     * starts a brand-new sync history against the new repo.
     */
    data class UploadedDifferentRepo(val previousOwner: String, val previousRepoName: String) : UploadButtonState()
}

data class WorkspaceUiState(
    val selectedFolderUri: Uri? = null,
    val isExtracting: Boolean = false,
    val fileTree: FileNode? = null,
    val errorMessage: String? = null,
    val uploadButtonState: UploadButtonState = UploadButtonState.NotUploaded,
    val isSyncing: Boolean = false,
    val syncSuccessMessage: String? = null
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
            refreshUploadButtonState(context)
        }
    }

    /**
     * Clears any error message (usually after it has been shown in the UI).
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /** Clears the "upload/commit succeeded" message after it's been shown. */
    fun clearSyncSuccessMessage() {
        _uiState.value = _uiState.value.copy(syncSuccessMessage = null)
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
                        refreshUploadButtonState(context)
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
            refreshUploadButtonState(context)
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

    // ── Upload / Commit Changes ─────────────────────────────────────────

    /**
     * A stable identifier for the currently-imported folder, used as the
     * key ProjectSyncStore tracks sync state under. [FileNode.path] is
     * either a content:// SAF URI or an absolute extracted-zip file path —
     * both are stable across app restarts for the *same* folder, which is
     * exactly what ProjectSyncStore requires (see ProjectSyncStore.kt).
     */
    private fun folderIdentifierFor(node: FileNode): String = node.path

    /**
     * Re-checks ProjectSyncStore + the currently-saved repo config and
     * updates [WorkspaceUiState.uploadButtonState] accordingly. Called
     * every time a new fileTree is set (new import) and again after every
     * successful upload/commit.
     */
    private fun refreshUploadButtonState(context: Context) {
        val node = _uiState.value.fileTree ?: return
        viewModelScope.launch {
            val repoConfig = AppPreferences.getRepoInfoFlow(context).first()
            val saved = ProjectSyncStore.getSyncMetadata(context, folderIdentifierFor(node))

            val newState = when {
                saved == null -> UploadButtonState.NotUploaded
                saved.repoOwner == repoConfig.owner && saved.repoName == repoConfig.repoName ->
                    UploadButtonState.UploadedSameRepo
                else -> UploadButtonState.UploadedDifferentRepo(saved.repoOwner, saved.repoName)
            }
            _uiState.value = _uiState.value.copy(uploadButtonState = newState)
        }
    }

    /** Pushes the current folder to GitHub for the first time. */
    fun onUploadClick(context: Context) {
        performSync(context, isCommit = false)
    }

    /** Pushes only what changed in the current folder since the last successful sync. */
    fun onCommitChangesClick(context: Context) {
        performSync(context, isCommit = true)
    }

    /**
     * Shared Upload/Commit Changes flow. Reads repo owner/name/branch from
     * [AppPreferences] and the PAT from [SecureTokenStore] fresh every time
     * (rather than caching them), since either can change in Settings while
     * this screen is open.
     *
     * Branch handling: [AppPreferences.getRepoInfoFlow] already guarantees a
     * non-blank branch (defaulting to "main" internally — see AppPreferences.kt),
     * but the `.ifBlank { "main" }` below is kept as a last-line-of-defense
     * fallback rather than trusting that invariant blindly. The branch is
     * NEVER hardcoded here — it always comes from Settings.
     */
    private fun performSync(context: Context, isCommit: Boolean) {
        val node = _uiState.value.fileTree
        if (node == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "No project imported yet.")
            return
        }

        val token = SecureTokenStore.getToken(context)
        if (token.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "No GitHub token saved. Add one in Settings first."
            )
            return
        }

        viewModelScope.launch {
            val repoConfig = AppPreferences.getRepoInfoFlow(context).first()
            if (repoConfig.owner.isBlank() || repoConfig.repoName.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Set a repo owner and repo name in Settings first."
                )
                return@launch
            }
            val branch = repoConfig.branch.ifBlank { "main" }
            val folderIdentifier = folderIdentifierFor(node)

            _uiState.value = _uiState.value.copy(isSyncing = true, errorMessage = null, syncSuccessMessage = null)

            val result = if (isCommit) {
                GitHubRepository.commitChanges(
                    context = context,
                    folderNode = node,
                    folderIdentifier = folderIdentifier,
                    repoOwner = repoConfig.owner,
                    repoName = repoConfig.repoName,
                    token = token,
                    branch = branch
                )
            } else {
                GitHubRepository.uploadProject(
                    context = context,
                    folderNode = node,
                    folderIdentifier = folderIdentifier,
                    repoOwner = repoConfig.owner,
                    repoName = repoConfig.repoName,
                    token = token,
                    branch = branch
                )
            }

            result.fold(
                onSuccess = { shaOrStatus ->
                    val message = when {
                        shaOrStatus == "NO_CHANGES" -> "No changes to commit."
                        isCommit -> "Changes committed successfully."
                        else -> "Project uploaded successfully."
                    }
                    _uiState.value = _uiState.value.copy(isSyncing = false, syncSuccessMessage = message)
                    refreshUploadButtonState(context)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isSyncing = false,
                        errorMessage = e.localizedMessage ?: "Sync failed."
                    )
                }
            )
        }
    }
}

