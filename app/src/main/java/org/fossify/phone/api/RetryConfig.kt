package org.fossify.phone.api

import java.util.concurrent.TimeUnit

/**
 * Configuration for retry behavior in API calls
 */
data class RetryConfig(
    val maxAttempts: Int = 7,
    val initialDelayMs: Long = 10_000, // 10 seconds
    val maxDelayMs: Long = 3_840_000, // 64 minutes
    val multiplier: Double = 2.0,
    val jitterEnabled: Boolean = true,
    val jitterFactor: Double = 0.1,
    val retryableStatusCodes: Set<Int> = setOf(
        429, // Too Many Requests
        500, // Internal Server Error
        502, // Bad Gateway
        503, // Service Unavailable
        504  // Gateway Timeout
    ),
    val retryableExceptions: Set<String> = setOf(
        "java.net.ConnectException",
        "java.net.SocketTimeoutException",
        "java.net.SocketException",
        "java.io.IOException"
    )
) {
    companion object {
        /**
         * Default configuration following LINEAR exponential backoff policy
         * 10s → 20s → 40s → 80s → 160s → 320s → 640s (64min max)
         */
        val DEFAULT = RetryConfig()

        /**
         * Conservative configuration for production use
         */
        val CONSERVATIVE = RetryConfig(
            maxAttempts = 5,
            initialDelayMs = 30_000, // 30 seconds
            maxDelayMs = 1_920_000, // 32 minutes
            jitterEnabled = true
        )

        /**
         * Aggressive configuration for testing
         */
        val AGGRESSIVE = RetryConfig(
            maxAttempts = 10,
            initialDelayMs = 1_000, // 1 second
            maxDelayMs = 300_000, // 5 minutes
            jitterEnabled = false
        )
    }

    /**
     * Checks if a status code should be retried
     */
    fun shouldRetryOnStatusCode(statusCode: Int): Boolean {
        return statusCode >= 500 || statusCode == 429
    }

    /**
     * Checks if an exception should be retried
     */
    fun shouldRetryOnException(exceptionClassName: String): Boolean {
        return retryableExceptions.any {
            exceptionClassName.contains(it, ignoreCase = true)
        }
    }

    /**
     * Calculates delay for attempt number using exponential backoff with jitter
     */
    fun calculateDelay(attemptNumber: Int): Long {
        if (attemptNumber <= 1) return 0L

        // Calculate base delay using exponential backoff
        val baseDelay = (initialDelayMs * Math.pow(multiplier, (attemptNumber - 1).toDouble())).toLong()

        // Cap at max delay
        val cappedDelay = baseDelay.coerceAtMost(maxDelayMs)

        return if (jitterEnabled) {
            // Add jitter to avoid thundering herd
            val jitterRange = (cappedDelay * jitterFactor).toLong()
            val jitter = (-jitterRange..jitterRange).random()
            (cappedDelay + jitter).coerceAtLeast(1_000L) // Minimum 1 second
        } else {
            cappedDelay
        }
    }

    /**
     * Validates configuration parameters
     */
    fun validate(): Result<Unit> {
        return runCatching {
            require(maxAttempts > 0) { "Max attempts must be positive" }
            require(initialDelayMs > 0) { "Initial delay must be positive" }
            require(maxDelayMs >= initialDelayMs) { "Max delay must be >= initial delay" }
            require(multiplier >= 1.0) { "Multiplier must be >= 1.0" }
            require(jitterFactor in 0.0..1.0) { "Jitter factor must be between 0.0 and 1.0" }
            require(retryableStatusCodes.isNotEmpty()) { "At least one retryable status code required" }
        }
    }
}