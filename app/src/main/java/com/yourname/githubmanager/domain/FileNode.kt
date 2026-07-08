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
 * [children] is non-nullable — leaf files simply have an empty list, so
 * callers (e.g. [com.yourname.githubmanager.data.github.GitHubRepository]'s
 * tree-flattening logic, [com.yourname.githubmanager.ui.components.FileTreeItem])
 * can iterate it directly without null-checking.
 */
data class FileNode(
    val name: String,
    val path: String,
    val isFolder: Boolean,
    val children: List<FileNode> = emptyList()
)
