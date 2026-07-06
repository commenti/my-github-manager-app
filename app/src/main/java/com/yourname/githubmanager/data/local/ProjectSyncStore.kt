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
 * Everything the app knows about one GitHub repo's last successful sync
 * from this app.
 *
 * @param repoOwner     Owner this metadata was saved under.
 * @param repoName      Repo this metadata was saved under.
 * @param lastCommitSha The commit this repo's contents currently match.
 *                       Used as the `parents` entry for the next commit.
 * @param lastTreeSha   The tree of [lastCommitSha]. Used as `base_tree` so an
 *                       incremental commit only needs to send changed files.
 * @param fileHashes    Repo-relative path -> content hash (e.g. SHA-256),
 *                       as of [lastCommitSha]. Diffing current on-disk
 *                       hashes against this map is how "Commit Changes"
 *                       finds out what actually changed (added, modified,
 *                       or deleted).
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
 * separate from [AppPreferences] since this holds one entry per repo rather
 * than a single global settings blob.
 */
private val Context.syncDataStore by preferencesDataStore(name = "project_sync_store")

/**
 * Tracks, per GitHub repo (owner + repoName + branch), whether this app has
 * ever pushed to it and what the last-known synced state was.
 *
 * This is what MainWorkspaceViewModel consults to decide between showing
 * "Upload" (this repo has never been pushed to from this app) vs "Commit
 * Changes" (this repo already has a saved sync record).
 *
 * IMPORTANT — keyed by REPO, not by local folder:
 * Sync state is intentionally tied to the target repo (owner+repoName+branch),
 * not to the local folder/URI that happens to be open in the workspace.
 * Since Settings only ever configures one repo at a time, "has this repo been
 * pushed to before?" is the question that actually matters. This also means
 * re-importing the same project from a fresh zip (which lands in a brand-new,
 * timestamped local folder every time) is correctly recognized as the same
 * repo, so "Commit Changes" — not "Upload" — is offered. Use [repoKeyFor] to
 * build the key so every caller derives it the same way.
 *
 * A plain singleton object (no DI), consistent with [AppPreferences] and
 * [SecureTokenStore]. Every function takes a [Context] (application
 * context recommended) and a `repoKey` — see [repoKeyFor].
 */
object ProjectSyncStore {

    /**
     * Builds the stable key sync state is tracked under for a given repo.
     *
     * Owner and repo name are lower-cased (GitHub treats them as
     * case-insensitive, so "Owner/Repo" and "owner/repo" must resolve to the
     * same sync record) and trimmed to absorb incidental whitespace from
     * Settings text fields. Branch is kept case-sensitive, since Git branch
     * names genuinely are.
     */
    fun repoKeyFor(owner: String, repoName: String, branch: String): String =
        "${owner.trim().lowercase()}/${repoName.trim().lowercase()}/${branch.trim()}"

    /**
     * Preference keys can't safely contain arbitrary characters, so we hash
     * the repo key down to a short, safe, collision-resistant key. SHA-256
     * rather than String.hashCode() to avoid 32-bit hash collisions across
     * many repos.
     */
    private fun keyFor(repoKey: String) =
        stringPreferencesKey("sync_${sha256(repoKey)}")

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Returns the saved sync state for this repo, or null if this app has
     * never pushed to it before (i.e. the "Upload" case).
     */
    suspend fun getSyncMetadata(context: Context, repoKey: String): SyncMetadata? {
        val raw = context.syncDataStore.data
            .map { prefs -> prefs[keyFor(repoKey)] }
            .first()
        return raw?.let { runCatching { Json.decodeFromString<SyncMetadata>(it) }.getOrNull() }
    }

    /** Observes this repo's sync state, emitting null if never synced. */
    fun getSyncMetadataFlow(context: Context, repoKey: String): Flow<SyncMetadata?> {
        return context.syncDataStore.data.map { prefs ->
            prefs[keyFor(repoKey)]?.let { json ->
                runCatching { Json.decodeFromString<SyncMetadata>(json) }.getOrNull()
            }
        }
    }

    /**
     * Overwrites this repo's sync state after a fully successful
     * upload/commit. Callers MUST only invoke this once every Git Data API
     * step (blob -> tree -> commit -> ref update) has succeeded — never
     * partially, so a failed push can't leave a stale/incorrect record
     * that would desync "Commit Changes" from reality.
     */
    suspend fun saveSyncMetadata(
        context: Context,
        repoKey: String,
        metadata: SyncMetadata
    ) {
        val json = Json.encodeToString(metadata)
        context.syncDataStore.edit { prefs ->
            prefs[keyFor(repoKey)] = json
        }
    }

    /**
     * Clears this repo's sync record, forcing "Upload" again next time.
     * Useful if the user explicitly disconnects a repo, or wants to
     * re-link it from scratch.
     */
    suspend fun clearSyncMetadata(context: Context, repoKey: String) {
        context.syncDataStore.edit { prefs ->
            prefs.remove(keyFor(repoKey))
        }
    }
}

