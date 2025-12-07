package org.fossify.phone.utils

import android.content.Context
import android.content.SharedPreferences

data class CrashInfo(
    val kind: String?,
    val operation: String?,
    val message: String?,
    val timestamp: Long
)

object CrashTracker {
    private const val PREFS_NAME = "crash_tracker"
    private const val KEY_LAST_KIND = "last_crash_kind"
    private const val KEY_LAST_OPERATION = "last_crash_operation"
    private const val KEY_LAST_MESSAGE = "last_crash_message"
    private const val KEY_LAST_TIMESTAMP = "last_crash_timestamp"

    private lateinit var appContext: Context
    private var initialized = false
    private var originalHandler: Thread.UncaughtExceptionHandler? = null

    fun initialize(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            recordUncaught(thread, throwable)
            originalHandler?.uncaughtException(thread, throwable)
        }
        initialized = true
    }

    fun recordDatabaseException(operation: String, throwable: Throwable) {
        record("database", operation, throwable)
    }

    fun recordSyncException(operation: String, throwable: Throwable) {
        record("sync", operation, throwable)
    }

    fun getLastCrashInfo(): CrashInfo {
        val prefs = getPrefs()
        return CrashInfo(
            kind = prefs.getString(KEY_LAST_KIND, null),
            operation = prefs.getString(KEY_LAST_OPERATION, null),
            message = prefs.getString(KEY_LAST_MESSAGE, null),
            timestamp = prefs.getLong(KEY_LAST_TIMESTAMP, 0L)
        )
    }

    private fun record(kind: String, operation: String, throwable: Throwable) {
        Logger.e("CrashTracker", "[$kind] $operation", throwable)
        saveMetadata(kind, operation, throwable.message)
    }

    private fun recordUncaught(thread: Thread, throwable: Throwable) {
        Logger.e("CrashTracker", "Uncaught exception on ${thread.name}", throwable)
        saveMetadata("uncaught", "thread:${thread.name}", throwable.message)
    }

    private fun saveMetadata(kind: String, operation: String, message: String?) {
        val prefs = getPrefs()
        prefs.edit()
            .putString(KEY_LAST_KIND, kind)
            .putString(KEY_LAST_OPERATION, operation)
            .putString(KEY_LAST_MESSAGE, message)
            .putLong(KEY_LAST_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    private fun getPrefs(): SharedPreferences {
        ensureInitialized()
        return appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun ensureInitialized() {
        check(initialized) { "CrashTracker must be initialized before use" }
    }
}
