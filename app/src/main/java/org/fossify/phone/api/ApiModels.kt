package org.fossify.phone.api

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.Date

/**
 * ODK Central API request and response data models
 */

@Serializable
data class CallLogProperties(
    val timestamp: Long,
    val phoneNumber: String,
    val contactName: String? = null,
    val callType: String, // "incoming", "outgoing", "missed"
    val duration: Long?, // Call duration in seconds
    val direction: String, // "in", "out"
    val status: String, // "completed", "missed", "voicemail"
    val notes: String? = null,
    val location: String? = null
)

@Serializable
data class CallLogEntityRequest(
    val properties: CallLogProperties
)

@Serializable
data class CallLogEntityRequestWrapper(
    val entity: CallLogEntityRequest
)

@Serializable
data class CallLogEntityResponse(
    val uuid: String,
    val properties: CallLogProperties
)

@Serializable
data class CallLogEntityResponseWrapper(
    val entity: CallLogEntityResponse
)

@Serializable
data class OdkCentralCredentials(
    val url: String,
    val username: String,
    val password: String,
    val projectId: Int,
    val validated: Boolean = false,
    val lastValidation: Long? = null
)

/**
 * Represents a call log that needs to be synced
 */
data class CallLogToSync(
    val id: Long,
    val timestamp: Long,
    val phoneNumber: String,
    val contactName: String?,
    val callType: String,
    val duration: Long?,
    val direction: String,
    val status: String,
    val notes: String?,
    val location: String?
) {
    /**
     * Converts to ODK Central API request format
     */
    fun toApiRequest(): CallLogEntityRequest {
        return CallLogEntityRequest(
            properties = CallLogProperties(
                timestamp = timestamp,
                phoneNumber = phoneNumber,
                contactName = contactName,
                callType = callType,
                duration = duration,
                direction = direction,
                status = status,
                notes = notes,
                location = location
            )
        )
    }
}

/**
 * API response wrapper for successful operations
 */
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    @Contextual val error: ApiError? = null,
    val message: String? = null
)

/**
 * API response wrapper for error operations
 */
@Serializable
data class ApiErrorResponse(
    val success: Boolean = false,
    @Contextual val error: ApiError,
    val message: String? = null
)