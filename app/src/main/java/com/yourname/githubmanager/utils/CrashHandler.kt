package com.yourname.githubmanager.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashHandler : Thread.UncaughtExceptionHandler {

    private const val TAG = "CrashHandler"
    private lateinit var applicationContext: Context
    private var defaultExceptionHandler: Thread.UncaughtExceptionHandler? = null

    fun init(context: Context) {
        applicationContext = context.applicationContext
        defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        val crashFileName = "crash_log_" + timestamp + ".txt"
        val crashReport = getStackTrace(throwable)

        saveCrashLogToDownloads(crashFileName, crashReport)

        // Call the original exception handler to let the app crash normally
        defaultExceptionHandler?.uncaughtException(thread, throwable)
    }

    private fun getStackTrace(throwable: Throwable): String {
        val sb = StringBuilder()
        sb.append("Crash Report\n")
        sb.append("Timestamp: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())).append("\n")
        sb.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n")
        sb.append("Android Version: ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
        sb.append("\n")
        sb.append(Log.getStackTraceString(throwable))
        return sb.toString()
    }

    private fun saveCrashLogToDownloads(fileName: String, content: String) {
        try {
            val resolver = applicationContext.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            uri?.let {
                resolver.openOutputStream(it)?.use {
                    OutputStreamWriter(it).use {
                        writer -> writer.write(content)
                    }
                }
                Log.i(TAG, "Crash log saved to Downloads: $fileName")
            } ?: run {
                Log.e(TAG, "Failed to create new MediaStore entry for crash log.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving crash log to Downloads: ", e)
        }
    }
}

