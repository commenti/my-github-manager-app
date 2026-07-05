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
 * Stores non-sensitive app settings — currently the GitHub repo owner and
 * repo name the user wants to work with.
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

    /**
     * Persists the repo owner and repo name.
     * Call from a coroutine (e.g. viewModelScope.launch { ... }).
     */
    suspend fun saveRepoInfo(context: Context, owner: String, repoName: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_REPO_OWNER] = owner
            prefs[KEY_REPO_NAME] = repoName
        }
    }

    /**
     * Emits the current (owner, repoName) pair every time either value changes.
     * Defaults to ("", "") when nothing has been saved yet.
     */
    fun getRepoInfoFlow(context: Context): Flow<Pair<String, String>> {
        return context.dataStore.data.map { prefs ->
            val owner = prefs[KEY_REPO_OWNER] ?: ""
            val repoName = prefs[KEY_REPO_NAME] ?: ""
            owner to repoName
        }
    }

    /** Clears the saved repo owner/name, e.g. on "sign out" or "reset". */
    suspend fun clearRepoInfo(context: Context) {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_REPO_OWNER)
            prefs.remove(KEY_REPO_NAME)
        }
    }
}
