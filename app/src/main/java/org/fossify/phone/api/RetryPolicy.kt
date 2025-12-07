package org.fossify.phone.api

import android.util.Log
import okhttp3.Response
import java.io.IOException

/**
 * Policy for determining when and how to retry API calls
 */
class RetryPolicy(
    private val config: RetryConfig = RetryConfig.DEFAULT
) {
    companion object {
        private const val TAG = "RetryPolicy"
    }

    /**
     * Determines if a response should be retried based on status code
     */
    fun shouldRetryResponse(response: Response): Boolean {
        val statusCode = response.code
        val shouldRetry = config.shouldRetryOnStatusCode(statusCode)

        Log.d(TAG, "Response code $statusCode - should retry: $shouldRetry")

        return shouldRetry
    }

    /**
     * Determines if an exception should be retried
     */
    fun shouldRetryException(exception: Throwable): Boolean {
        val exceptionClassName = exception.javaClass.name
        val shouldRetry = config.shouldRetryOnException(exceptionClassName)

        Log.d(TAG, "Exception $exceptionClassName - should retry: $shouldRetry")

        return shouldRetry
    }

    /**
     * Determines if a response body should be preserved for retry
     */
    fun shouldPreserveResponseBody(response: Response): Boolean {
        // Only preserve successful responses or responses with retryable status codes
        return response.isSuccessful || config.shouldRetryOnStatusCode(response.code)
    }

    /**
     * Gets the delay for the next retry attempt
     */
    fun getRetryDelay(attemptNumber: Int): Long {
        val delay = config.calculateDelay(attemptNumber)
        Log.d(TAG, "Retry attempt $attemptNumber - delay: ${delay}ms")
        return delay
    }

    /**
     * Gets the maximum number of retry attempts
     */
    fun getMaxAttempts(): Int = config.maxAttempts

    /**
     * Gets the current configuration
     */
    fun getConfig(): RetryConfig = config

    /**
     * Creates a retry decision based on response
     */
    fun createRetryDecision(response: Response, attemptNumber: Int): RetryDecision {
        return if (shouldRetryResponse(response) && attemptNumber < config.maxAttempts) {
            val delay = getRetryDelay(attemptNumber)
            RetryDecision.Retry(delay)
        } else {
            RetryDecision.NoRetry
        }
    }

    /**
     * Creates a retry decision based on exception
     */
    fun createRetryDecision(exception: Throwable, attemptNumber: Int): RetryDecision {
        return if (shouldRetryException(exception) && attemptNumber < config.maxAttempts) {
            val delay = getRetryDelay(attemptNumber)
            RetryDecision.Retry(delay)
        } else {
            RetryDecision.NoRetry
        }
    }

    /**
     * Validates the retry policy configuration
     */
    fun validate(): Result<Unit> {
        return config.validate()
    }
}

/**
 * Decision for retry operation
 */
sealed class RetryDecision {
    object NoRetry : RetryDecision()
    data class Retry(val delayMs: Long) : RetryDecision()
}

/**
 * Retry context for tracking retry state
 */
data class RetryContext(
    val attemptNumber: Int,
    val totalAttempts: Int,
    val lastException: Throwable? = null,
    val lastResponse: Response? = null,
    val startTime: Long = System.currentTimeMillis()
) {
    /**
     * Checks if this is the final attempt
     */
    fun isFinalAttempt(): Boolean = attemptNumber >= totalAttempts

    /**
     * Gets elapsed time in milliseconds
     */
    fun getElapsedTimeMs(): Long = System.currentTimeMillis() - startTime

    /**
     * Creates next retry context
     */
    fun nextAttempt(): RetryContext {
        return copy(attemptNumber = attemptNumber + 1)
    }

    /**
     * Creates context with exception
     */
    fun withException(exception: Throwable): RetryContext {
        return copy(lastException = exception)
    }

    /**
     * Creates context with response
     */
    fun withResponse(response: Response): RetryContext {
        return copy(lastResponse = response)
    }

    /**
     * Formats retry information for logging
     */
    fun formatLogMessage(): String {
        return "RetryContext(attempt=$attemptNumber/$totalAttempts, elapsed=${getElapsedTimeMs()}ms" +
                if (lastException != null) ", lastException=${lastException.javaClass.simpleName}" else "" +
                if (lastResponse != null) ", lastResponse=${lastResponse.code}" else ""
    }
}

/**
 * Factory for creating retry contexts
 */
object RetryContextFactory {
    /**
     * Creates initial retry context
     */
    fun create(initialTotalAttempts: Int): RetryContext {
        return RetryContext(
            attemptNumber = 1,
            totalAttempts = initialTotalAttempts
        )
    }
}

/**
 * Extension functions for enhanced retry functionality
 */
fun Response.isRetryable(): Boolean {
    return this.code >= 500 || this.code == 429
}

fun Throwable.isRetryable(config: RetryConfig): Boolean {
    return config.shouldRetryOnException(this.javaClass.name)
}

/**
 * Logger for retry operations
 */
class RetryLogger {
    companion object {
        private const val TAG = "RetryLogger"

        fun logRetryAttempt(context: RetryContext, delay: Long) {
            Log.i(TAG, "Retry attempt ${context.attemptNumber}/${context.totalAttempts} - " +
                    "delay: ${delay}ms, ${context.formatLogMessage()}")
        }

        fun logRetryFailure(context: RetryContext, reason: String) {
            Log.w(TAG, "Retry failed after ${context.attemptNumber} attempts - " +
                    "reason: $reason, ${context.formatLogMessage()}")
        }

        fun logRetrySuccess(context: RetryContext) {
            Log.d(TAG, "Retry succeeded after ${context.attemptNumber} attempts, " +
                    "total time: ${context.getElapsedTimeMs()}ms")
        }

        fun logErrorResponse(response: Response) {
            Log.w(TAG, "Retryable response: ${response.code} ${response.message}")
            if (response.body != null) {
                try {
                    val responseBody = response.peekBody(1024).string()
                    Log.d(TAG, "Response body preview: $responseBody")
                } catch (e: Exception) {
                    Log.d(TAG, "Could not read response body")
                }
            }
        }

        fun logException(exception: Throwable, attempt: Int) {
            Log.w(TAG, "Retryable exception on attempt $attempt: ${exception.javaClass.simpleName}")
            if (exception.message != null) {
                Log.d(TAG, "Exception message: ${exception.message}")
            }
        }
    }
}