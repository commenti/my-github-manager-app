// File: app/src/main/java/com/yourname/githubmanager/data/github/GitHubRepository.kt
package com.yourname.githubmanager.data.github

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.yourname.githubmanager.data.local.ProjectSyncStore
import com.yourname.githubmanager.data.local.SecureTokenStore
import com.yourname.githubmanager.data.local.SyncMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException
import java.security.MessageDigest

/**
 * Which GitHub repo/branch a project folder should sync to.
 * Comes from whatever the user has configured in Settings (AppPreferences).
 */
data class RepoConfig(
    val owner: String,
    val repoName: String,
    val branch: String = "main"
)

/**
 * One file as read from local disk, ready to be pushed.
 *
 * @param path    Repo-relative path, e.g. "app/src/main/.../Foo.kt"
 * @param content Raw text content of the file. (Binary files aren't
 *                supported by this simple representation — every file is
 *                assumed to be UTF-8 text, which matches a Kotlin/Android
 *                source project.)
 */
data class LocalFile(
    val path: String,
    val content: String
)

/** Outcome of an upload/commit attempt. */
sealed class GitHubSyncResult {
    data class Success(val commitSha: String, val filesPushed: Int) : GitHubSyncResult()
    data class Error(val type: ErrorType, val message: String) : GitHubSyncResult()
}

enum class ErrorType {
    NO_TOKEN,
    UNAUTHORIZED,
    RATE_LIMITED,
    NETWORK,
    UNKNOWN
}

/**
 * Implements the two operations MainWorkspaceViewModel needs:
 *  - [uploadProject]: first-ever push of a folder to a repo (no history to build on)
 *  - [commitChanges]: incremental push, diffing against the folder's last
 *    known synced state in [ProjectSyncStore]
 *
 * Both follow the same Git Data API sequence: blob(s) -> tree -> commit ->
 * ref update. [ProjectSyncStore] is only written to after every step in
 * that sequence has succeeded — a failure partway through leaves the
 * stored sync record untouched, per the app's "no partial state" rule.
 */
class GitHubRepository(
    private val api: GitHubApiService
) {

    private val gson = Gson()

    /**
     * Pushes every file in [files] as a brand-new commit with no parent —
     * use only when this folder has never been synced before (or the user
     * has explicitly chosen to re-link it to a different repo).
     */
    suspend fun uploadProject(
        context: Context,
        folderIdentifier: String,
        files: List<LocalFile>,
        repoConfig: RepoConfig,
        commitMessage: String = "Initial upload from GitHub Manager App"
    ): GitHubSyncResult = withContext(Dispatchers.IO) {
        val authHeader = authHeaderOrNull(context) ?: return@withContext GitHubSyncResult.Error(
            ErrorType.NO_TOKEN,
            "No GitHub token saved. Add one in Settings."
        )

        runCatching {
            // 1. Blob per file.
            val treeEntries = mutableListOf<TreeEntry>()
            for (file in files) {
                val blobResponse = api.createBlob(
                    repoConfig.owner,
                    repoConfig.repoName,
                    authHeader,
                    BlobRequest(content = encodeBase64(file.content))
                )
                val blob = unwrapOrReturn(blobResponse) { return@withContext it }
                treeEntries += TreeEntry(path = file.path, sha = blob.sha)
            }

            // 2. Tree — no base_tree, this is the very first commit for the repo/folder.
            val treeResponse = api.createTree(
                repoConfig.owner,
                repoConfig.repoName,
                authHeader,
                TreeRequest(baseTree = null, tree = treeEntries)
            )
            val tree = unwrapOrReturn(treeResponse) { return@withContext it }

            // 3. Commit — no parents, since there's no prior commit to build on.
            val commitResponse = api.createCommit(
                repoConfig.owner,
                repoConfig.repoName,
                authHeader,
                CommitRequest(message = commitMessage, tree = tree.sha, parents = emptyList())
            )
            val commit = unwrapOrReturn(commitResponse) { return@withContext it }

            // 4. Move the branch ref to point at the new commit.
            val refResponse = api.updateRef(
                repoConfig.owner,
                repoConfig.repoName,
                repoConfig.branch,
                authHeader,
                RefUpdateRequest(sha = commit.sha, force = false)
            )
            unwrapOrReturn(refResponse) { return@withContext it }

            // 5. Only now, with every step confirmed successful, persist the sync record.
            ProjectSyncStore.saveSyncMetadata(
                context,
                folderIdentifier,
                SyncMetadata(
                    repoOwner = repoConfig.owner,
                    repoName = repoConfig.repoName,
                    lastCommitSha = commit.sha,
                    lastTreeSha = tree.sha,
                    fileHashes = files.associate { it.path to sha256(it.content) }
                )
            )

            GitHubSyncResult.Success(commitSha = commit.sha, filesPushed = files.size)
        }.getOrElse { throwable -> throwable.toSyncError() }
    }

    /**
     * Pushes only the files that changed/were added/were deleted since the
     * folder's last recorded sync. Falls back to [uploadProject] if there's
     * no prior sync record at all (defensive — MainWorkspaceViewModel should
     * normally already route that case to "Upload" before ever calling this).
     */
    suspend fun commitChanges(
        context: Context,
        folderIdentifier: String,
        files: List<LocalFile>,
        repoConfig: RepoConfig,
        commitMessage: String = "Update from GitHub Manager App"
    ): GitHubSyncResult = withContext(Dispatchers.IO) {
        val syncMeta = ProjectSyncStore.getSyncMetadata(context, folderIdentifier)
            ?: return@withContext uploadProject(context, folderIdentifier, files, repoConfig, commitMessage)

        val authHeader = authHeaderOrNull(context) ?: return@withContext GitHubSyncResult.Error(
            ErrorType.NO_TOKEN,
            "No GitHub token saved. Add one in Settings."
        )

        val currentHashes = files.associate { it.path to sha256(it.content) }
        val changedOrNewPaths = currentHashes.filter { (path, hash) ->
            syncMeta.fileHashes[path] != hash
        }.keys
        val deletedPaths = syncMeta.fileHashes.keys - currentHashes.keys

        if (changedOrNewPaths.isEmpty() && deletedPaths.isEmpty()) {
            // Nothing to push — treat as a no-op success against the last commit.
            return@withContext GitHubSyncResult.Success(commitSha = syncMeta.lastCommitSha, filesPushed = 0)
        }

        val filesByPath = files.associateBy { it.path }

        runCatching {
            // 1. Blobs only for changed/new files.
            val treeEntries = mutableListOf<TreeEntry>()
            for (path in changedOrNewPaths) {
                val file = filesByPath.getValue(path)
                val blobResponse = api.createBlob(
                    repoConfig.owner,
                    repoConfig.repoName,
                    authHeader,
                    BlobRequest(content = encodeBase64(file.content))
                )
                val blob = unwrapOrReturn(blobResponse) { return@withContext it }
                treeEntries += TreeEntry(path = path, sha = blob.sha)
            }
            // Deleted files: explicit null-sha entries remove them from base_tree.
            for (path in deletedPaths) {
                treeEntries += TreeEntry(path = path, sha = null)
            }

            // 2. Tree — layered on top of the last known tree, so unmodified
            //    files are carried over without needing to be re-sent.
            val treeResponse = api.createTree(
                repoConfig.owner,
                repoConfig.repoName,
                authHeader,
                TreeRequest(baseTree = syncMeta.lastTreeSha, tree = treeEntries)
            )
            val tree = unwrapOrReturn(treeResponse) { return@withContext it }

            // 3. Commit — parent is the last commit, so history is linear.
            val commitResponse = api.createCommit(
                repoConfig.owner,
                repoConfig.repoName,
                authHeader,
                CommitRequest(message = commitMessage, tree = tree.sha, parents = listOf(syncMeta.lastCommitSha))
            )
            val commit = unwrapOrReturn(commitResponse) { return@withContext it }

            // 4. Fast-forward the branch ref.
            val refResponse = api.updateRef(
                repoConfig.owner,
                repoConfig.repoName,
                repoConfig.branch,
                authHeader,
                RefUpdateRequest(sha = commit.sha, force = false)
            )
            unwrapOrReturn(refResponse) { return@withContext it }

            // 5. Persist the new baseline. fileHashes reflects the FULL current
            //    file set (not just this commit's diff), so the next diff is correct.
            ProjectSyncStore.saveSyncMetadata(
                context,
                folderIdentifier,
                SyncMetadata(
                    repoOwner = repoConfig.owner,
                    repoName = repoConfig.repoName,
                    lastCommitSha = commit.sha,
                    lastTreeSha = tree.sha,
                    fileHashes = currentHashes
                )
            )

            GitHubSyncResult.Success(
                commitSha = commit.sha,
                filesPushed = changedOrNewPaths.size + deletedPaths.size
            )
        }.getOrElse { throwable -> throwable.toSyncError() }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun authHeaderOrNull(context: Context): String? {
        val token = SecureTokenStore.getToken(context) ?: return null
        return "Bearer $token"
    }

    private fun encodeBase64(content: String): String =
        Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    private fun sha256(content: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Unwraps a successful Retrofit [Response], or short-circuits the caller
     * with a mapped [GitHubSyncResult.Error] via [onError] when the response
     * failed (non-2xx) or had no body.
     *
     * Note: [onError] must itself `return` from the enclosing suspend
     * function (see call sites — they pass `{ return@withContext it }`),
     * so a failed step never falls through to later steps or a partial
     * ProjectSyncStore write.
     */
    private inline fun <T> unwrapOrReturn(
        response: Response<T>,
        onError: (GitHubSyncResult.Error) -> Nothing
    ): T {
        if (response.isSuccessful) {
            return response.body() ?: onError(
                GitHubSyncResult.Error(ErrorType.UNKNOWN, "GitHub returned an empty response.")
            )
        }
        onError(mapHttpError(response))
    }

    private fun <T> mapHttpError(response: Response<T>): GitHubSyncResult.Error {
        val serverMessage = runCatching {
            response.errorBody()?.string()?.let { gson.fromJson(it, GitHubErrorResponse::class.java) }?.message
        }.getOrNull()

        return when (response.code()) {
            401 -> GitHubSyncResult.Error(
                ErrorType.UNAUTHORIZED,
                serverMessage ?: "Invalid or expired token. Check Settings."
            )
            403 -> {
                val remaining = response.headers()["X-RateLimit-Remaining"]
                if (remaining == "0") {
                    GitHubSyncResult.Error(ErrorType.RATE_LIMITED, "GitHub rate limit reached. Try again later.")
                } else {
                    GitHubSyncResult.Error(ErrorType.UNAUTHORIZED, serverMessage ?: "GitHub denied this request.")
                }
            }
            else -> GitHubSyncResult.Error(
                ErrorType.UNKNOWN,
                serverMessage ?: "GitHub request failed (HTTP ${response.code()})."
            )
        }
    }

    private fun Throwable.toSyncError(): GitHubSyncResult.Error = when (this) {
        is IOException -> GitHubSyncResult.Error(ErrorType.NETWORK, "Network error. Check your connection and try again.")
        else -> GitHubSyncResult.Error(ErrorType.UNKNOWN, message ?: "Unexpected error during sync.")
    }
}

