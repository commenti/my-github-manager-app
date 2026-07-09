// File: app/src/main/java/com/yourname/githubmanager/data/filesystem/LocalFileSystem.kt
package com.yourname.githubmanager.data.filesystem

import com.yourname.githubmanager.domain.FileNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

class LocalFileSystem : ProjectFileSystem {

    private fun toFile(node: FileNode): File {
        return File(node.path)
    }

    override suspend fun readText(node: FileNode): String = withContext(Dispatchers.IO) {
        try {
            val file = toFile(node)
            if (!file.exists()) throw FileSystemException.FileNotFound("File not found: ${file.path}")
            file.readText()
        } catch (e: SecurityException) {
            throw FileSystemException.PermissionDenied("Permission denied reading file: ${node.name}", e)
        } catch (e: FileNotFoundException) {
            throw FileSystemException.FileNotFound("File not found: ${node.name}", e)
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
            FileNode(
                name = name,
                path = newFile.absolutePath,
                isFolder = false,
                children = emptyList()
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
                isFolder = true,
                children = emptyList()
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
            // Preserve original isFolder and children.
            FileNode(
                name = newName,
                path = newFile.absolutePath,
                isFolder = node.isFolder,
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
