// File: app/src/main/java/com/yourname/githubmanager/data/filesystem/FileSystemException.kt
package com.yourname.githubmanager.data.filesystem

// TODO: If Phase 1-4 already has a common app-wide error-handling pattern
// (e.g. a shared Result/exception hierarchy), replace this with that instead
// of keeping a filesystem-specific one.
sealed class FileSystemException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class PermissionDenied(message: String, cause: Throwable? = null) : FileSystemException(message, cause)
    class FileNotFound(message: String, cause: Throwable? = null) : FileSystemException(message, cause)
    class OperationFailed(message: String, cause: Throwable? = null) : FileSystemException(message, cause)
}

