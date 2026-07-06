// File: app/src/main/java/com/yourname/githubmanager/ui/screens/settings/SettingsViewModel.kt
package com.yourname.githubmanager.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.githubmanager.data.local.AppPreferences
import com.yourname.githubmanager.data.local.SecureTokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for [SettingsScreen].
 *
 * NOTE: [token] only ever holds what the user is currently typing in the
 * input field — it is cleared immediately after a successful save and is
 * NEVER pre-filled with the previously saved token (we don't read the
 * token back out of SecureTokenStore for display, only [isTokenSaved]
 * status is exposed).
 *
 * [branch] defaults to "main" to match [AppPreferences]'s own default —
 * both before anything has been saved yet, and as a fallback if older
 * saved data predates this field.
 */
data class SettingsUiState(
    val repoOwner: String = "",
    val repoName: String = "",
    val branch: String = "main",
    val token: String = "",
    val isTokenSaved: Boolean = false,
    val showPrivacyDialog: Boolean = false,
    val saveConfirmationVisible: Boolean = false
)

/**
 * Backs [SettingsScreen].
 *
 * Uses [AndroidViewModel] (instead of plain ViewModel + DI) so it has an
 * application [android.content.Context] to hand to [AppPreferences] and
 * [SecureTokenStore], matching how those two objects are called elsewhere.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Reflect saved repo owner/name/branch as they change.
        viewModelScope.launch {
            AppPreferences.getRepoInfoFlow(appContext).collect { repoConfig ->
                _uiState.update {
                    it.copy(
                        repoOwner = repoConfig.owner,
                        repoName = repoConfig.repoName,
                        branch = repoConfig.branch
                    )
                }
            }
        }
        // Reflect whether a token already exists, without ever loading its value.
        _uiState.update { it.copy(isTokenSaved = SecureTokenStore.hasToken(appContext)) }
    }

    fun onRepoOwnerChange(value: String) {
        _uiState.update { it.copy(repoOwner = value) }
    }

    fun onRepoNameChange(value: String) {
        _uiState.update { it.copy(repoName = value) }
    }

    fun onBranchChange(value: String) {
        _uiState.update { it.copy(branch = value) }
    }

    fun onTokenChange(value: String) {
        _uiState.update { it.copy(token = value) }
    }

    fun onShowPrivacyPolicyClick() {
        _uiState.update { it.copy(showPrivacyDialog = true) }
    }

    fun onDismissPrivacyPolicy() {
        _uiState.update { it.copy(showPrivacyDialog = false) }
    }

    /**
     * Persists repo owner/name/branch (DataStore) and, if the user typed
     * one, the token (EncryptedSharedPreferences). Clears the token field
     * from memory right after saving it.
     */
    fun onSaveClick() {
        val current = _uiState.value
        viewModelScope.launch {
            AppPreferences.saveRepoInfo(
                context = appContext,
                owner = current.repoOwner.trim(),
                repoName = current.repoName.trim(),
                branch = current.branch.trim().ifBlank { "main" }
            )

            if (current.token.isNotBlank()) {
                SecureTokenStore.saveToken(appContext, current.token.trim())
                _uiState.update { it.copy(token = "", isTokenSaved = true) }
            }

            _uiState.update { it.copy(saveConfirmationVisible = true) }
        }
    }

    fun onSaveConfirmationShown() {
        _uiState.update { it.copy(saveConfirmationVisible = false) }
    }

    /** Removes the stored token, e.g. "Disconnect GitHub". */
    fun onClearTokenClick() {
        SecureTokenStore.clearToken(appContext)
        _uiState.update { it.copy(isTokenSaved = false) }
    }
}
