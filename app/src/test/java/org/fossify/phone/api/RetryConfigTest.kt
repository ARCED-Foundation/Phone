package org.fossify.phone.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for RetryConfig
 */
class RetryConfigTest {

    @Test
    fun `shouldRetryOnStatusCode should return true for server errors`() {
        val config = RetryConfig()

        // Test server errors (5xx)
        assertTrue(config.shouldRetryOnStatusCode(500))
        assertTrue(config.shouldRetryOnStatusCode(502))
        assertTrue(config.shouldRetryOnStatusCode(503))
        assertTrue(config.shouldRetryOnStatusCode(504))
        assertTrue(config.shouldRetryOnStatusCode(599))

        // Test rate limiting (429)
        assertTrue(config.shouldRetryOnStatusCode(429))
    }

    @Test
    fun `shouldRetryOnStatusCode should return false for client errors`() {
        val config = RetryConfig()

        // Test client errors (4xx except 429)
        assertFalse(config.shouldRetryOnStatusCode(400))
        assertFalse(config.shouldRetryOnStatusCode(401))
        assertFalse(config.shouldRetryOnStatusCode(403))
        assertFalse(config.shouldRetryOnStatusCode(404))
        assertFalse(config.shouldRetryOnStatusCode(422))
    }

    @Test
    fun `shouldRetryOnException should return true for network exceptions`() {
        val config = RetryConfig()

        assertTrue(config.shouldRetryOnException("java.net.ConnectException"))
        assertTrue(config.shouldRetryOnException("java.net.SocketTimeoutException"))
        assertTrue(config.shouldRetryOnException("java.net.SocketException"))
        assertTrue(config.shouldRetryOnException("java.io.IOException"))
    }

    @Test
    fun `shouldRetryOnException should return false for authentication errors`() {
        val config = RetryConfig()

        assertFalse(config.shouldRetryOnException("java.security.AccessControlException"))
        assertFalse(config.shouldRetryOnException("java.lang.IllegalArgumentException"))
    }

    @Test
    fun `calculateDelay should follow exponential backoff pattern`() {
        val config = RetryConfig(
            initialDelayMs = 1000,
            multiplier = 2.0,
            jitterEnabled = false,
            maxDelayMs = 16000
        )

        // Test exponential growth: 1000 -> 2000 -> 4000 -> 8000 -> 16000
        assertEquals(0L, config.calculateDelay(1))
        assertEquals(2000L, config.calculateDelay(2))
        assertEquals(4000L, config.calculateDelay(3))
        assertEquals(8000L, config.calculateDelay(4))
        assertEquals(16000L, config.calculateDelay(5))
        assertEquals(16000L, config.calculateDelay(6)) // Should cap at max delay
    }

    @Test
    fun `calculateDelay should respect jitter when enabled`() {
        val config = RetryConfig(
            initialDelayMs = 1000,
            multiplier = 2.0,
            jitterEnabled = true,
            jitterFactor = 0.2,
            maxDelayMs = 5000
        )

        val delays = (1..10).map { config.calculateDelay(it) }

        assertEquals(0L, delays.first())
        val nonZero = delays.drop(1)
        assertTrue("Jittered delays should be present", nonZero.isNotEmpty())
        nonZero.forEach { delay ->
            assertTrue("Delay $delay should be within bounds", delay in 1_000L..6_000L)
        }
        assertTrue("At least one jittered delay should differ", nonZero.distinct().size >= 2)
    }

    @Test
    fun `validation should pass for valid configuration`() {
        val config = RetryConfig(
            maxAttempts = 5,
            initialDelayMs = 5000,
            maxDelayMs = 300000,
            multiplier = 1.5,
            jitterFactor = 0.1
        )

        val result = config.validate()
        assertTrue("Configuration should be valid", result.isSuccess)
    }

    @Test
    fun `validation should fail for invalid configuration`() {
        // Test invalid max attempts
        var config = RetryConfig(maxAttempts = 0)
        var result = config.validate()
        assertTrue("Configuration should be invalid with maxAttempts <= 0", result.isFailure)

        // Test invalid initial delay
        config = RetryConfig(initialDelayMs = -1)
        result = config.validate()
        assertTrue("Configuration should be invalid with initialDelay < 0", result.isFailure)

        // Test invalid multiplier
        config = RetryConfig(multiplier = 0.5)
        result = config.validate()
        assertTrue("Configuration should be invalid with multiplier < 1.0", result.isFailure)

        // Test invalid jitter factor
        config = RetryConfig(jitterFactor = 1.5)
        result = config.validate()
        assertTrue("Configuration should be invalid with jitterFactor > 1.0", result.isFailure)
    }

    @Test
    fun `companion objects should provide predefined configurations`() {
        // Test DEFAULT configuration
        val defaultConfig = RetryConfig.DEFAULT
        assertEquals(7, defaultConfig.maxAttempts)
        assertEquals(10000L, defaultConfig.initialDelayMs)
        assertEquals(3840000L, defaultConfig.maxDelayMs)

        // Test CONSERVATIVE configuration
        val conservativeConfig = RetryConfig.CONSERVATIVE
        assertEquals(5, conservativeConfig.maxAttempts)
        assertEquals(30000L, conservativeConfig.initialDelayMs)

        // Test AGGRESSIVE configuration
        val aggressiveConfig = RetryConfig.AGGRESSIVE
        assertEquals(10, aggressiveConfig.maxAttempts)
        assertEquals(1000L, aggressiveConfig.initialDelayMs)
        assertFalse(aggressiveConfig.jitterEnabled)
    }
}
