// File: app/src/main/java/com/yourname/githubmanager/data/github/GitHubModels.kt
package com.yourname.githubmanager.data.github

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * Data-transfer objects for the GitHub "Git Data" REST API — the low-level
 * blob/tree/commit/ref endpoints used for bulk pushes, as opposed to the
 * Contents API (which only handles one file per request).
 *
 * Reference: https://docs.github.com/en/rest/git
 *
 * All of these are plain Gson-annotated data classes; no logic lives here.
 * Request-building and orchestration (blob -> tree -> commit -> ref) lives
 * in GitHubRepository.kt.
 */

// ── Blobs ────────────────────────────────────────────────────────────────
// POST /repos/{owner}/{repo}/git/blobs

/** One file's raw content, base64-encoded, ready to become a git blob. */
@Serializable
data class BlobRequest(
    @SerialName("content") val content: String,
    @SerialName("encoding") val encoding: String = "base64"
)

@Serializable
data class BlobResponse(
    @SerialName("sha") val sha: String,
    @SerialName("url") val url: String
)

// ── Trees ────────────────────────────────────────────────────────────────
// POST /repos/{owner}/{repo}/git/trees

/**
 * One entry inside a git tree — maps a repo-relative file path to a blob sha.
 *
 * @param path Repo-relative path, e.g. "app/src/main/.../Foo.kt"
 * @param mode "100644" = normal file, "100755" = executable, "040000" = subdirectory
 * @param type "blob" for files, "tree" for subdirectories (we only ever send "blob"
 *             entries; GitHub builds the directory structure for us from the paths)
 * @param sha  The blob sha returned from a prior BlobResponse
 */
@Serializable
data class TreeEntry(
    @SerialName("path") val path: String,
    @SerialName("mode") val mode: String = "100644",
    @SerialName("type") val type: String = "blob",
    @SerialName("sha") val sha: String
)

/**
 * @param baseTree The sha of the tree to layer these entries on top of.
 *                 Omit (null) only for a brand-new repo with no prior tree —
 *                 for incremental commits this should be the previous
 *                 commit's tree sha, so unmodified files are carried over
 *                 without needing to be re-sent.
 * @param tree     Only the changed/new file entries — GitHub merges them
 *                 into baseTree automatically.
 */
@Serializable
data class TreeRequest(
    @SerialName("base_tree") val baseTree: String? = null,
    @SerialName("tree") val tree: List<TreeEntry>
)

@Serializable
data class TreeResponse(
    @SerialName("sha") val sha: String,
    @SerialName("url") val url: String,
    @SerialName("tree") val tree: List<TreeEntry> = emptyList()
)

// ── Commits ──────────────────────────────────────────────────────────────
// POST /repos/{owner}/{repo}/git/commits

/**
 * @param parents Empty list only for the very first commit in a repo's
 *                history. For every subsequent commit this must contain
 *                exactly the previous commit's sha, or history will fork.
 */
@Serializable
data class CommitRequest(
    @SerialName("message") val message: String,
    @SerialName("tree") val tree: String,
    @SerialName("parents") val parents: List<String> = emptyList()
)

@Serializable
data class CommitResponse(
    @SerialName("sha") val sha: String,
    @SerialName("url") val url: String,
    @SerialName("message") val message: String
)

// ── Refs ─────────────────────────────────────────────────────────────────
// GET  /repos/{owner}/{repo}/git/refs/heads/{branch}
// PATCH /repos/{owner}/{repo}/git/refs/heads/{branch}

/**
 * @param force Should stay false in normal use — a false value makes GitHub
 *              reject the update if it isn't a fast-forward, which is the
 *              safety net that stops us from silently overwriting commits
 *              made outside the app (e.g. from a browser or another device).
 */
@Serializable
data class RefUpdateRequest(
    @SerialName("sha") val sha: String,
    @SerialName("force") val force: Boolean = false
)

@Serializable
data class RefObject(
    @SerialName("sha") val sha: String,
    @SerialName("type") val type: String,
    @SerialName("url") val url: String
)

@Serializable
data class RefResponse(
    @SerialName("ref") val ref: String,
    @SerialName("node_id") val nodeId: String? = null,
    @SerialName("url") val url: String,
    @SerialName("object") val gitObject: RefObject
)

// ── Errors ───────────────────────────────────────────────────────────────

/**
 * Shape of GitHub's standard error body, e.g. `{"message": "Bad credentials"}`.
 * Used by GitHubRepository to surface a readable message instead of a raw
 * HTTP status code when a call fails.
 */
@Serializable
data class GitHubErrorResponse(
    @SerialName("message") val message: String? = null,
    @SerialName("documentation_url") val documentationUrl: String? = null
)
