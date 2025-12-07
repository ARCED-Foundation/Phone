package org.fossify.phone.network

import okhttp3.Interceptor
import okhttp3.Response
import org.fossify.phone.api.ApiError
import org.fossify.phone.api.getErrorCategory
import org.fossify.phone.api.isRetryableError
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Policy definitions for determining when to retry requests
 */
class RetryPolicy(private val config: RetryConfig) {

    /**
     * Determines if a response should be retried
     *
     * @param response The HTTP response to evaluate
     * @return true if the response should be retried, false otherwise
     */
    fun shouldRetryResponse(response: Response): Boolean {
        if (response.code == 200 || response.code == 201) {
            return false // Success responses should not be retried
        }

        // Check if status code is in retryable codes
        if (config.retryableStatusCodes.contains(response.code)) {
            return true
        }

        // Check for specific server error categories
        val errorCategory = getErrorCategory(response.code)
        return when (errorCategory) {
            org.fossify.phone.api.ErrorCategory.SERVER -> true
            org.fossify.phone.api.ErrorCategory.RETRYABLE -> true
            else -> false
        }
    }

    /**
     * Determines if an exception should be retried
     *
     * @param exception The exception to evaluate
     * @return true if the exception should be retried, false otherwise
     */
    fun shouldRetryException(exception: Exception): Boolean {
        if (!config.retryOnNetworkErrors) {
            return false
        }

        return when (exception) {
            is SocketTimeoutException -> true // Connection/read timeout
            is ConnectException -> true // Connection refused
            is UnknownHostException -> true // DNS resolution failed
            is IOException -> true // General network IO error
            else -> false
        }
    }

    /**
     * Determines if an API error should be retried
     *
     * @param error The API error to evaluate
     * @return true if the error should be retried, false otherwise
     */
    fun shouldRetryApiError(error: ApiError): Boolean {
        if (error is ApiError.NetworkError && !config.retryOnNetworkErrors) {
            return false
        }

        if (error is ApiError.AuthenticationError && !config.retryOnAuthErrors) {
            return false
        }

        if (error is ApiError.AuthorizationError && !config.retryOnAuthErrors) {
            return false
        }

        return isRetryableError(error)
    }

    /**
     * Checks if the maximum number of attempts has been reached
     *
     * @param attempt The current attempt number (1-based)
     * @return true if max attempts reached, false otherwise
     */
    fun maxAttemptsReached(attempt: Int): Boolean {
        return attempt >= config.maxAttempts
    }

    /**
     * Calculates the delay for the next retry attempt
     *
     * @param attempt The current attempt number (0-based, so first retry is attempt 1)
     * @return The delay in milliseconds
     */
    fun calculateDelay(attempt: Int): Long {
        if (attempt < 0) return config.baseDelayMs

        // Calculate exponential backoff with linear growth when multiplier is 1.0
        val delayMs = when {
            config.multiplier == 1.0 -> {
                // Linear growth
                config.baseDelayMs * (attempt + 1L)
            }
            else -> {
                // Exponential growth
                (config.baseDelayMs * Math.pow(config.multiplier, attempt.toDouble())).toLong()
            }
        }

        // Apply maximum delay limit
        val cappedDelay = delayMs.coerceAtMost(config.maxDelayMs)

        // Add jitter if enabled
        return if (config.jitterEnabled) {
            addJitter(cappedDelay)
        } else {
            cappedDelay
        }
    }

    /**
     * Adds random jitter to delay to avoid thundering herd problems
     *
     * @param delay The base delay in milliseconds
     * @return The delay with jitter applied
     */
    private fun addJitter(delay: Long): Long {
        if (config.jitterFactor <= 0.0) return delay

        val jitterRange = (delay * config.jitterFactor).toLong()
        val jitter = (Math.random() * 2 * jitterRange - jitterRange).toLong()

        return (delay + jitter).coerceAtLeast(0L)
    }

    /**
     * Gets retry attempt information from response headers
     *
     * @param response The HTTP response
     * @return RetryAttemptInfo or null if no retry info present
     */
    fun getRetryAttemptInfo(response: Response): RetryAttemptInfo? {
        val attemptHeader = response.headers["X-Retry-Attempt"]
        val maxAttemptsHeader = response.headers["X-Retry-Max-Attempts"]
        val delayHeader = response.headers["X-Retry-Delay-MS"]

        return if (attemptHeader != null && maxAttemptsHeader != null) {
            try {
                RetryAttemptInfo(
                    attempt = attemptHeader.toInt(),
                    maxAttempts = maxAttemptsHeader.toInt(),
                    delayMs = delayHeader?.toLongOrNull()
                )
            } catch (e: NumberFormatException) {
                null
            }
        } else {
            null
        }
    }

    /**
     * Information about retry attempts extracted from response headers
     */
    data class RetryAttemptInfo(
        val attempt: Int,
        val maxAttempts: Int,
        val delayMs: Long? = null
    )
}

/**
 * Extension function to easily create a retry policy
 */
fun RetryConfig.toPolicy(): RetryPolicy {
    return RetryPolicy(this)
}