package com.yourname.githubmanager.data.filesystem

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * WorkManager CoroutineWorker that extracts a .zip file to the app's
 * internal or cache directory using a streaming, buffered approach.
 *
 * Input data keys:
 *  - KEY_ZIP_URI_PATH: absolute path (or content:// resolved path) of the source zip
 *  - KEY_DEST_DIR_PATH: absolute path of the destination directory
 *  - KEY_USE_CACHE_DIR: (optional) Boolean, defaults to false -> filesDir; true -> cacheDir
 *
 * Output/progress data keys:
 *  - KEY_PROGRESS_PERCENT: Int 0-100
 *  - KEY_CURRENT_ENTRY_NAME: String, name of entry currently being extracted
 *  - KEY_ERROR_TYPE: String, one of ERROR_PERMISSION_DENIED, ERROR_IO, ERROR_INVALID_ZIP
 *  - KEY_ERROR_MESSAGE: String, human-readable message for Snackbar display
 *  - KEY_EXTRACTED_PATH: String, final extraction directory path on success
 */
class ZipExtractor(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "ZipExtractor"

        const val KEY_ZIP_URI_PATH = "key_zip_uri_path"
        const val KEY_DEST_DIR_PATH = "key_dest_dir_path"
        const val KEY_USE_CACHE_DIR = "key_use_cache_dir"

        const val KEY_PROGRESS_PERCENT = "key_progress_percent"
        const val KEY_CURRENT_ENTRY_NAME = "key_current_entry_name"

        const val KEY_ERROR_TYPE = "key_error_type"
        const val KEY_ERROR_MESSAGE = "key_error_message"
        const val KEY_EXTRACTED_PATH = "key_extracted_path"

        const val ERROR_PERMISSION_DENIED = "ERROR_PERMISSION_DENIED"
        const val ERROR_IO = "ERROR_IO"
        const val ERROR_INVALID_ZIP = "ERROR_INVALID_ZIP"

        // 8 KB chunks keep memory flat regardless of entry size, preventing
        // OOM on large archives even under Android 15 memory constraints.
        private const val BUFFER_SIZE = 8 * 1024
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val zipPath = inputData.getString(KEY_ZIP_URI_PATH)
        val destDirPath = inputData.getString(KEY_DEST_DIR_PATH)
        val useCacheDir = inputData.getBoolean(KEY_USE_CACHE_DIR, false)

        if (zipPath.isNullOrBlank()) {
            return@withContext failureWith(
                ERROR_INVALID_ZIP,
                "No zip file path provided."
            )
        }

        val sourceZip = File(zipPath)
        if (!sourceZip.exists() || !sourceZip.isFile) {
            return@withContext failureWith(
                ERROR_INVALID_ZIP,
                "Zip file not found: $zipPath"
            )
        }

        val baseDir = when {
            !destDirPath.isNullOrBlank() -> File(destDirPath)
            useCacheDir -> applicationContext.cacheDir
            else -> applicationContext.filesDir
        }

        try {
            if (!baseDir.exists() && !baseDir.mkdirs()) {
                return@withContext failureWith(
                    ERROR_PERMISSION_DENIED,
                    "Unable to create destination directory. Storage permission may be denied."
                )
            }
        } catch (se: SecurityException) {
            Log.w(TAG, "Permission denied creating destination dir", se)
            return@withContext failureWith(
                ERROR_PERMISSION_DENIED,
                "Permission denied: cannot write to destination folder."
            )
        }

        return@withContext extractZip(sourceZip, baseDir)
    }

    private suspend fun extractZip(sourceZip: File, destDir: File): Result {
        // First pass: count entries so progress percentage is meaningful.
        val totalEntries = try {
            countEntries(sourceZip)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read zip for entry count", e)
            return failureWith(ERROR_INVALID_ZIP, "The file is not a valid zip archive.")
        }

        if (totalEntries == 0) {
            return failureWith(ERROR_INVALID_ZIP, "Zip archive is empty or unreadable.")
        }

        var processedEntries = 0

        try {
            BufferedInputStream(sourceZip.inputStream()).use { bufferedIn ->
                ZipInputStream(bufferedIn).use { zipIn ->
                    var entry: ZipEntry? = zipIn.nextEntry

                    while (entry != null) {
                        if (isStopped) {
                            zipIn.closeEntry()
                            return Result.failure(
                                workDataOf(
                                    KEY_ERROR_TYPE to ERROR_IO,
                                    KEY_ERROR_MESSAGE to "Extraction cancelled."
                                )
                            )
                        }

                        val safeEntry = entry
                        val targetFile = resolveSafeTargetFile(destDir, safeEntry.name)
                            ?: run {
                                // Zip Slip protection: skip entries that try to escape destDir
                                zipIn.closeEntry()
                                entry = zipIn.nextEntry
                                return@run null
                            }

                        if (targetFile != null) {
                            if (safeEntry.isDirectory) {
                                ensureDirectory(targetFile)
                            } else {
                                targetFile.parentFile?.let { ensureDirectory(it) }
                                writeEntryToDisk(zipIn, targetFile)
                            }
                        }

                        zipIn.closeEntry()
                        processedEntries++

                        val percent = ((processedEntries * 100f) / totalEntries).toInt()
                            .coerceIn(0, 100)

                        setProgress(
                            workDataOf(
                                KEY_PROGRESS_PERCENT to percent,
                                KEY_CURRENT_ENTRY_NAME to safeEntry.name
                            )
                        )

                        entry = zipIn.nextEntry
                    }
                }
            }
        } catch (se: SecurityException) {
            Log.w(TAG, "Permission denied during extraction", se)
            return failureWith(
                ERROR_PERMISSION_DENIED,
                "Permission denied while writing extracted files."
            )
        } catch (ioe: IOException) {
            Log.e(TAG, "IO error during extraction", ioe)
            return failureWith(
                ERROR_IO,
                "Extraction failed: ${ioe.localizedMessage ?: "unknown IO error"}"
            )
        }

        return Result.success(
            workDataOf(
                KEY_PROGRESS_PERCENT to 100,
                KEY_EXTRACTED_PATH to destDir.absolutePath
            )
        )
    }

    /** Streams a single zip entry to disk in fixed-size chunks, never loading it fully into memory. */
    private fun writeEntryToDisk(zipIn: ZipInputStream, targetFile: File) {
        val buffer = ByteArray(BUFFER_SIZE)
        BufferedOutputStream(FileOutputStream(targetFile), BUFFER_SIZE).use { out ->
            var bytesRead: Int
            while (true) {
                bytesRead = zipIn.read(buffer)
                if (bytesRead == -1) break
                out.write(buffer, 0, bytesRead)
            }
            out.flush()
        }
    }

    private fun ensureDirectory(dir: File) {
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("Failed to create directory: ${dir.absolutePath}")
        }
    }

    /**
     * Resolves the target file path while guarding against Zip Slip
     * (entries with "../" that try to write outside destDir).
     * Returns null if the entry is unsafe and should be skipped.
     */
    private fun resolveSafeTargetFile(destDir: File, entryName: String): File? {
        val targetFile = File(destDir, entryName)
        val destCanonical = destDir.canonicalPath
        val targetCanonical = targetFile.canonicalPath
        return if (targetCanonical.startsWith(destCanonical + File.separator) ||
            targetCanonical == destCanonical
        ) {
            targetFile
        } else {
            Log.w(TAG, "Skipping unsafe zip entry (path traversal): $entryName")
            null
        }
    }

    private fun countEntries(sourceZip: File): Int {
        var count = 0
        BufferedInputStream(sourceZip.inputStream()).use { bufferedIn ->
            ZipInputStream(bufferedIn).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    count++
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
        }
        return count
    }

    private fun failureWith(errorType: String, message: String): Result {
        Log.e(TAG, "$errorType: $message")
        return Result.failure(
            workDataOf(
                KEY_ERROR_TYPE to errorType,
                KEY_ERROR_MESSAGE to message
            )
        )
    }
}
