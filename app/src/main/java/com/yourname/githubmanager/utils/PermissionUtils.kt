package com.yourname.githubmanager.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Utility functions for handling SAF (Storage Access Framework) permissions.
 *
 * Provides helpers to check and request persistent URI permissions,
 * displaying Snackbar feedback instead of crashing on failure.
 */
object PermissionUtils {

    /**
     * Checks if the app already holds a persisted read permission for the given [uri].
     */
    fun hasPersistableUriPermission(context: Context, uri: Uri): Boolean {
        val persistedPermissions = context.contentResolver.persistedUriPermissions
        return persistedPermissions.any { it.uri == uri && it.isReadPermission }
    }

    /**
     * Attempts to take a persistable URI permission for the given [uri].
     *
     * @return `true` if the permission was successfully granted (or already held),
     *         `false` if a [SecurityException] occurred (e.g., the user revoked it).
     */
    fun takePersistableUriPermission(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            true
        } catch (e: SecurityException) {
            false
        }
    }

    /**
     * Ensures the app holds a persistable URI permission for [uri].
     * If not already held, attempts to take it and displays a [SnackbarHostState] message
     * on success or failure. This method is safe to call from any thread.
     *
     * @param context Application context.
     * @param uri The content URI for which the permission is needed.
     * @param snackbarHostState The Snackbar host to show messages.
     * @param scope A [CoroutineScope] in which to launch the Snackbar display.
     */
    fun ensurePersistableUriPermission(
        context: Context,
        uri: Uri,
        snackbarHostState: SnackbarHostState,
        scope: CoroutineScope
    ) {
        if (hasPersistableUriPermission(context, uri)) return

        val granted = takePersistableUriPermission(context, uri)
        scope.launch {
            if (granted) {
                snackbarHostState.showSnackbar("Folder access permission saved.")
            } else {
                snackbarHostState.showSnackbar(
                    "Could not obtain folder access permission. Some features may be limited."
                )
            }
        }
    }
}
