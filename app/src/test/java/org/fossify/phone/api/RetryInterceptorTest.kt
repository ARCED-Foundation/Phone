package org.fossify.phone.api

import okhttp3.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.fossify.phone.testutil.TestApplication

/**
 * Unit tests for RetryInterceptor
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class RetryInterceptorTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var retryInterceptor: RetryInterceptor

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        retryInterceptor = RetryInterceptor()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `success response should not be retried`() {
        // Mock a successful response
        mockWebServer.enqueue(MockResponse().setResponseCode(200))
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val client = OkHttpClient.Builder()
            .addInterceptor(retryInterceptor)
            .build()

        val request = Request.Builder()
            .url(mockWebServer.url("/test"))
            .build()

        val response = client.newCall(request).execute()

        // Should succeed on first attempt
        assertEquals(200, response.code)

        // Only one request should have been made
        assertEquals(1, mockWebServer.requestCount)
    }

    @Test
    fun `server error response should be retried`() {
        // Mock a server error first, then success
        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val client = OkHttpClient.Builder()
            .addInterceptor(retryInterceptor)
            .build()

        val request = Request.Builder()
            .url(mockWebServer.url("/test"))
            .build()

        val response = client.newCall(request).execute()

        // Should succeed after retry
        assertEquals(200, response.code)

        // Two requests should have been made (error + retry)
        assertEquals(2, mockWebServer.requestCount)
    }

    @Test
    fun `rate limit response should be retried`() {
        // Mock a rate limit response first, then success
        mockWebServer.enqueue(MockResponse().setResponseCode(429))
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val client = OkHttpClient.Builder()
            .addInterceptor(retryInterceptor)
            .build()

        val request = Request.Builder()
            .url(mockWebServer.url("/test"))
            .build()

        val response = client.newCall(request).execute()

        // Should succeed after retry
        assertEquals(200, response.code)

        // Two requests should have been made (rate limit + retry)
        assertEquals(2, mockWebServer.requestCount)
    }

    @Test
    fun `network exception should be retried`() {
        // Mock a network failure first, then success
        mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val client = OkHttpClient.Builder()
            .addInterceptor(retryInterceptor)
            .build()

        val request = Request.Builder()
            .url(mockWebServer.url("/test"))
            .build()

        val response = client.newCall(request).execute()

        // Should succeed after retry
        assertEquals(200, response.code)

        // Two requests should have been made (failure + retry)
        assertEquals(2, mockWebServer.requestCount)
    }

    @Test
    fun `retry configuration should be customizable`() {
        val customConfig = RetryConfig(
            maxAttempts = 3,
            initialDelayMs = 1000,
            maxDelayMs = 16000
        )

        val retryPolicy = RetryPolicy(customConfig)
        val retryInterceptor = RetryInterceptor(retryPolicy)

        // Mock two failures, then success
        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val client = OkHttpClient.Builder()
            .addInterceptor(retryInterceptor)
            .build()

        val request = Request.Builder()
            .url(mockWebServer.url("/test"))
            .build()

        val response = client.newCall(request).execute()

        // Should succeed after third attempt
        assertEquals(200, response.code)

        // Three requests should have been made
        assertEquals(3, mockWebServer.requestCount)
    }

    @Test
    fun `exponential backoff should increase delays`() {
        val config = RetryConfig(
            maxAttempts = 4,
            initialDelayMs = 100,
            maxDelayMs = 5000,
            jitterEnabled = false
        )

        val retryPolicy = RetryPolicy(config)
        val retryInterceptor = RetryInterceptor(retryPolicy)

        // Mock three failures, then success
        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val client = OkHttpClient.Builder()
            .addInterceptor(retryInterceptor)
            .build()

        val request = Request.Builder()
            .url(mockWebServer.url("/test"))
            .build()

        val startTime = System.currentTimeMillis()
        val response = client.newCall(request).execute()
        val endTime = System.currentTimeMillis()

        // Should succeed after fourth attempt
        assertEquals(200, response.code)

        // Total time should be approximately the sum of delays: 100 + 200 + 400 = 700ms
        val totalTime = endTime - startTime
        assertTrue("Total time $totalTime should be between 600-1500ms", totalTime in 600..1500)

        // Four requests should have been made
        assertEquals(4, mockWebServer.requestCount)
    }
}
