// File: app/src/main/java/com/yourname/githubmanager/data/local/AppPreferences.kt
package com.yourname.githubmanager.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore delegate — creates a single "app_prefs" Preferences DataStore
 * scoped to the application Context.
 */
private val Context.dataStore by preferencesDataStore(name = "app_prefs")

/**
 * Everything needed to talk to a specific GitHub repo/branch.
 *
 * [branch] defaults to "main" wherever it's constructed from possibly-missing
 * data (see [AppPreferences.getRepoInfoFlow]) so callers never have to deal
 * with an empty branch string.
 */
data class RepoConfig(
    val owner: String,
    val repoName: String,
    val branch: String
)

/**
 * Stores non-sensitive app settings — currently the GitHub repo owner, repo
 * name, and branch the user wants to work with.
 *
 * IMPORTANT: The GitHub Personal Access Token must NEVER be stored here.
 * Use [com.yourname.githubmanager.data.local.SecureTokenStore] for the token.
 *
 * This is a plain singleton object (no DI) to keep Phase 1–3 simple.
 * Every function requires a [Context] (application context recommended)
 * to obtain the DataStore instance.
 */
object AppPreferences {

    private val KEY_REPO_OWNER = stringPreferencesKey("repo_owner")
    private val KEY_REPO_NAME = stringPreferencesKey("repo_name")
    private val KEY_BRANCH = stringPreferencesKey("branch")

    /** Used whenever no branch has been saved yet (new install, or data saved before this field existed). */
    private const val DEFAULT_BRANCH = "main"

    /**
     * Persists the repo owner, repo name, and branch.
     * Call from a coroutine (e.g. viewModelScope.launch { ... }).
     *
     * [branch] defaults to "main" so existing call sites that don't pass it
     * keep compiling and behave the same way as before this field existed.
     */
    suspend fun saveRepoInfo(context: Context, owner: String, repoName: String, branch: String = DEFAULT_BRANCH) {
        context.dataStore.edit { prefs ->
            prefs[KEY_REPO_OWNER] = owner
            prefs[KEY_REPO_NAME] = repoName
            prefs[KEY_BRANCH] = branch
        }
    }

    /**
     * Emits the current [RepoConfig] every time any of its values change.
     * Defaults to owner="" and repoName="" when nothing has been saved yet,
     * matching the previous behavior.
     *
     * Backward compatibility: if a user saved repo info before the branch
     * field existed, [KEY_BRANCH] won't be present in their DataStore yet —
     * in that case (and any other case where it's missing) this falls back
     * to [DEFAULT_BRANCH] ("main") rather than emitting an empty string.
     */
    fun getRepoInfoFlow(context: Context): Flow<RepoConfig> {
        return context.dataStore.data.map { prefs ->
            val owner = prefs[KEY_REPO_OWNER] ?: ""
            val repoName = prefs[KEY_REPO_NAME] ?: ""
            val branch = prefs[KEY_BRANCH]?.takeIf { it.isNotBlank() } ?: DEFAULT_BRANCH
            RepoConfig(owner = owner, repoName = repoName, branch = branch)
        }
    }

    /** Clears the saved repo owner/name/branch, e.g. on "sign out" or "reset". */
    suspend fun clearRepoInfo(context: Context) {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_REPO_OWNER)
            prefs.remove(KEY_REPO_NAME)
            prefs.remove(KEY_BRANCH)
        }
    }
}
