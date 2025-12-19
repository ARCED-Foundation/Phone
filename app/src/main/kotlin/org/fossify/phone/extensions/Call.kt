package org.fossify.phone.extensions

import android.telecom.Call
import android.telecom.Call.STATE_CONNECTING
import android.telecom.Call.STATE_DIALING
import android.telecom.Call.STATE_SELECT_PHONE_ACCOUNT
import org.fossify.commons.helpers.isQPlus
import org.fossify.commons.helpers.isSPlus
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

// ODK Session Management Constants
const val ODK_SESSION_PREFS_NAME = "odk_session"
const val ODK_SESSION_STATE_KEY = "odk_session_state"
const val ODK_SESSION_CALL_RECORDS_KEY = "odk_session_call_records"
const val ODK_SESSION_START_TIME_KEY = "odk_session_start_time"
const val ODK_SESSION_EXISTING_VALUE_KEY = "odk_session_existing_value"
const val ODK_SESSION_PHONE_NUMBER_KEY = "odk_session_phone_number"
const val ODK_SESSION_IS_ACTIVE_KEY = "odk_session_is_active"
const val ODK_SESSION_VARIANT_KEY = "odk_session_variant"

// Session timeout constants (in milliseconds)
const val ODK_SESSION_TIMEOUT_MS = 600000L // 10 minutes in milliseconds
const val ODK_SESSION_CHECK_INTERVAL_MS = 30000L // 30 seconds in milliseconds

// Call tracking constants
const val CALL_RECORD_ID_PREFIX = "odk_call_"
const val TIMESTAMP_FORMAT_ISO_8601 = "yyyy-MM-dd'T'HH:mm:ss'Z'"
const val TIMESTAMP_FORMAT_READABLE = "yyyy-MM-dd HH:mm:ss"

// Value concatenation constants
const val CALL_DATA_SEPARATOR = " | "
const val CALL_FIELD_SEPARATOR = "; "
const val CALL_DIRECTION_OUTGOING = "Out"
const val CALL_DIRECTION_INCOMING = "In"
const val CALL_DATA_FORMAT = "$CALL_DIRECTION_OUTGOING: %s; Duration: %.2fs; Started: %s"
const val CALL_DATA_FORMAT_INCOMING = "$CALL_DIRECTION_INCOMING: %s; Duration: %.2fs; Started: %s"

private val OUTGOING_CALL_STATES = arrayOf(STATE_CONNECTING, STATE_DIALING, STATE_SELECT_PHONE_ACCOUNT)

@Suppress("DEPRECATION")
fun Call?.getStateCompat(): Int {
    return when {
        this == null -> Call.STATE_DISCONNECTED
        isSPlus() -> details.state
        else -> state
    }
}

fun Call?.getCallDuration(): Int {
    return if (this != null) {
        val connectTimeMillis = details.connectTimeMillis
        if (connectTimeMillis == 0L) {
            return 0
        }
        ((System.currentTimeMillis() - connectTimeMillis) / 1000).toInt()
    } else {
        0
    }
}

fun Call.isOutgoing(): Boolean {
    return if (isQPlus()) {
        details.callDirection == Call.Details.DIRECTION_OUTGOING
    } else {
        OUTGOING_CALL_STATES.contains(getStateCompat())
    }
}

fun Call.hasCapability(capability: Int): Boolean = (details.callCapabilities and capability) != 0

fun Call?.isConference(): Boolean = this?.details?.hasProperty(Call.Details.PROPERTY_CONFERENCE) == true

/**
 * Formats a timestamp to ISO 8601 format
 */
fun formatTimestampIso8601(timestamp: Long): String {
    val dateFormat = SimpleDateFormat(TIMESTAMP_FORMAT_ISO_8601, Locale.US)
    return dateFormat.format(timestamp)
}

/**
 * Formats a timestamp to human-readable format
 */
fun formatTimestampReadable(timestamp: Long): String {
    val dateFormat = SimpleDateFormat(TIMESTAMP_FORMAT_READABLE, Locale.getDefault())
    return dateFormat.format(timestamp)
}

/**
 * Formats current time to ISO 8601 format
 */
fun formatCurrentTimeIso8601(): String = formatTimestampIso8601(System.currentTimeMillis())

/**
 * Formats duration in seconds to human-readable format
 */
fun formatDuration(durationSeconds: Long): String {
    val hours = TimeUnit.SECONDS.toHours(durationSeconds)
    val minutes = TimeUnit.SECONDS.toMinutes(durationSeconds) % 60
    val seconds = durationSeconds % 60

    return when {
        hours > 0 -> String.format(Locale.US, "%dh %02dm %02ds", hours, minutes, seconds)
        minutes > 0 -> String.format(Locale.US, "%dm %02ds", minutes, seconds)
        else -> String.format(Locale.US, "%ds", seconds)
    }
}

/**
 * Formats duration in seconds with decimal precision
 */
fun formatDurationDecimal(durationSeconds: Double): String {
    return String.format(Locale.US, "%.2fs", durationSeconds)
}

/**
 * Formats call data for concatenation with existing values
 */
fun formatCallDataForConcatenation(
    phoneNumber: String,
    direction: String = CALL_DIRECTION_OUTGOING,
    duration: Double = 0.0,
    timestamp: Long = System.currentTimeMillis()
): String {
    val formattedTimestamp = formatTimestampIso8601(timestamp)
    return when (direction) {
        CALL_DIRECTION_INCOMING -> String.format(
            Locale.US,
            CALL_DATA_FORMAT_INCOMING,
            phoneNumber,
            formatDurationDecimal(duration),
            formattedTimestamp
        )
        else -> String.format(
            Locale.US,
            CALL_DATA_FORMAT,
            phoneNumber,
            formatDurationDecimal(duration),
            formattedTimestamp
        )
    }
}

/**
 * Concatenates existing value with new call data using pipe separator
 */
fun concatenateCallValues(existingValue: String?, newCallData: String?): String {
    val existing = existingValue?.takeIf { it.isNotBlank() }
    val new = newCallData?.takeIf { it.isNotBlank() }

    return when {
        existing == null && new == null -> ""
        existing != null && new != null -> "$existing$CALL_DATA_SEPARATOR$new"
        existing != null -> existing
        new != null -> new
        else -> ""
    }
}
