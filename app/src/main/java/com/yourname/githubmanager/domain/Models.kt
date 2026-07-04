package com.yourname.githubmanager.domain

/**
 * Represents a single node in a file/folder tree.
 *
 * @param name     The display name of the file or folder (e.g., "src", "main.kt").
 * @param filePath The full path or URI string that uniquely identifies this node
 *                 (for a SAF document, this is the content URI; for a regular file, the absolute path).
 * @param isFolder Whether this node is a folder/directory.
 * @param children The list of child [FileNode]s if this is a folder and has been expanded.
 *                 Empty for files or empty folders.
 */
data class FileNode(
    val name: String,
    val filePath: String,
    val isFolder: Boolean,
    val children: List<FileNode> = emptyList()
)
