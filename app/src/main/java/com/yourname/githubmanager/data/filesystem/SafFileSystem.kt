package com.yourname.githubmanager.data.filesystem

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.yourname.githubmanager.model.FileNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.lang.SecurityException

// TODO: Replace this sealed class with the existing error-handling pattern
// from Phase 1-4 (e.g., if a common FileSystemException hierarchy already exists,
// reuse it to maintain consistency).
sealed class FileSystemException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class PermissionDenied(message: String, cause: Throwable? = null) : FileSystemException(message, cause)
    class FileNotFound(message: String, cause: Throwable? = null) : FileSystemException(message, cause)
    class OperationFailed(message: String, cause: Throwable? = null) : FileSystemException(message, cause)
}

/**
 * SAF-based implementation of [ProjectFileSystem] for live device folders
 * imported via Storage Access Framework.
 *
 * Expects [FileNode.uri] to contain a valid content:// URI that can be
 * resolved with [DocumentFile.fromSingleUri]. All blocking I/O is performed
 * on [Dispatchers.IO].
 */
class SafFileSystem(private val context: Context) : ProjectFileSystem {

    private fun toDocumentFile(node: FileNode): DocumentFile? {
        // Assumption: FileNode.uri is a non-null String holding a content URI.
        val uri = Uri.parse(node.uri)
        return DocumentFile.fromSingleUri(context, uri)
    }

    override suspend fun readText(node: FileNode): String = withContext(Dispatchers.IO) {
        try {
            val doc = toDocumentFile(node)
                ?: throw FileSystemException.FileNotFound("Cannot resolve DocumentFile for node: ${node.name}")
            context.contentResolver.openInputStream(doc.uri)?.bufferedReader().use { reader ->
                reader?.readText() ?: throw FileSystemException.OperationFailed("Failed to read file: ${node.name}")
            }
        } catch (e: SecurityException) {
            throw FileSystemException.PermissionDenied("Permission lost for file: ${node.name}", e)
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
            val doc = toDocumentFile(node)
                ?: throw FileSystemException.FileNotFound("Cannot resolve DocumentFile for node: ${node.name}")
            context.contentResolver.openOutputStream(doc.uri)?.bufferedWriter().use { writer ->
                writer?.write(content)
                writer?.flush()
            } ?: throw FileSystemException.OperationFailed("Failed to open output stream for: ${node.name}")
        } catch (e: SecurityException) {
            throw FileSystemException.PermissionDenied("Permission lost for file: ${node.name}", e)
        } catch (e: FileNotFoundException) {
            throw FileSystemException.FileNotFound("File not found: ${node.name}", e)
        } catch (e: FileSystemException) {
            throw e
        } catch (e: Exception) {
            throw FileSystemException.OperationFailed("Error writing file: ${node.name}", e)
        }
    }

    override suspend fun createFile(parent: FileNode, name: String): FileNode = withContext(Dispatchers.IO) {
        try {
            val parentDoc = toDocumentFile(parent)
                ?: throw FileSystemException.FileNotFound("Parent folder not found: ${parent.name}")
            if (!parentDoc.isDirectory) {
                throw FileSystemException.OperationFailed("Parent is not a directory: ${parent.name}")
            }
            val newDoc = parentDoc.createFile("text/plain", name)
                ?: throw FileSystemException.OperationFailed("Could not create file: $name")
            // Assumption: FileNode constructor accepts (name, uri, isDirectory, ...)
            // Adjust fields to match the actual FileNode definition from Phase 1-4.
            FileNode(
                name = name,
                uri = newDoc.uri.toString(),
                isDirectory = false,
                children = null // or emptyList() depending on the actual data class
            )
        } catch (e: SecurityException) {
            throw FileSystemException.PermissionDenied("Permission lost creating file: $name", e)
        } catch (e: FileSystemException) {
            throw e
        } catch (e: Exception) {
            throw FileSystemException.OperationFailed("Error creating file: $name", e)
        }
    }

    override suspend fun createFolder(parent: FileNode, name: String): FileNode = withContext(Dispatchers.IO) {
        try {
            val parentDoc = toDocumentFile(parent)
                ?: throw FileSystemException.FileNotFound("Parent folder not found: ${parent.name}")
            if (!parentDoc.isDirectory) {
                throw FileSystemException.OperationFailed("Parent is not a directory: ${parent.name}")
            }
            val newDoc = parentDoc.createDirectory(name)
                ?: throw FileSystemException.OperationFailed("Could not create folder: $name")
            FileNode(
                name = name,
                uri = newDoc.uri.toString(),
                isDirectory = true,
                children = null
            )
        } catch (e: SecurityException) {
            throw FileSystemException.PermissionDenied("Permission lost creating folder: $name", e)
        } catch (e: FileSystemException) {
            throw e
        } catch (e: Exception) {
            throw FileSystemException.OperationFailed("Error creating folder: $name", e)
        }
    }

    override suspend fun delete(node: FileNode) = withContext(Dispatchers.IO) {
        try {
            val doc = toDocumentFile(node)
                ?: throw FileSystemException.FileNotFound("Cannot resolve DocumentFile for node: ${node.name}")
            val deleted = doc.delete()
            if (!deleted) {
                throw FileSystemException.OperationFailed("Failed to delete: ${node.name}")
            }
        } catch (e: SecurityException) {
            throw FileSystemException.PermissionDenied("Permission lost deleting: ${node.name}", e)
        } catch (e: FileSystemException) {
            throw e
        } catch (e: Exception) {
            throw FileSystemException.OperationFailed("Error deleting: ${node.name}", e)
        }
    }

    override suspend fun rename(node: FileNode, newName: String): FileNode = withContext(Dispatchers.IO) {
        try {
            val doc = toDocumentFile(node)
                ?: throw FileSystemException.FileNotFound("Cannot resolve DocumentFile for node: ${node.name}")
            // Same-folder rename only (no path/move, as per spec).
            val renamedDoc = doc.renameTo(newName)
                ?: throw FileSystemException.OperationFailed("Rename failed for: ${node.name}")
            // Preserve original node attributes (isDirectory, children).
            FileNode(
                name = newName,
                uri = renamedDoc.uri.toString(),
                isDirectory = node.isDirectory,  // assume node.isDirectory exists
                children = node.children
            )
        } catch (e: SecurityException) {
            throw FileSystemException.PermissionDenied("Permission lost renaming: ${node.name}", e)
        } catch (e: FileSystemException) {
            throw e
        } catch (e: Exception) {
            throw FileSystemException.OperationFailed("Error renaming: ${node.name}", e)
        }
    }
}
