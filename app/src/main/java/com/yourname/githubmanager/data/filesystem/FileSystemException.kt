// File: app/src/main/java/com/yourname/githubmanager/data/filesystem/FileSystemException.kt
package com.yourname.githubmanager.data.filesystem

/**
 * Common exception hierarchy for [ProjectFileSystem] implementations.
 *
 * LocalFileSystem.kt and SafFileSystem.kt both call the subtypes below
 * (e.g. FileSystemException.FileNotFound(...)), so this must be a sealed
 * class with those three subtypes — a single flat class would not compile
 * against that usage.
 */
sealed class FileSystemException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    class FileNotFound(message: String, cause: Throwable? = null) : FileSystemException(message, cause)

    class PermissionDenied(message: String, cause: Throwable? = null) : FileSystemException(message, cause)

    class OperationFailed(message: String, cause: Throwable? = null) : FileSystemException(message, cause)
}
