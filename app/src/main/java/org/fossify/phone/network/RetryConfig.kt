package org.fossify.phone.network

import okhttp3.internal.http.promisesBody
import java.util.concurrent.TimeUnit

/**
 * Configuration parameters for retry behavior
 *
 * @param maxAttempts Maximum number of retry attempts (default: 7)
 * @param baseDelayMs Base delay in milliseconds for exponential backoff (default: 10000ms = 10s)
 * @param maxDelayMs Maximum delay in milliseconds for exponential backoff (default: 3840000ms = 64min)
 * @param multiplier Multiplier for exponential backoff growth (default: 2.0)
 * @param jitterEnabled Whether to add random jitter to delays (default: true)
 * @param jitterFactor Jitter factor (0.0 to 1.0) (default: 0.1)
 * @param retryableStatusCodes Set of HTTP status codes that should trigger retry (default: 408, 429, 500, 502, 503, 504)
 * @param retryOnNetworkErrors Whether to retry on network errors like timeouts (default: true)
 * @param retryOnAuthErrors Whether to retry on authentication errors (401, 403) (default: false)
 * @param enableLogging Whether to enable detailed retry logging (default: true)
 */
data class RetryConfig(
    val maxAttempts: Int = 7,
    val baseDelayMs: Long = 10_000, // 10 seconds
    val maxDelayMs: Long = 3_840_000, // 64 minutes
    val multiplier: Double = 2.0,
    val jitterEnabled: Boolean = true,
    val jitterFactor: Double = 0.1,
    val retryableStatusCodes: Set<Int> = setOf(
        408, // Request Timeout
        429, // Too Many Requests
        500, // Internal Server Error
        502, // Bad Gateway
        503, // Service Unavailable
        504  // Gateway Timeout
    ),
    val retryOnNetworkErrors: Boolean = true,
    val retryOnAuthErrors: Boolean = false,
    val enableLogging: Boolean = true
) {
    init {
        require(maxAttempts > 0) { "Max attempts must be greater than 0" }
        require(baseDelayMs > 0) { "Base delay must be greater than 0" }
        require(maxDelayMs >= baseDelayMs) { "Max delay must be greater than or equal to base delay" }
        require(multiplier >= 1.0) { "Multiplier must be greater than or equal to 1.0" }
        require(jitterFactor in 0.0..1.0) { "Jitter factor must be between 0.0 and 1.0" }
        require(retryableStatusCodes.isNotEmpty()) { "At least one retryable status code must be specified" }
    }

    companion object {
        /**
         * Creates a retry config with linear growth strategy (matches WorkManager LINEAR policy)
         */
        fun linearConfig(
            maxAttempts: Int = 7,
            baseDelayMs: Long = 10_000,
            maxDelayMs: Long = 3_840_000,
            retryableStatusCodes: Set<Int> = setOf(408, 429, 500, 502, 503, 504)
        ): RetryConfig {
            return RetryConfig(
                maxAttempts = maxAttempts,
                baseDelayMs = baseDelayMs,
                maxDelayMs = maxDelayMs,
                multiplier = 1.0, // Linear growth
                retryableStatusCodes = retryableStatusCodes
            )
        }

        /**
         * Creates a retry config with exponential growth strategy
         */
        fun exponentialConfig(
            maxAttempts: Int = 7,
            baseDelayMs: Long = 10_000,
            maxDelayMs: Long = 3_840_000,
            multiplier: Double = 2.0,
            retryableStatusCodes: Set<Int> = setOf(408, 429, 500, 502, 503, 504)
        ): RetryConfig {
            return RetryConfig(
                maxAttempts = maxAttempts,
                baseDelayMs = baseDelayMs,
                maxDelayMs = maxDelayMs,
                multiplier = multiplier,
                retryableStatusCodes = retryableStatusCodes
            )
        }
    }
}