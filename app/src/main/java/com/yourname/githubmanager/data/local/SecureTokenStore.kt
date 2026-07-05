// File: app/src/main/java/com/yourname/githubmanager/data/local/SecureTokenStore.kt
package com.yourname.githubmanager.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the GitHub Personal Access Token using [EncryptedSharedPreferences]
 * so the raw token never sits in plain text on disk.
 *
 * This is a plain singleton object (no DI) to match [AppPreferences].
 * The underlying [SharedPreferences] instance is created lazily and cached
 * per-process the first time it's needed.
 *
 * SECURITY NOTES:
 *  - Never log the token (no Log.d / Log.e / println with the token value).
 *  - Never write the token anywhere else (no DataStore, no plain SharedPreferences,
 *    no crash-reporting breadcrumbs).
 *  - [getToken] returns null if no token has been saved yet.
 */
object SecureTokenStore {

    private const val PREFS_FILE_NAME = "secure_token_prefs"
    private const val KEY_GITHUB_TOKEN = "github_pat"

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences {
        return cachedPrefs ?: synchronized(this) {
            cachedPrefs ?: buildEncryptedPrefs(context.applicationContext).also {
                cachedPrefs = it
            }
        }
    }

    private fun buildEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** Saves (overwrites) the GitHub Personal Access Token. */
    fun saveToken(context: Context, token: String) {
        prefs(context).edit()
            .putString(KEY_GITHUB_TOKEN, token)
            .apply()
    }

    /** Returns the saved token, or null if none has been saved. */
    fun getToken(context: Context): String? {
        return prefs(context).getString(KEY_GITHUB_TOKEN, null)
    }

    /** Removes the saved token, e.g. on "sign out" or "disconnect GitHub". */
    fun clearToken(context: Context) {
        prefs(context).edit()
            .remove(KEY_GITHUB_TOKEN)
            .apply()
    }

    /** Convenience check without exposing the token value itself. */
    fun hasToken(context: Context): Boolean = getToken(context) != null
}
