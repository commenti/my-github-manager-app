package com.yourname.githubmanager.data.filesystem

import com.yourname.githubmanager.domain.FileNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.lang.SecurityException

// TODO: Replace this sealed class with the existing error-handling pattern
// from Phase 1-4 to remain consistent. (Currently duplicated from SafFileSystem.kt)
sealed class FileSystemException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class PermissionDenied(message: String, cause: Throwable? = null) : FileSystemException(message, cause)
    class FileNotFound(message: String, cause: Throwable? = null) : FileSystemException(message, cause)
    class OperationFailed(message: String, cause: Throwable? = null) : FileSystemException(message, cause)
}

/**
 * Plain java.io.File based implementation of [ProjectFileSystem] for extracted
 * zip content located inside the app's internal storage.
 *
 * Assumes [FileNode] provides an absolute file path (e.g., via a `path` property)
 * that points to a location within the app's internal files directory (as set up
 * by zip-extraction logic in Phase 1-4). If the actual field name differs, adjust
 * the `toFile()` helper accordingly.
 */
class LocalFileSystem : ProjectFileSystem {

    private fun toFile(node: FileNode): File {
        // Assumption: FileNode has a `path: String` property holding the absolute file path.
        // If the field is named differently (e.g., `localPath`), change this line.
        return File(node.path)
    }

    override suspend fun readText(node: FileNode): String = withContext(Dispatchers.IO) {
        try {
            val file = toFile(node)
            if (!file.exists()) throw FileSystemException.FileNotFound("File not found: ${file.path}")
            file.readText()
        } catch (e: SecurityException) {
            throw FileSystemException.PermissionDenied("Permission denied reading file: ${node.name}", e)
        } catch (e: FileSystemException) {
            throw e
        } catch (e: Exception) {
            throw FileSystemException.OperationFailed("Error reading file: ${node.name}", e)
        }
    }

    override suspend fun writeText(node: FileNode, content: String) = withContext(Dispatchers.IO) {
        try {
            val file = toFile(node)
            file.parentFile?.mkdirs() // ensure parent exists
            file.writeText(content)
        } catch (e: SecurityException) {
            throw FileSystemException.PermissionDenied("Permission denied writing file: ${node.name}", e)
        } catch (e: FileSystemException) {
            throw e
        } catch (e: Exception) {
            throw FileSystemException.OperationFailed("Error writing file: ${node.name}", e)
        }
    }

    override suspend fun createFile(parent: FileNode, name: String): FileNode = withContext(Dispatchers.IO) {
        try {
            val parentFile = toFile(parent)
            if (!parentFile.isDirectory) {
                throw FileSystemException.OperationFailed("Parent is not a directory: ${parent.name}")
            }
            val newFile = File(parentFile, name)
            val created = newFile.createNewFile()
            if (!created) {
                throw FileSystemException.OperationFailed("Could not create file: $name (may already exist)")
            }
            // Assumption: FileNode can be constructed with (name, path, isDirectory, children).
            FileNode(
                name = name,
                path = newFile.absolutePath,
                isDirectory = false,
                children = null // or emptyList()
            )
        } catch (e: SecurityException) {
            throw FileSystemException.PermissionDenied("Permission denied creating file: $name", e)
        } catch (e: FileSystemException) {
            throw e
        } catch (e: Exception) {
            throw FileSystemException.OperationFailed("Error creating file: $name", e)
        }
    }

    override suspend fun createFolder(parent: FileNode, name: String): FileNode = withContext(Dispatchers.IO) {
        try {
            val parentFile = toFile(parent)
            if (!parentFile.isDirectory) {
                throw FileSystemException.OperationFailed("Parent is not a directory: ${parent.name}")
            }
            val newFolder = File(parentFile, name)
            val created = newFolder.mkdir()
            if (!created) {
                throw FileSystemException.OperationFailed("Could not create folder: $name (may already exist)")
            }
            FileNode(
                name = name,
                path = newFolder.absolutePath,
                isDirectory = true,
                children = null
            )
        } catch (e: SecurityException) {
            throw FileSystemException.PermissionDenied("Permission denied creating folder: $name", e)
        } catch (e: FileSystemException) {
            throw e
        } catch (e: Exception) {
            throw FileSystemException.OperationFailed("Error creating folder: $name", e)
        }
    }

    override suspend fun delete(node: FileNode) = withContext(Dispatchers.IO) {
        try {
            val file = toFile(node)
            if (!file.exists()) throw FileSystemException.FileNotFound("File not found: ${file.path}")
            val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
            if (!deleted) {
                throw FileSystemException.OperationFailed("Failed to delete: ${file.path}")
            }
        } catch (e: SecurityException) {
            throw FileSystemException.PermissionDenied("Permission denied deleting: ${node.name}", e)
        } catch (e: FileSystemException) {
            throw e
        } catch (e: Exception) {
            throw FileSystemException.OperationFailed("Error deleting: ${node.name}", e)
        }
    }

    override suspend fun rename(node: FileNode, newName: String): FileNode = withContext(Dispatchers.IO) {
        try {
            val file = toFile(node)
            if (!file.exists()) throw FileSystemException.FileNotFound("File not found: ${file.path}")
            val newFile = File(file.parentFile, newName)
            val success = file.renameTo(newFile)
            if (!success) {
                throw FileSystemException.OperationFailed("Rename failed: ${file.name} -> $newName")
            }
            // Preserve original isDirectory and children.
            FileNode(
                name = newName,
                path = newFile.absolutePath,
                isDirectory = node.isDirectory, // assume node.isDirectory exists
                children = node.children
            )
        } catch (e: SecurityException) {
            throw FileSystemException.PermissionDenied("Permission denied renaming: ${node.name}", e)
        } catch (e: FileSystemException) {
            throw e
        } catch (e: Exception) {
            throw FileSystemException.OperationFailed("Error renaming: ${node.name}", e)
        }
    }
}
