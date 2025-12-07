package org.fossify.phone.utils

import android.content.Context

data class CallDetectionFailureSummary(
    val failureCount: Int,
    val lastFailurePoint: String?,
    val lastFailureReason: String?,
    val lastFailureAt: Long
)

object CallDetectionAnalytics {
    private const val PREFS_NAME = "call_detection_analytics"
    private const val KEY_FAILURE_COUNT = "failure_count"
    private const val KEY_LAST_POINT = "last_failure_point"
    private const val KEY_LAST_REASON = "last_failure_reason"
    private const val KEY_LAST_AT = "last_failure_at"

    fun recordFailure(context: Context, point: String, reason: String?) {
        val prefs = getPrefs(context)
        val nextCount = prefs.getInt(KEY_FAILURE_COUNT, 0) + 1
        prefs.edit()
            .putInt(KEY_FAILURE_COUNT, nextCount)
            .putString(KEY_LAST_POINT, point)
            .putString(KEY_LAST_REASON, reason)
            .putLong(KEY_LAST_AT, System.currentTimeMillis())
            .apply()

        Logger.callDetection("Detection failure @ $point [${nextCount}]: ${reason ?: "unknown"}", "w")
    }

    fun getSummary(context: Context): CallDetectionFailureSummary {
        val prefs = getPrefs(context)
        return CallDetectionFailureSummary(
            failureCount = prefs.getInt(KEY_FAILURE_COUNT, 0),
            lastFailurePoint = prefs.getString(KEY_LAST_POINT, null),
            lastFailureReason = prefs.getString(KEY_LAST_REASON, null),
            lastFailureAt = prefs.getLong(KEY_LAST_AT, 0L)
        )
    }

    fun reset(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    private fun getPrefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
