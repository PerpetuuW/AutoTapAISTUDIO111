package com.example.autotap.core.logger

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val TAG = "AutoTap"
    private const val MAX_LOG_SIZE = 1024 * 1024 // 1 MB
    private val logLock = Any()
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    var appContext: Context? = null
    
    private var isCrashHandlerInstalled = false

    fun init(context: Context) {
        appContext = context.applicationContext
        
        if (!isCrashHandlerInstalled) {
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                logError(appContext, "FATAL_CRASH", throwable)
                defaultHandler?.uncaughtException(thread, throwable)
            }
            isCrashHandlerInstalled = true
        }
    }

    private fun writeWithRotation(targetCtx: Context, content: String) {
        synchronized(logLock) {
            try {
                val logFile = File(targetCtx.filesDir, "error_log.txt")
                if (logFile.exists() && logFile.length() > MAX_LOG_SIZE) {
                    val tail = logFile.readText(Charsets.UTF_8).takeLast(512 * 1024)
                    logFile.writeText("...[АВТО-РОТАЦИЯ ЛОГОВ]...\n$tail", Charsets.UTF_8)
                }
                logFile.appendText(content, Charsets.UTF_8)
            } catch (_: Exception) {}
        }
    }

    fun log(context: Context?, category: String, message: String) {
        val timeStr = sdf.format(Date())
        val formatted = "[$timeStr][$category] $message"
        Log.d(TAG, formatted)

        val targetCtx = context ?: appContext ?: return
        writeWithRotation(targetCtx, formatted + "\n")
    }

    fun logError(context: Context?, category: String, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stackTrace = sw.toString()
        Log.e(TAG, "[$category] EXCEPTION: $stackTrace", throwable)

        val targetCtx = context ?: appContext ?: return
        val timeStr = sdf.format(Date())
        val entry = "=== [$timeStr][$category] FATAL EXCEPTION ===\n$stackTrace\n========================================\n"
        writeWithRotation(targetCtx, entry)
    }

    fun clearLogs(context: Context? = null) {
        val targetCtx = context ?: appContext ?: return
        synchronized(logLock) {
            try {
                val logFile = File(targetCtx.filesDir, "error_log.txt")
                if (logFile.exists()) {
                    logFile.writeText("", Charsets.UTF_8)
                }
            } catch (_: Exception) {}
        }
    }

        fun getLogs(context: Context? = null): String {
        val targetCtx = context ?: appContext ?: return "Журнал логов пуст."
        synchronized(logLock) {
            val f1 = File(targetCtx.filesDir, "error_log.txt")
            val f2 = File(targetCtx.filesDir, "app_session.log")
            return when {
                f1.exists() && f1.length() > 0 -> f1.readText(Charsets.UTF_8).takeLast(24000)
                f2.exists() && f2.length() > 0 -> f2.readText(Charsets.UTF_8).takeLast(24000)
                else -> "Журнал логов пуст."
            }
        }
    }

    fun shareLogs(context: Context, customText: String? = null) {
        val content = customText ?: getLogs(context)
        try {
            val cacheLog = File(context.cacheDir, "autotap_debug_log.txt")
            cacheLog.writeText(content, Charsets.UTF_8)

            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", cacheLog
            )

            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_SUBJECT, "AutoTap Debug Logs")
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                clipData = android.content.ClipData.newUri(context.contentResolver, "autotap_debug_log.txt", uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = android.content.Intent.createChooser(shareIntent, "Отправить журнал логов").apply {
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is android.app.Activity) {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            // Выдача прав чтения всем приложениям, способным принять текстовый файл
            val resolvedActivities = context.packageManager.queryIntentActivities(shareIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            for (resInfo in resolvedActivities) {
                val pkg = resInfo.activityInfo.packageName
                context.grantUriPermission(pkg, uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            logError(context, "SHARE_LOGS_CRASH", e)
            android.widget.Toast.makeText(context, "Ошибка отправки: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
