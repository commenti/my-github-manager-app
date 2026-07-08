package com.yourname.githubmanager.data.filesystem

import com.yourname.githubmanager.domain.FileNode

/**
 * Storage-source-agnostic contract for file operations.
 *
 * This interface abstracts the underlying file system implementation so that
 * editor, create, and delete screens do not need to know whether the real
 * storage is SAF (Storage Access Framework) based live device folders or
 * plain java.io.File based extracted zip content.
 */
interface ProjectFileSystem {
    suspend fun readText(node: FileNode): String
    suspend fun writeText(node: FileNode, content: String)
    suspend fun createFile(parent: FileNode, name: String): FileNode
    suspend fun createFolder(parent: FileNode, name: String): FileNode
    suspend fun delete(node: FileNode)
    suspend fun rename(node: FileNode, newName: String): FileNode
}
