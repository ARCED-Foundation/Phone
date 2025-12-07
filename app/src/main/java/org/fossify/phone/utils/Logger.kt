package org.fossify.phone.utils

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.json.JSONObject

/**
 * Centralized logging framework for CATI Call Logger with ODK Central integration
 * Provides consistent logging for call detection and sync operations across the app
 *
 * Usage:
 * Logger.d("CallDetection", "Call started: ${call.phoneNumber}")
 * Logger.e("OdkSync", "Sync failed", exception)
 * Logger.i("OdkSync", "jsonPayload", payloadJson)
 */
object Logger {
    private const val MAX_LOG_TAG_LENGTH = 23
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    // Default tags for this feature
    const val TAG_CALL_DETECTION = "CallDetection"
    const val TAG_ODK_SYNC = "OdkSync"
    const val TAG_CALL_LOGGER = "CallLogger"
    const val TAG_ADMIN_SETUP = "AdminSetup"

    fun v(tag: String, message: String) {
        Log.v(sanitizeTag(tag), message)
    }

    fun d(tag: String, message: String) {
        Log.d(sanitizeTag(tag), message)
    }

    fun i(tag: String, message: String) {
        Log.i(sanitizeTag(tag), message)
    }

    fun w(tag: String, message: String) {
        Log.w(sanitizeTag(tag), message)
    }

    fun w(tag: String, throwable: Throwable) {
        Log.w(sanitizeTag(tag), throwable)
    }

    fun e(tag: String, message: String) {
        Log.e(sanitizeTag(tag), message)
    }

    fun e(tag: String, message: String, throwable: Throwable) {
        Log.e(sanitizeTag(tag), message, throwable)
    }

    /**
     * Logs JSON object in pretty-printed format (useful for sync payloads/responses)
     */
    fun json(tag: String, json: Any) {
        val jsonString = try {
            when (json) {
                is JSONObject -> json.toString(2)
                is String -> if (json.startsWith("{") || json.startsWith("[")) json else gson.toJson(json)
                else -> gson.toJson(json)
            }
        } catch (e: Exception) {
            "Failed to format JSON: ${e.message}"
        }
        d(tag, "JSON: $jsonString")
    }

    /**
     * Sanitizes tag to comply with Log tag length limits (max 23 chars)
     */
    private fun sanitizeTag(tag: String): String {
        return if (tag.length > MAX_LOG_TAG_LENGTH) {
            tag.take(MAX_LOG_TAG_LENGTH)
        } else {
            tag
        }
    }

    // Convenience methods for common tags
    fun callDetection(message: String, level: String = "d") = logWithTag(TAG_CALL_DETECTION, message, level)
    fun odkSync(message: String, level: String = "d") = logWithTag(TAG_ODK_SYNC, message, level)
    fun callLogger(message: String, level: String = "d") = logWithTag(TAG_CALL_LOGGER, message, level)

    private fun logWithTag(tag: String, message: String, level: String) {
        when (level.lowercase()) {
            "v" -> v(tag, message)
            "d" -> d(tag, message)
            "i" -> i(tag, message)
            "w" -> w(tag, message)
            "e" -> e(tag, message)
        }
    }
}

/**
 * Extension functions for easy structured logging
 */
fun String.callDetectionLog(level: String = "d") = Logger.callDetection(this, level)
fun String.odkSyncLog(level: String = "d") = Logger.odkSync(this, level)

fun Throwable.callDetectionError(message: String) {
    Logger.e(Logger.TAG_CALL_DETECTION, message, this)
}

fun Throwable.odkSyncError(message: String) {
    Logger.e(Logger.TAG_ODK_SYNC, message, this)
}