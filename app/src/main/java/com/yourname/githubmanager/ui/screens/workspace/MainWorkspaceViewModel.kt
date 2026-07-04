// File: app/src/main/java/com/yourname/githubmanager/ui/screens/workspace/MainWorkspaceViewModel.kt
package com.yourname.githubmanager.ui.screens.workspace

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for [MainWorkspaceScreen].
 *
 * Phase 1: This is a placeholder ViewModel.
 * The only state exposed is [isLoading] — always `false` for now.
 *
 * Future phases will add:
 *  - The list of project nodes (file tree)
 *  - Upload / commit / push states
 *  - Error handling states
 */
class MainWorkspaceViewModel : ViewModel() {

    /**
     * Indicates whether a background operation is in progress.
     * Phase 1: Always `false`. Real logic arrives in Phase 2+.
     */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ── Future actions (stubs) ────────────────────────────────────────────────

    /**
     * Placeholder for the "Import File" action.
     * Phase 2 will implement real SAF-based file picking here.
     */
    fun onImportFile() {
        // No-op in Phase 1 — UI layer shows a Snackbar instead.
    }

    /**
     * Placeholder for the "Import Folder" action.
     * Phase 2 will implement real SAF-based folder picking here.
     */
    fun onImportFolder() {
        // No-op in Phase 1 — UI layer shows a Snackbar instead.
    }
}
