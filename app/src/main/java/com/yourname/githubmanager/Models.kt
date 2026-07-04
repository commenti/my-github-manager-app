package com.yourname.githubmanager.domain

import java.io.File

/**
 * Represents a single file or folder node within a file-tree UI.
 * Folders hold their children recursively, allowing the whole
 * extracted/selected directory to be modeled as one tree.
 *
 * @param name Display name of the file or folder (not the full path).
 * @param path Absolute filesystem path to this file or folder.
 * @param isFolder True if this node represents a directory.
 * @param children Child nodes; always empty for files, may be empty
 *   for an empty folder. Populated recursively for folders.
 * @param sizeBytes Size in bytes. For folders, this is optionally the
 *   aggregated size of all descendants (0 if not computed).
 * @param lastModified Last-modified timestamp in epoch millis.
 * @param extension Lowercased file extension without the dot, empty for folders.
 * @param isExpanded UI-only state indicating whether a folder node is
 *   currently expanded in the tree view. Ignored for files.
 */
data class FileNode(
    val name: String,
    val path: String,
    val isFolder: Boolean,
    val children: List<FileNode> = emptyList(),
    val sizeBytes: Long = 0L,
    val lastModified: Long = 0L,
    val extension: String = "",
    val isExpanded: Boolean = false
) {

    /** Total number of descendant nodes (files + folders), excluding this node itself. */
    fun descendantCount(): Int =
        children.sumOf { child -> 1 + child.descendantCount() }

    /** Returns true if this folder node has no children. Always false for files. */
    fun isEmptyFolder(): Boolean = isFolder && children.isEmpty()

    /** Returns a new copy of this node with the expansion state toggled. */
    fun toggleExpanded(): FileNode = copy(isExpanded = !isExpanded)

    companion object {

        /**
         * Recursively builds a [FileNode] tree from a real [File] on disk.
         * Intended for use after zip extraction or when the user selects
         * an existing folder to browse.
         *
         * @param file The root file or directory to convert.
         * @param computeFolderSize If true, aggregates descendant file
         *   sizes into each folder's [sizeBytes]. Disabled by default to
         *   avoid extra IO cost on large trees.
         */
        fun fromFile(file: File, computeFolderSize: Boolean = false): FileNode {
            val isDir = file.isDirectory

            val childNodes: List<FileNode> = if (isDir) {
                file.listFiles()
                    ?.sortedWith(
                        compareByDescending<File> { it.isDirectory }
                            .thenBy { it.name.lowercase() }
                    )
                    ?.map { child -> fromFile(child, computeFolderSize) }
                    ?: emptyList()
            } else {
                emptyList()
            }

            val size = when {
                !isDir -> file.length()
                computeFolderSize -> childNodes.sumOf { it.sizeBytes }
                else -> 0L
            }

            return FileNode(
                name = file.name,
                path = file.absolutePath,
                isFolder = isDir,
                children = childNodes,
                sizeBytes = size,
                lastModified = file.lastModified(),
                extension = if (isDir) "" else file.extension.lowercase()
            )
        }

        /**
         * Builds a [FileNode] tree rooted at the given directory path.
         * Convenience overload for use when only a path string is available
         * (e.g., the destination path returned by ZipExtractor's WorkInfo output).
         */
        fun fromPath(rootPath: String, computeFolderSize: Boolean = false): FileNode? {
            val rootFile = File(rootPath)
            return if (rootFile.exists()) fromFile(rootFile, computeFolderSize) else null
        }
    }
}
