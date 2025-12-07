package org.fossify.phone.api

import android.util.Log
import okhttp3.*
import okio.Buffer
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/**
 * OkHttp interceptor that implements exponential backoff retry strategy
 * for ODK Central API calls
 */
class RetryInterceptor(
    private val retryPolicy: RetryPolicy = RetryPolicy()
) : Interceptor {

    companion object {
        private const val TAG = "RetryInterceptor"
        private const val MAX_RETRY_BODY_SIZE = 1024 * 1024 // 1MB
        private val UTF8 = Charset.forName("UTF-8")
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val context = RetryContextFactory.create(retryPolicy.getMaxAttempts())

        return executeWithRetry(chain, request, context)
    }

    /**
     * Executes the request with retry logic
     */
    private fun executeWithRetry(
        chain: Interceptor.Chain,
        request: Request,
        context: RetryContext
    ): Response {
        return try {
            Log.d(TAG, "Executing request: ${request.method} ${request.url}")
            val response = chain.proceed(request)

            handleResponse(response, chain, request, context)

        } catch (exception: Throwable) {
            handleException(exception, chain, request, context)
        }
    }

    /**
     * Handles successful or retryable responses
     */
    private fun handleResponse(
        response: Response,
        chain: Interceptor.Chain,
        request: Request,
        context: RetryContext
    ): Response {
        // Log response details
        Log.d(TAG, "Retryable response: ${response.code} ${response.message}")

        // Check if response should be retried
        val retryDecision = retryPolicy.createRetryDecision(response, context.attemptNumber)

        return when (retryDecision) {
            is RetryDecision.NoRetry -> {
                Log.d(TAG, "No retry needed for response: ${response.code}")
                response
            }

            is RetryDecision.Retry -> {
                Log.i(TAG, "Retryable response, will retry in ${retryDecision.delayMs}ms")

                // Close the response body before retrying
                response.body?.close()

                // Wait for the retry delay
                Thread.sleep(retryDecision.delayMs)

                // Execute next retry
                executeWithRetry(chain, request, context.nextAttempt())
            }
        }
    }

    /**
     * Handles exceptions that may be retryable
     */
    private fun handleException(
        exception: Throwable,
        chain: Interceptor.Chain,
        request: Request,
        context: RetryContext
    ): Response {
        // Log exception details
        Log.d(TAG, "Retryable exception: ${exception.javaClass.simpleName}")

        // Check if exception should be retried
        val retryDecision = retryPolicy.createRetryDecision(exception, context.attemptNumber)

        return when (retryDecision) {
            is RetryDecision.NoRetry -> {
                Log.d(TAG, "No retry needed for exception: ${exception.javaClass.simpleName}")
                throw exception
            }

            is RetryDecision.Retry -> {
                Log.i(TAG, "Retryable exception, will retry in ${retryDecision.delayMs}ms")

                // Wait for the retry delay
                Thread.sleep(retryDecision.delayMs)

                // Execute next retry
                executeWithRetry(chain, request, context.nextAttempt())
            }
        }
    }

    
    /**
     * Creates a new request with retry headers
     */
    private fun createRetryRequest(originalRequest: Request, attempt: Int): Request {
        return originalRequest.newBuilder()
            .header("X-Retry-Attempt", attempt.toString())
            .header("X-Retry-Total", retryPolicy.getMaxAttempts().toString())
            .build()
    }

    /**
     * Validates that the request can be retried (idempotent methods only)
     */
    private fun canBeRetried(request: Request): Boolean {
        val method = request.method
        return method == "GET" || method == "HEAD" ||
               method == "OPTIONS" || method == "PUT" ||
               method == "DELETE"
    }

    /**
     * Gets the request method for logging
     */
    private fun getRequestSummary(request: Request): String {
        val method = request.method
        val url = request.url.encodedPath
        val headers = request.headers.toMultimap()

        return "$method $url${if (headers.containsKey("Authorization")) " [AUTH]" else ""}"
    }

    /**
     * Gets the response summary for logging
     */
    private fun getResponseSummary(response: Response): String {
        val code = response.code
        val message = response.message
        val isSuccessful = response.isSuccessful

        return "HTTP $code $message${if (isSuccessful) " [SUCCESS]" else " [RETRYABLE]"}"
    }

    /**
     * Logs request details
     */
    private fun logRequest(request: Request) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Request: ${getRequestSummary(request)}")

            // Log headers (excluding sensitive ones like Authorization)
            request.headers.names().forEach { name ->
                if (!name.equals("Authorization", ignoreCase = true)) {
                    Log.d(TAG, "Header: $name: ${request.headers[name]}")
                }
            }

            // Log body for safe methods
            if (request.method in listOf("GET", "HEAD")) {
                Log.d(TAG, "No request body for ${request.method}")
            } else {
                try {
                    val buffer = Buffer()
                    request.body?. writeTo(buffer)
                    val charset = request.body?.contentType()?.charset(UTF8) ?: UTF8
                    val body = buffer.readString(charset)
                    Log.d(TAG, "Request body: ${body.take(500)}${if (body.length > 500) "..." else ""}")
                } catch (e: Exception) {
                    Log.d(TAG, "Could not read request body")
                }
            }
        }
    }

    /**
     * Logs response details
     */
    private fun logResponse(response: Response) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Response: ${getResponseSummary(response)}")

            // Log headers
            response.headers.names().forEach { name ->
                Log.d(TAG, "Header: $name: ${response.headers[name]}")
            }

            // Log body preview
            if (response.body != null) {
                try {
                    val body = response.peekBody(1024).string()
                    Log.d(TAG, "Response body preview: ${body.take(500)}${if (body.length > 500) "..." else ""}")
                } catch (e: Exception) {
                    Log.d(TAG, "Could not read response body")
                }
            }
        }
    }
}

/**
 * Factory for creating RetryInterceptor instances
 */
object RetryInterceptorFactory {
    /**
     * Creates a RetryInterceptor with default configuration
     */
    fun create(): RetryInterceptor {
        return RetryInterceptor()
    }

    /**
     * Creates a RetryInterceptor with custom retry policy
     */
    fun create(retryPolicy: RetryPolicy): RetryInterceptor {
        return RetryInterceptor(retryPolicy)
    }

    /**
     * Creates a RetryInterceptor with custom configuration
     */
    fun create(config: RetryConfig): RetryInterceptor {
        val retryPolicy = RetryPolicy(config)
        return RetryInterceptor(retryPolicy)
    }
}

/**
 * Extension function to add retry interceptor to OkHttp builder
 */
fun OkHttpClient.Builder.addRetryInterceptor(config: RetryConfig = RetryConfig.DEFAULT): OkHttpClient.Builder {
    return addInterceptor(RetryInterceptorFactory.create(config))
}

/**
 * Extension function to check if a request is idempotent (can be safely retried)
 */
fun Request.isIdempotent(): Boolean {
    val method = this.method
    return method == "GET" || method == "HEAD" ||
           method == "OPTIONS" || method == "PUT" ||
           method == "DELETE"
}