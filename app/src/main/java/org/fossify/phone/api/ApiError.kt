package org.fossify.phone.api

import java.net.HttpURLConnection

/**
 * Error handling for ODK Central API operations
 */

sealed class ApiError {
    data class NetworkError(val message: String, val cause: Throwable? = null) : ApiError()
    data class AuthenticationError(val message: String = "Authentication failed") : ApiError()
    data class AuthorizationError(val message: String = "Access denied") : ApiError()
    data class ValidationError(val field: String, val message: String) : ApiError()
    data class ServerError(val code: Int, val message: String) : ApiError()
    data class UnknownError(val message: String, val cause: Throwable? = null) : ApiError()
}

/**
 * HTTP status code categorization
 */
enum class ErrorCategory {
    NETWORK,      // Connection issues, timeouts
    AUTH,         // 401 Unauthorized
    FORBIDDEN,    // 403 Forbidden
    VALIDATION,   // 400 Bad Request, 422 Unprocessable Entity
    SERVER,       // 500+ server errors
    RETRYABLE,    // Errors that can be retried (5xx, 429, 503)
    FATAL         // Errors that should not be retried (4xx except validation)
}

// HTTP constants
object HttpConstants {
    const val HTTP_OK = 200
    const val HTTP_CREATED = 201
    const val HTTP_BAD_REQUEST = 400
    const val HTTP_UNAUTHORIZED = 401
    const val HTTP_FORBIDDEN = 403
    const val HTTP_NOT_FOUND = 404
    const val HTTP_TOO_MANY_REQUESTS = 429
    const val HTTP_UNPROCESSABLE_ENTITY = 422
    const val HTTP_INTERNAL_SERVER_ERROR = 500
    const val HTTP_BAD_GATEWAY = 502
    const val HTTP_SERVICE_UNAVAILABLE = 503
    const val HTTP_GATEWAY_TIMEOUT = 504
}

/**
 * Determines the error category from HTTP status code
 */
fun getErrorCategory(statusCode: Int): ErrorCategory {
    return when (statusCode) {
        HttpConstants.HTTP_UNAUTHORIZED -> ErrorCategory.AUTH
        HttpConstants.HTTP_FORBIDDEN -> ErrorCategory.FORBIDDEN
        HttpConstants.HTTP_BAD_REQUEST -> ErrorCategory.VALIDATION
        HttpConstants.HTTP_UNPROCESSABLE_ENTITY -> ErrorCategory.VALIDATION
        HttpConstants.HTTP_TOO_MANY_REQUESTS -> ErrorCategory.RETRYABLE
        in 500..599 -> ErrorCategory.SERVER
        in 400..499 -> ErrorCategory.FATAL
        else -> ErrorCategory.NETWORK
    }
}

/**
 * Checks if an error is retryable
 */
fun isRetryableError(error: ApiError): Boolean {
    return when (error) {
        is ApiError.NetworkError -> true
        is ApiError.ServerError -> error.code in 500..599 || error.code == 429 || error.code == 503
        is ApiError.UnknownError -> true
        else -> false
    }
}

/**
 * Converts HTTP status code to ApiError
 */
fun statusCodeToApiError(statusCode: Int, responseBody: String? = null): ApiError {
    val message = responseBody ?: "HTTP $statusCode error"

    return when (statusCode) {
        HttpConstants.HTTP_UNAUTHORIZED -> ApiError.AuthenticationError(message)
        HttpConstants.HTTP_FORBIDDEN -> ApiError.AuthorizationError(message)
        HttpConstants.HTTP_BAD_REQUEST -> ApiError.ServerError(statusCode, message)
        HttpConstants.HTTP_UNPROCESSABLE_ENTITY -> ApiError.ServerError(statusCode, message)
        in 500..599 -> ApiError.ServerError(statusCode, message)
        else -> ApiError.UnknownError("HTTP $statusCode: $message")
    }
}

/**
 * Exception thrown for API operation failures
 */
class ApiException(
    val error: ApiError,
    val statusCode: Int? = null,
    val requestUrl: String? = null
) : Exception(error.toString()) {
    companion object {
        fun fromResponseCode(statusCode: Int, message: String? = null, url: String? = null): ApiException {
            return ApiException(
                error = statusCodeToApiError(statusCode, message),
                statusCode = statusCode,
                requestUrl = url
            )
        }
    }
}

/**
 * Exception thrown for network operation failures
 */
class NetworkException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)