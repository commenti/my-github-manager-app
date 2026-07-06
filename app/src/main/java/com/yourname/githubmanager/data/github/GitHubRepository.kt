// File: app/src/main/java/com/yourname/githubmanager/data/github/GitHubRepository.kt
package com.yourname.githubmanager.data.github

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.google.gson.Gson
import com.yourname.githubmanager.data.local.ProjectSyncStore
import com.yourname.githubmanager.data.local.SyncMetadata
import com.yourname.githubmanager.domain.FileNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * High-level "Upload" / "Commit Changes" logic that MainWorkspaceViewModel
 * calls. Talks to GitHub only through [GitHubApiService] and only reads the
 * existing DTOs from GitHubModels.kt. Persists the result of every fully
 * successful push through [ProjectSyncStore] — never partially, matching
 * the contract documented on [ProjectSyncStore.saveSyncMetadata].
 *
 * A plain singleton object (no DI), consistent with AppPreferences /
 * SecureTokenStore / ProjectSyncStore.
 *
 * IMPORTANT DEVIATION FROM THE ORIGINAL SIGNATURES:
 * The requested signatures were
 *   uploadProject(folderNode, repoOwner, repoName, token): Result<String>
 *   commitChanges(folderNode, repoOwner, repoName, token): Result<String>
 * Two things beyond that turned out to be unavoidable:
 *  1. A [Context] is required to actually read file bytes (SAF content://
 *     URIs need a ContentResolver; extracted-zip files are plain java.io.File
 *     paths — both cases already exist in MainWorkspaceViewModel).
 *  2. A `folderIdentifier` is required because that's what ProjectSyncStore
 *     keys its saved state by (see ProjectSyncStore.kt) — without it there
 *     is no way to know "has this exact folder been synced before?".
 * Both are added as extra parameters; nothing about the requested behavior
 * changes.
 */
object GitHubRepository {

    private const val DEFAULT_BRANCH = "main"

    private val apiService: GitHubApiService by lazy { GitHubApiClient.create() }
    private val errorGson: Gson by lazy { Gson() }

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * First-ever push of [folderNode] from this app to [repoOwner]/[repoName].
     *
     * Non-destructive: if the target [branch] already has commits (e.g. the
     * repo was created on GitHub with a README), this layers the folder's
     * files on top of that existing tree instead of replacing it. If the
     * branch has no commits at all, this fails with a clear message asking
     * the user to make one first — the Git Data API this app uses can only
     * update an *existing* ref, not create a brand-new one.
     */
    suspend fun uploadProject(
        context: Context,
        folderNode: FileNode,
        folderIdentifier: String,
        repoOwner: String,
        repoName: String,
        token: String,
        branch: String = DEFAULT_BRANCH
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val authHeader = GitHubApiService.bearerToken(token)

            val flatFiles = flattenTree(folderNode)
            if (flatFiles.isEmpty()) {
                return@withContext Result.failure(Exception("No files found to upload."))
            }

            // Find out what the branch currently points to (must already
            // exist — see doc comment above).
            val refResponse = apiService.getRef(authHeader, repoOwner, repoName, branch)
            val (parentCommitSha, baseTreeSha) = when {
                refResponse.isSuccessful -> {
                    val commitSha = refResponse.body()!!.gitObject.sha
                    val commitResponse = apiService.getCommit(authHeader, repoOwner, repoName, commitSha)
                    if (!commitResponse.isSuccessful) {
                        return@withContext Result.failure(
                            Exception(describeError("Reading existing commit", commitResponse))
                        )
                    }
                    commitSha to commitResponse.body()!!.tree.sha
                }
                refResponse.code() == 404 -> {
                    return@withContext Result.failure(
                        Exception(
                            "Branch '$branch' on $repoOwner/$repoName has no commits yet. " +
                                "Create at least one commit (e.g. a README) on GitHub first, " +
                                "then try Upload again."
                        )
                    )
                }
                else -> {
                    return@withContext Result.failure(Exception(describeError("Checking branch", refResponse)))
                }
            }

            // Build a blob for every file and record its content hash for
            // future diffing by commitChanges().
            val fileHashes = mutableMapOf<String, String>()
            val treeEntries = mutableListOf<TreeEntry>()
            for (flatFile in flatFiles) {
                val bytes = readNodeBytes(context, flatFile.node)
                fileHashes[flatFile.repoPath] = sha256Hex(bytes)

                val blobResponse = apiService.createBlob(
                    authHeader, repoOwner, repoName,
                    BlobRequest(content = Base64.encodeToString(bytes, Base64.NO_WRAP))
                )
                if (!blobResponse.isSuccessful) {
                    return@withContext Result.failure(
                        Exception(describeError("Uploading '${flatFile.repoPath}'", blobResponse))
                    )
                }
                treeEntries.add(
                    TreeEntry(path = flatFile.repoPath, sha = blobResponse.body()!!.sha)
                )
            }

            val treeResponse = apiService.createTree(
                authHeader, repoOwner, repoName,
                TreeRequest(baseTree = baseTreeSha, tree = treeEntries)
            )
            if (!treeResponse.isSuccessful) {
                return@withContext Result.failure(Exception(describeError("Building tree", treeResponse)))
            }
            val newTreeSha = treeResponse.body()!!.sha

            val commitResponse = apiService.createCommit(
                authHeader, repoOwner, repoName,
                CommitRequest(
                    message = "Initial upload via GitHub Manager",
                    tree = newTreeSha,
                    parents = listOf(parentCommitSha)
                )
            )
            if (!commitResponse.isSuccessful) {
                return@withContext Result.failure(Exception(describeError("Creating commit", commitResponse)))
            }
            val newCommitSha = commitResponse.body()!!.sha

            val refUpdateResponse = apiService.updateRef(
                authHeader, repoOwner, repoName, branch,
                RefUpdateRequest(sha = newCommitSha, force = false)
            )
            if (!refUpdateResponse.isSuccessful) {
                return@withContext Result.failure(Exception(describeError("Updating branch", refUpdateResponse)))
            }

            // Only persist once every step above has actually succeeded.
            ProjectSyncStore.saveSyncMetadata(
                context, folderIdentifier,
                SyncMetadata(
                    repoOwner = repoOwner,
                    repoName = repoName,
                    lastCommitSha = newCommitSha,
                    lastTreeSha = newTreeSha,
                    fileHashes = fileHashes
                )
            )

            Result.success(newCommitSha)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Incremental push: diffs [folderNode]'s current file hashes against
     * the last saved [SyncMetadata] for [folderIdentifier] and only sends
     * changed/new/deleted files.
     *
     * Uses the *saved* lastCommitSha as the new commit's parent (not a
     * freshly-fetched ref) on purpose: PATCHing the ref with force=false
     * will then be rejected by GitHub if it isn't a fast-forward — i.e. if
     * someone else pushed to this branch outside the app since our last
     * sync. That rejection is the safety net, not a bug.
     */
    suspend fun commitChanges(
        context: Context,
        folderNode: FileNode,
        folderIdentifier: String,
        repoOwner: String,
        repoName: String,
        token: String,
        branch: String = DEFAULT_BRANCH
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val saved = ProjectSyncStore.getSyncMetadata(context, folderIdentifier)
                ?: return@withContext Result.failure(
                    Exception("This folder has never been uploaded yet. Use Upload first.")
                )

            val authHeader = GitHubApiService.bearerToken(token)
            val flatFiles = flattenTree(folderNode)

            // Hash every current file so we know exactly what changed.
            val currentHashes = mutableMapOf<String, String>()
            val bytesByPath = mutableMapOf<String, ByteArray>()
            for (flatFile in flatFiles) {
                val bytes = readNodeBytes(context, flatFile.node)
                currentHashes[flatFile.repoPath] = sha256Hex(bytes)
                bytesByPath[flatFile.repoPath] = bytes
            }

            val changedPaths = currentHashes.filter { (path, hash) ->
                saved.fileHashes[path] != hash
            }.keys
            val deletedPaths = saved.fileHashes.keys - currentHashes.keys

            if (changedPaths.isEmpty() && deletedPaths.isEmpty()) {
                // Nothing to do — not an error, just nothing to push.
                return@withContext Result.success("NO_CHANGES")
            }

            val treeEntries = mutableListOf<TreeEntry>()

            for (path in changedPaths) {
                val bytes = bytesByPath.getValue(path)
                val blobResponse = apiService.createBlob(
                    authHeader, repoOwner, repoName,
                    BlobRequest(content = Base64.encodeToString(bytes, Base64.NO_WRAP))
                )
                if (!blobResponse.isSuccessful) {
                    return@withContext Result.failure(Exception(describeError("Uploading '$path'", blobResponse)))
                }
                treeEntries.add(TreeEntry(path = path, sha = blobResponse.body()!!.sha))
            }
            for (path in deletedPaths) {
                // Explicit null sha == "remove this path" per GitHub Trees API.
                treeEntries.add(TreeEntry(path = path, sha = null))
            }

            val treeResponse = apiService.createTree(
                authHeader, repoOwner, repoName,
                TreeRequest(baseTree = saved.lastTreeSha, tree = treeEntries)
            )
            if (!treeResponse.isSuccessful) {
                return@withContext Result.failure(Exception(describeError("Building tree", treeResponse)))
            }
            val newTreeSha = treeResponse.body()!!.sha

            val commitResponse = apiService.createCommit(
                authHeader, repoOwner, repoName,
                CommitRequest(
                    message = "Update project via GitHub Manager",
                    tree = newTreeSha,
                    parents = listOf(saved.lastCommitSha)
                )
            )
            if (!commitResponse.isSuccessful) {
                return@withContext Result.failure(Exception(describeError("Creating commit", commitResponse)))
            }
            val newCommitSha = commitResponse.body()!!.sha

            val refUpdateResponse = apiService.updateRef(
                authHeader, repoOwner, repoName, branch,
                RefUpdateRequest(sha = newCommitSha, force = false)
            )
            if (!refUpdateResponse.isSuccessful) {
                return@withContext Result.failure(
                    Exception(
                        "Updating branch failed (${describeError("", refUpdateResponse)}). " +
                            "This usually means the branch changed outside the app since the " +
                            "last sync — pull those changes into a fresh Upload before retrying."
                    )
                )
            }

            ProjectSyncStore.saveSyncMetadata(
                context, folderIdentifier,
                SyncMetadata(
                    repoOwner = repoOwner,
                    repoName = repoName,
                    lastCommitSha = newCommitSha,
                    lastTreeSha = newTreeSha,
                    fileHashes = currentHashes
                )
            )

            Result.success(newCommitSha)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Internal helpers ────────────────────────────────────────────────

    private data class FlatFile(val repoPath: String, val node: FileNode)

    /**
     * Flattens a [FileNode] tree into repo-relative file paths.
     *
     * - If [root] is itself a single file (not a folder), it becomes one
     *   entry named after itself.
     * - If [root] is a folder, its own name is NOT included in the repo
     *   paths — only its *contents* are uploaded, e.g. importing a folder
     *   "MyApp" containing "src/Main.kt" produces the repo path
     *   "src/Main.kt", not "MyApp/src/Main.kt".
     */
    private fun flattenTree(root: FileNode): List<FlatFile> {
        if (!root.isFolder) return listOf(FlatFile(root.name, root))
        val result = mutableListOf<FlatFile>()
        collectChildren(root, "", result)
        return result
    }

    private fun collectChildren(node: FileNode, relativePath: String, out: MutableList<FlatFile>) {
        for (child in node.children) {
            val childPath = if (relativePath.isEmpty()) child.name else "$relativePath/${child.name}"
            if (child.isFolder) {
                collectChildren(child, childPath, out)
            } else {
                out.add(FlatFile(childPath, child))
            }
        }
    }

    /**
     * Reads a file's raw bytes regardless of whether it came from a SAF
     * folder pick (content:// URI, stored in [FileNode.path]) or from a
     * zip extracted into app-internal storage (plain absolute file path —
     * see MainWorkspaceViewModel.fileToNode).
     */
    private fun readNodeBytes(context: Context, node: FileNode): ByteArray {
        val path = node.path
        return if (path.startsWith("content://")) {
            context.contentResolver.openInputStream(Uri.parse(path))?.use { it.readBytes() }
                ?: throw IOException("Could not open '${node.name}'.")
        } else {
            File(path).readBytes()
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** Turns a failed Retrofit [Response] into a readable "<action>: <reason>" string. */
    private fun describeError(action: String, response: Response<*>): String {
        val reason = try {
            val body = response.errorBody()?.string()
            val parsed = body?.let { errorGson.fromJson(it, GitHubErrorResponse::class.java) }
            parsed?.message ?: "HTTP ${response.code()}"
        } catch (e: Exception) {
            "HTTP ${response.code()}"
        }
        return if (action.isEmpty()) reason else "$action failed: $reason"
    }
}

