// File: app/src/main/java/com/yourname/githubmanager/domain/FileNode.kt
package com.yourname.githubmanager.domain

/**
 * Represents a single entry (file or folder) in the project's file tree,
 * regardless of whether it originated from a SAF-imported live folder or
 * from an extracted zip in local app storage.
 *
 * [path] is intentionally a single generic String field that holds either:
 *  - an absolute java.io.File path (for [com.yourname.githubmanager.data.filesystem.LocalFileSystem]), or
 *  - a content:// URI string (for [com.yourname.githubmanager.data.filesystem.SafFileSystem])
 *
 * Keeping this as one field (rather than separate `path`/`uri` properties)
 * avoids the two filesystem implementations having to know about each
 * other's storage model — each just parses [path] the way it needs to.
 *
 * TODO: If Phase 1-4 already builds a file tree elsewhere (e.g. a tree-view
 * screen or repository) with a differently-shaped node, reconcile that
 * shape with this one rather than keeping two parallel models.
 */
data class FileNode(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val children: List<FileNode>? = null
)

