// File: app/src/main/java/com/yourname/githubmanager/data/local/ProjectSyncStore.kt
package com.yourname.githubmanager.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Everything the app knows about one local project folder's last
 * successful sync to GitHub.
 *
 * @param repoOwner     Owner the folder was last pushed to.
 * @param repoName      Repo the folder was last pushed to.
 * @param lastCommitSha The commit this folder's contents currently match.
 *                       Used as the `parents` entry for the next commit.
 * @param lastTreeSha   The tree of [lastCommitSha]. Used as `base_tree` so an
 *                       incremental commit only needs to send changed files.
 * @param fileHashes    Repo-relative path -> content hash (e.g. SHA-256),
 *                       as of [lastCommitSha]. Diffing current on-disk
 *                       hashes against this map is how "Commit Changes"
 *                       finds out what actually changed.
 */
@Serializable
data class SyncMetadata(
    val repoOwner: String,
    val repoName: String,
    val lastCommitSha: String,
    val lastTreeSha: String,
    val fileHashes: Map<String, String> = emptyMap()
)

/**
 * DataStore delegate — a dedicated "project_sync_store" Preferences DataStore,
 * separate from [AppPreferences] since this holds one entry per project
 * folder rather than a single global settings blob.
 */
private val Context.syncDataStore by preferencesDataStore(name = "project_sync_store")

/**
 * Tracks, per local project folder, whether it has ever been pushed to
 * GitHub and what its last-known synced state was.
 *
 * This is what MainWorkspaceViewModel consults to decide between showing
 * "Upload" (never synced, or synced to a *different* repo than the one
 * currently configured in Settings) vs "Commit Changes" (synced to the
 * same repo already).
 *
 * A plain singleton object (no DI), consistent with [AppPreferences] and
 * [SecureTokenStore]. Every function takes a [Context] (application
 * context recommended) and a `folderIdentifier` — a stable string that
 * uniquely identifies the local folder (e.g. its absolute path or a
 * persisted content:// URI string). Callers own the choice of identifier;
 * this store only needs it to be stable across app restarts for the
 * same folder.
 */
object ProjectSyncStore {

    /**
     * Preference keys can't safely contain arbitrary characters (folder
     * paths can), so we hash the folder identifier down to a short, safe,
     * collision-resistant key. SHA-256 rather than String.hashCode() to
     * avoid 32-bit hash collisions across many projects.
     */
    private fun keyFor(folderIdentifier: String) =
        stringPreferencesKey("sync_${sha256(folderIdentifier)}")

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Returns the saved sync state for this folder, or null if it has
     * never been synced (i.e. the "Upload" case).
     */
    suspend fun getSyncMetadata(context: Context, folderIdentifier: String): SyncMetadata? {
        val raw = context.syncDataStore.data
            .map { prefs -> prefs[keyFor(folderIdentifier)] }
            .first()
        return raw?.let { runCatching { Json.decodeFromString<SyncMetadata>(it) }.getOrNull() }
    }

    /** Observes this folder's sync state, emitting null if never synced. */
    fun getSyncMetadataFlow(context: Context, folderIdentifier: String): Flow<SyncMetadata?> {
        return context.syncDataStore.data.map { prefs ->
            prefs[keyFor(folderIdentifier)]?.let { json ->
                runCatching { Json.decodeFromString<SyncMetadata>(json) }.getOrNull()
            }
        }
    }

    /**
     * Overwrites this folder's sync state after a fully successful
     * upload/commit. Callers MUST only invoke this once every Git Data API
     * step (blob -> tree -> commit -> ref update) has succeeded — never
     * partially, so a failed push can't leave a stale/incorrect record
     * that would desync "Commit Changes" from reality.
     */
    suspend fun saveSyncMetadata(
        context: Context,
        folderIdentifier: String,
        metadata: SyncMetadata
    ) {
        val json = Json.encodeToString(metadata)
        context.syncDataStore.edit { prefs ->
            prefs[keyFor(folderIdentifier)] = json
        }
    }

    /**
     * Clears this folder's sync record, forcing "Upload" again next time.
     * Useful if the user explicitly disconnects a folder from GitHub, or
     * if a repo mismatch is detected and the user confirms they want to
     * re-link to the new repo from scratch.
     */
    suspend fun clearSyncMetadata(context: Context, folderIdentifier: String) {
        context.syncDataStore.edit { prefs ->
            prefs.remove(keyFor(folderIdentifier))
        }
    }
}
