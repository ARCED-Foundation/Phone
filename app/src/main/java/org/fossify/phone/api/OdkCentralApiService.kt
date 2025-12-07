package org.fossify.phone.api

import org.fossify.phone.models.CallLogItem

/**
 * ODK Central API service interface
 * Provides all operations for interacting with the ODK Central API
 */
interface OdkCentralApiService {

    /**
     * Sends a call log to ODK Central
     *
     * @param callLog The call log to send
     * @return ApiResponse containing the created entity response
     * @throws ApiException on API errors
     */
    suspend fun sendCallLog(callLog: CallLogItem): ApiResponse<CallLogEntityResponseWrapper>

    /**
     * Validates the stored credentials against ODK Central
     *
     * @return true if credentials are valid, false otherwise
     */
    suspend fun validateCredentials(): Boolean

    /**
     * Tests the connection to ODK Central
     *
     * @return true if connection is successful, false otherwise
     */
    suspend fun testConnection(): Boolean

    /**
     * Gets the current project ID from credentials
     *
     * @return Project ID or null if not configured
     */
    fun getProjectId(): Int?

    /**
     * Checks if the service has valid credentials configured
     *
     * @return true if valid credentials exist, false otherwise
     */
    suspend fun hasValidCredentials(): Boolean
}

/**
 * Configuration for the ODK Central API service
 */
data class OdkCentralApiConfig(
    val baseUrl: String,
    val username: String,
    val password: String,
    val projectId: Long,
    val connectTimeout: Long = 30000, // 30 seconds
    val readTimeout: Long = 30000,    // 30 seconds
    val writeTimeout: Long = 30000,   // 30 seconds,
    val retryConfig: RetryConfig = RetryConfig.DEFAULT
)

/**
 * Interface for API operation callbacks
 */
interface ApiCallback<T> {
    fun onSuccess(result: T)
    fun onError(error: ApiError)
}

/**
 * High-level API operations with retry logic and error handling
 */
object OdkCentralHighLevelOperations {

    /**
     * Sends call logs with retry logic and exponential backoff
     *
     * @param callLog The call log to send
     * @param apiService The API service instance
     * @return ApiResponse containing the result
     */
    suspend fun sendCallLogWithRetry(
        callLog: CallLogItem,
        apiService: OdkCentralApiService
    ): ApiResponse<CallLogEntityResponseWrapper> {
        var lastError: ApiError? = null
        val maxRetries = 3

        repeat(maxRetries) { attempt ->
            try {
                val response = apiService.sendCallLog(callLog)
                if (response.success) {
                    return response
                } else {
                    lastError = response.error ?: ApiError.UnknownError("Unknown error")
                }
            } catch (e: ApiException) {
                lastError = e.error
            }

            // Wait before retry (exponential backoff)
            if (attempt < maxRetries - 1) {
                val delay = 1000L * (2L.pow(attempt.toLong()))
                kotlinx.coroutines.delay(delay)
            }
        }

        return ApiResponse(success = false, error = lastError ?: ApiError.UnknownError("Max retries exceeded"))
    }

    /**
     * Validates credentials with retry logic
     *
     * @param apiService The API service instance
     * @return true if validation succeeds, false otherwise
     */
    suspend fun validateCredentialsWithRetry(apiService: OdkCentralApiService): Boolean {
        return try {
            apiService.validateCredentials()
        } catch (e: ApiException) {
            false
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Extension function for powers
 */
private fun Long.pow(exponent: Long): Long {
    var result = 1L
    repeat(exponent.toInt()) {
        result *= this
    }
    return result
}
