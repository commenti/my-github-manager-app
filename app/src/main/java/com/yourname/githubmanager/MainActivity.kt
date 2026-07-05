// File: app/src/main/java/com/yourname/githubmanager/MainActivity.kt
package com.yourname.githubmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.yourname.githubmanager.navigation.AppNavigator
import com.yourname.githubmanager.ui.theme.GitHubManagerTheme

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import android.widget.Toast
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Single Activity that hosts the entire Compose UI.
 * Sets up edge-to-edge display and delegates all navigation
 * to [AppNavigator].
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            var showCrashDialog by remember { mutableStateOf(false) }
            var crashLogContent by remember { mutableStateOf<String?>(null) }
            var crashLogUri by remember { mutableStateOf<Uri?>(null) }

            LaunchedEffect(Unit) {
                val crashLogs = findCrashLogsInDownloads(context)
                if (crashLogs.isNotEmpty()) {
                    // Get the most recent crash log
                    val latestCrashLog = crashLogs.maxByOrNull { it.first } // Pair<Long, Uri>
                    latestCrashLog?.let {
                        crashLogUri = it.second
                        crashLogContent = readCrashLogContent(context, it.second)
                        if (crashLogContent != null) {
                            showCrashDialog = true
                        }
                    }
                }
            }

            GitHubManagerTheme {
                if (showCrashDialog && crashLogContent != null) {
                    CrashLogDialog(
                        crashLog = crashLogContent!!,
                        onDismiss = {
                            showCrashDialog = false
                            crashLogUri?.let { uri ->
                                deleteCrashLog(context, uri)
                            }
                        },
                        onShare = {
                            crashLogUri?.let { uri ->
                                shareCrashLog(context, uri)
                            }
                        }
                    )
                }
                AppNavigator()
            }
        }
    }

    private fun findCrashLogsInDownloads(context: Context): List<Pair<Long, Uri>> {
        val crashLogs = mutableListOf<Pair<Long, Uri>>()
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED
        )

        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("crash_log_%.txt")
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val dateAddedColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val dateAdded = it.getLong(dateAddedColumn)
                val contentUri = Uri.withAppendedPath(collection, id.toString())
                crashLogs.add(Pair(dateAdded, contentUri))
            }
        }
        return crashLogs
    }

    private fun readCrashLogContent(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use {
                BufferedReader(InputStreamReader(it)).readText()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error reading crash log: ", e)
            null
        }
    }

    private fun deleteCrashLog(context: Context, uri: Uri) {
        try {
            context.contentResolver.delete(uri, null, null)
            Log.i("MainActivity", "Crash log deleted: $uri")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error deleting crash log: ", e)
        }
    }

    private fun shareCrashLog(context: Context, uri: Uri) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Crash Log"))
        } catch (e: Exception) {
            Log.e("MainActivity", "Error sharing crash log: ", e)
        }
    }

    @Composable
    fun CrashLogDialog(crashLog: String, onDismiss: () -> Unit, onShare: () -> Unit) {
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Last Crash Log") },
            text = {
                Column {
                    Text(
                        text = crashLog,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onShare) {
                    Text("Share")
                }
            },
            neutralButton = {
                val clipboardManager = LocalClipboardManager.current
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(crashLog))
                    Toast.makeText(context, "Crash log copied to clipboard", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Copy")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        )
    }
            GitHubManagerTheme {
                AppNavigator()
            }
        }
    }
}
