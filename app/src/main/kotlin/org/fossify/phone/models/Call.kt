package org.fossify.phone.models

import android.telecom.Call
import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual
import org.fossify.phone.extensions.formatTimestampIso8601
import org.fossify.phone.extensions.concatenateCallValues
import org.fossify.phone.extensions.CALL_DATA_SEPARATOR

/**
 * Enhanced call tracking information for ODK sessions.
 * Provides additional metadata beyond standard Call object tracking.
 */
@Serializable
data class OdkCallTrackingInfo(
    @Contextual val call: Call,
    val startTime: Long,                    // Dial time timestamp
    val connectTime: Long?,                 // Connection time timestamp (null for failed calls)
    val number: String,                     // Phone number (E.164 format)
    val direction: CallDirection,           // Call direction
    val isActive: Boolean = true            // Currently active call
)

/**
 * Represents individual call attempts made during ODK session.
 * Includes all metadata needed for value concatenation and timestamping.
 */
@Serializable
data class OdkCallRecord(
    val callId: String,                     // Unique call identifier
    val direction: CallDirection,           // INCOMING or OUTGOING
    val phoneNumber: String,                // E.164 formatted phone number
    val duration: Double,                   // Call duration in seconds
    val startTime: Long,                    // Call start timestamp (dial time)
    val connectTime: Long?,                 // Call connection timestamp (null for failed calls)
    val endTime: Long,                      // Call end timestamp
    val wasSuccessful: Boolean,             // True if call reached active state
    val failureReason: String?,             // Null for successful calls
    val formattedTimestamp: String          // ISO 8601 formatted timestamp for display
) {
    constructor(
        callId: String,
        direction: CallDirection,
        phoneNumber: String,
        duration: Double,
        startTime: Long,
        connectTime: Long?,
        endTime: Long,
        wasSuccessful: Boolean,
        failureReason: String?
    ) : this(
        callId = callId,
        direction = direction,
        phoneNumber = phoneNumber,
        duration = duration,
        startTime = startTime,
        connectTime = connectTime,
        endTime = endTime,
        wasSuccessful = wasSuccessful,
        failureReason = failureReason,
        formattedTimestamp = formatTimestampIso8601(startTime)
    )
}

/**
 * Represents an active integration session with ODK Collect.
 * Contains session state, existing values, and call tracking data.
 */
@Serializable
data class ODKSession(
    val isActive: Boolean,
    val phoneNumber: String?,               // Phone number from ODK intent
    val existingValue: String?,             // Value from previous ODK session
    val sessionStartTime: Long,             // When session started
    val callRecords: List<OdkCallRecord>,   // All calls made during session
    val activeCalls: Map<String, OdkCallTrackingInfo>, // Currently active calls
    val sessionVariant: String?             // Build variant (core, foss, or gplay)
) {

    /**
     * Total duration of all calls in this session
     */
    val totalDuration: Double
        get() = callRecords.sumOf { it.duration }

    /**
     * Number of successful calls in this session
     */
    val successfulCallCount: Int
        get() = callRecords.count { it.wasSuccessful }

    /**
     * Number of failed calls in this session
     */
    val failedCallCount: Int
        get() = callRecords.count { !it.wasSuccessful }

    /**
     * Whether this session has any call records
     */
    val hasCalls: Boolean
        get() = callRecords.isNotEmpty()

    /**
     * Whether this session is active and has active calls
     */
    val hasActiveCalls: Boolean
        get() = isActive && activeCalls.isNotEmpty()

    /**
     * Create a new session with the specified phone number and existing value
     */
    fun startNewSession(phoneNumber: String?, existingValue: String?, variant: String?): ODKSession {
        return copy(
            isActive = true,
            phoneNumber = phoneNumber,
            existingValue = existingValue,
            sessionStartTime = System.currentTimeMillis(),
            callRecords = emptyList<OdkCallRecord>(),
            activeCalls = emptyMap<String, OdkCallTrackingInfo>(),
            sessionVariant = variant
        )
    }

    /**
     * End the current session and return the completed session
     */
    fun endSession(): ODKSession {
        return copy(isActive = false, activeCalls = emptyMap<String, OdkCallTrackingInfo>())
    }

    /**
     * Add a call record to this session
     */
    fun addCallRecord(record: OdkCallRecord): ODKSession {
        return copy(callRecords = callRecords + record)
    }

    /**
     * Add an active call to this session
     */
    fun addActiveCall(callId: String, trackingInfo: OdkCallTrackingInfo): ODKSession {
        return copy(activeCalls = activeCalls + (callId to trackingInfo))
    }

    /**
     * Remove an active call from this session
     */
    fun removeActiveCall(callId: String): ODKSession {
        return copy(activeCalls = activeCalls - callId)
    }

    /**
     * Update an active call in this session
     */
    fun updateActiveCall(callId: String, trackingInfo: OdkCallTrackingInfo): ODKSession {
        return copy(activeCalls = activeCalls + (callId to trackingInfo))
    }

    /**
     * Mark this session as timed out
     */
    fun timeoutSession(): ODKSession {
        return copy(isActive = false, activeCalls = emptyMap<String, OdkCallTrackingInfo>())
    }
}

// Extension functions for ODKSession
fun ODKSession.endSession(): ODKSession = copy(isActive = false, activeCalls = emptyMap<String, OdkCallTrackingInfo>())
fun ODKSession.addActiveCall(callId: String, trackingInfo: OdkCallTrackingInfo): ODKSession = copy(activeCalls = activeCalls + (callId to trackingInfo))
fun ODKSession.updateActiveCall(callId: String, trackingInfo: OdkCallTrackingInfo): ODKSession = copy(activeCalls = activeCalls + (callId to trackingInfo))
fun ODKSession.removeActiveCall(callId: String): ODKSession = copy(activeCalls = activeCalls - callId)
fun ODKSession.addCallRecord(record: OdkCallRecord): ODKSession = copy(callRecords = callRecords + record)

/**
 * Manages the lifecycle and state of ODK integration sessions.
 */
@Serializable
data class SessionState(
    val status: SessionStatus,              // Current session status
    val variant: String?,                   // Build variant (core/foss/gplay)
    val startTime: Long,                    // Session start time
    val lastUpdateTime: Long,               // Last state update
    val callCount: Int,                     // Number of calls in session
    val hasExistingValue: Boolean,          // True if session started with existing value
    val pendingCalls: Int                   // Calls being tracked
) {

    /**
     * Whether this session is currently active
     */
    val isActive: Boolean
        get() = status == SessionStatus.ACTIVE

    /**
     * Whether this session is completed
     */
    val isCompleted: Boolean
        get() = status == SessionStatus.COMPLETED

    /**
     * Whether this session has encountered an error
     */
    val isError: Boolean
        get() = status == SessionStatus.ERROR

    /**
     * Whether this session has timed out
     */
    val isTimeout: Boolean
        get() = status == SessionStatus.TIMEOUT

    /**
     * Create a new active session state
     */
    fun createActiveSession(variant: String?, hasExistingValue: Boolean): SessionState {
        return copy(
            status = SessionStatus.ACTIVE,
            variant = variant,
            startTime = System.currentTimeMillis(),
            lastUpdateTime = System.currentTimeMillis(),
            hasExistingValue = hasExistingValue,
            pendingCalls = 0
        )
    }

    /**
     * Update the session to completed status
     */
    fun completeSession(): SessionState {
        return copy(
            status = SessionStatus.COMPLETED,
            lastUpdateTime = System.currentTimeMillis(),
            pendingCalls = 0
        )
    }

    /**
     * Update the session to error status
     */
    fun errorSession(): SessionState {
        return copy(
            status = SessionStatus.ERROR,
            lastUpdateTime = System.currentTimeMillis(),
            pendingCalls = 0
        )
    }

    /**
     * Update the session to timeout status
     */
    fun timeoutSession(): SessionState {
        return copy(
            status = SessionStatus.TIMEOUT,
            lastUpdateTime = System.currentTimeMillis(),
            pendingCalls = 0
        )
    }

    /**
     * Update call count and pending calls
     */
    fun updateCallCounts(callCount: Int, pendingCalls: Int): SessionState {
        return copy(
            callCount = callCount,
            pendingCalls = pendingCalls,
            lastUpdateTime = System.currentTimeMillis()
        )
    }
}

/**
 * Represents the final formatted string returned to ODK Collect.
 * Combines existing values and new call records separated by pipes.
 */
@Serializable
data class ConcatenatedValue(
    val existingValue: String,              // Previous ODK value (may be empty)
    val newCallData: String,                // Newly formatted call data (may be empty)
    val finalValue: String,                  // Final concatenated result
    val isEmpty: Boolean,                   // True if both components are empty
    val separator: String = " | "           // Pipe separator with spaces
) {
    constructor(
        existingValue: String? = null,
        newCallData: String? = null,
        separator: String = " | "
    ) : this(
        existingValue = existingValue ?: "",
        newCallData = newCallData ?: "",
        finalValue = concatenateCallValues(existingValue, newCallData),
        isEmpty = (existingValue?.isBlank() != false) && (newCallData?.isBlank() != false),
        separator = separator
    )

    /**
     * Whether this concatenated value contains any data
     */
    val hasData: Boolean
        get() = !isEmpty

    /**
     * Whether this value contains only existing data (no new calls)
     */
    val hasExistingOnly: Boolean
        get() = existingValue.isNotBlank() && newCallData.isBlank()

    /**
     * Whether this value contains only new call data (no existing value)
     */
    val hasNewOnly: Boolean
        get() = existingValue.isBlank() && newCallData.isNotBlank()

    /**
     * Whether this value contains both existing and new data
     */
    val hasBoth: Boolean
        get() = existingValue.isNotBlank() && newCallData.isNotBlank()

    /**
     * Get the number of call records in the new call data
     * (This is a simple implementation - a more sophisticated version would parse the data)
     */
    val callRecordCount: Int
        get() = if (newCallData.isBlank()) 0 else newCallData.split(CALL_DATA_SEPARATOR).toList().size

    /**
     * Create a concatenated value with updated existing value
     */
    fun withExistingValue(existingValue: String?): ConcatenatedValue {
        return copy(
            existingValue = existingValue ?: "",
            finalValue = concatenateCallValues(existingValue, newCallData),
            isEmpty = (existingValue?.isBlank() != false) && (newCallData?.isBlank() != false)
        )
    }

    /**
     * Create a concatenated value with updated new call data
     */
    fun withNewCallData(newCallData: String?): ConcatenatedValue {
        return copy(
            newCallData = newCallData ?: "",
            finalValue = concatenateCallValues(existingValue, newCallData),
            isEmpty = (existingValue?.isBlank() != false) && (newCallData?.isBlank() != false)
        )
    }

    /**
     * Create a concatenated value with updated separator
     */
    fun withSeparator(separator: String): ConcatenatedValue {
        return copy(
            separator = separator,
            finalValue = concatenateCallValues(existingValue, newCallData),
            isEmpty = (existingValue?.isBlank() != false) && (newCallData?.isBlank() != false)
        )
    }
}

/**
 * Call direction enumeration for ODK call tracking.
 */
enum class CallDirection {
    INCOMING,
    OUTGOING
}

/**
 * Session status enumeration for ODK integration sessions.
 */
enum class SessionStatus {
    INACTIVE,      // No active ODK session
    ACTIVE,        // Session in progress, tracking calls
    COMPLETED,     // Session finished, data returned to ODK
    ERROR,         // Session encountered unrecoverable error
    TIMEOUT        // Session timed out
}