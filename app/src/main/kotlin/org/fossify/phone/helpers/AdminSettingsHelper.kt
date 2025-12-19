package org.fossify.phone.helpers

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Credentials as OkHttpCredentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.fossify.phone.database.AppDatabase
import org.fossify.phone.database.dao.CentralCredentialsDao
import org.fossify.phone.extensions.config
import org.fossify.phone.models.CentralCredentials
import java.time.Duration
import java.time.Instant

class AdminSettingsHelper(private val context: Context) {
    private val db by lazy { AppDatabase.getInstance(context) }
    private val credsDao: CentralCredentialsDao by lazy { db.centralCredentialsDao() }

    suspend fun saveCredentials(
        centralUrl: String?,
        username: String,
        password: String
    ): Boolean = withContext(Dispatchers.IO) {
        val normalizedUrl = centralUrl?.takeIf { it.isNotBlank() }
            ?: return@withContext false
        val validationSuccess = validateCredentialsBeforeStorage(normalizedUrl, username, password)
        if (!validationSuccess) {
            return@withContext false
        }

        val credentials = CentralCredentials(
            centralUrl = normalizedUrl,
            projectId = "",
            username = username,
            passwordHash = password,
            validated = true,
            lastValidation = System.currentTimeMillis()
        )

        credsDao.clear()
        credsDao.insert(credentials)
        true
    }

    suspend fun validateCredentialsBeforeStorage(
        centralUrl: String,
        username: String,
        password: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (centralUrl.isBlank() || username.isBlank() || password.isBlank()) {
                android.util.Log.e("AdminSettingsHelper", "Validation failed: Empty credentials")
                return@withContext false
            }

            val normalizedUrl = centralUrl.trimEnd('/')
            android.util.Log.d("AdminSettingsHelper", "Starting credential validation for URL: $normalizedUrl")

            // Create unified HTTP client matching ODK sync worker configuration
            val httpClient = OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build()

            // Primary endpoint test: /v1/users/current
            val primarySuccess = testEndpointWithFallback(
                httpClient, normalizedUrl, username, password, "/v1/users/current"
            )

            if (primarySuccess) {
                android.util.Log.d("AdminSettingsHelper", "Primary endpoint validation successful")
                return@withContext true
            }

            // Fallback: Test with /v1/projects endpoint for ODK Central v2025.3.0
            android.util.Log.d("AdminSettingsHelper", "Primary endpoint failed, trying fallback endpoint")
            val fallbackSuccess = testEndpointWithFallback(
                httpClient, normalizedUrl, username, password, "/v1/projects"
            )

            if (fallbackSuccess) {
                android.util.Log.d("AdminSettingsHelper", "Fallback endpoint validation successful")
                return@withContext true
            }

            android.util.Log.e("AdminSettingsHelper", "All endpoint validation attempts failed")
            return@withContext false
        } catch (e: Exception) {
            android.util.Log.e("AdminSettingsHelper", "Connection validation exception: ${e.javaClass.simpleName} - ${e.message}")
            android.util.Log.e("AdminSettingsHelper", "Exception cause: ${e.cause?.message}")
            android.util.Log.e("AdminSettingsHelper", "Exception stack trace:", e)
            false
        }
    }

    private fun testEndpointWithFallback(
        httpClient: OkHttpClient,
        baseUrl: String,
        username: String,
        password: String,
        endpoint: String
    ): Boolean {
        val url = "$baseUrl$endpoint"
        android.util.Log.d("AdminSettingsHelper", "Testing endpoint: $url")

        try {
            // Try GET request first
            val getRequest = Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", OkHttpCredentials.basic(username, password))
                .addHeader("User-Agent", "Fossify-Phone/1.8.7")
                .addHeader("Accept", "application/json")
                .build()

            httpClient.newCall(getRequest).execute().use { response ->
                android.util.Log.d("AdminSettingsHelper", "GET $endpoint - Status: ${response.code} - ${response.message}")
                android.util.Log.d("AdminSettingsHelper", "GET $endpoint - Headers: ${response.headers}")

                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    android.util.Log.d("AdminSettingsHelper", "GET $endpoint - Response length: ${responseBody.length}")
                    if (responseBody.isNotEmpty()) {
                        android.util.Log.d("AdminSettingsHelper", "GET $endpoint - Response preview: ${responseBody.take(200)}")
                    }
                    return true
                }

                if (response.code == 404) {
                    android.util.Log.d("AdminSettingsHelper", "GET $endpoint - 404 Not Found (expected for some endpoints)")
                }
            }

            // Try POST request for some endpoints that might require it
            if (endpoint == "/v1/users/current") {
                val jsonBody = "{}".toRequestBody("application/json".toMediaType())
                val postRequest = Request.Builder()
                    .url(url)
                    .post(jsonBody)
                    .addHeader("Authorization", OkHttpCredentials.basic(username, password))
                    .addHeader("User-Agent", "Fossify-Phone/1.8.7")
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .build()

                httpClient.newCall(postRequest).execute().use { response ->
                    android.util.Log.d("AdminSettingsHelper", "POST $endpoint - Status: ${response.code} - ${response.message}")
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: ""
                        android.util.Log.d("AdminSettingsHelper", "POST $endpoint - Response length: ${responseBody.length}")
                        return true
                    }
                }
            }

            return false
        } catch (e: Exception) {
            android.util.Log.e("AdminSettingsHelper", "Endpoint test failed for $url: ${e.message}")
            return false
        }
    }

    suspend fun hasCredentials(): Boolean = withContext(Dispatchers.IO) {
        credsDao.getCredentials() != null
    }

    suspend fun hasValidCredentials(): Boolean = withContext(Dispatchers.IO) {
        val credentials = credsDao.getCredentials()
        if (credentials == null) return@withContext false

        // Check if credentials are validated and recent (within 24 hours)
        val isValidated = credentials.validated
        val lastValidation = credentials.lastValidation
        val isRecent = lastValidation?.let {
            System.currentTimeMillis() - it < 24 * 60 * 60 * 1000L // 24 hours
        } ?: false

        if (isValidated && isRecent) {
            true
        } else {
            // If not validated or validation is old, try to validate again
            try {
                validateCredentialsBeforeStorage(credentials.centralUrl, credentials.username, credentials.passwordHash)
            } catch (e: Exception) {
                false
            }
        }
    }

    suspend fun getCredentials(): CentralCredentials? = withContext(Dispatchers.IO) { credsDao.getCredentials() }

    suspend fun getValidatedCredentials(): CentralCredentials? = withContext(Dispatchers.IO) { credsDao.getValidatedCredentials() }

    suspend fun updateValidation(validated: Boolean, lastValidation: Long?) = withContext(Dispatchers.IO) {
        credsDao.updateValidation(validated, lastValidation)
    }

    suspend fun deleteAllCredentials() = withContext(Dispatchers.IO) { credsDao.clear() }

    // Reserved keys management (SharedPreferences)
    fun getReservedKeys(): Set<String> = context.config.reservedKeys

    fun setReservedKeys(keys: Set<String>) {
        context.config.reservedKeys = keys
    }

    fun addReservedKey(key: String) {
        val current = getReservedKeys()
        setReservedKeys(current + key)
    }

    fun removeReservedKey(key: String) {
        val current = getReservedKeys()
        setReservedKeys(current - key)
    }

    fun getReservedKeysWithDefaults(): Set<String> = DEFAULT_RESERVED_KEYS + getReservedKeys()

    fun updateReservedKeys(keys: Set<String>): Boolean {
        val validKeys = keys.filter { isValidReservedKey(it.trim()) }.toSet()
        if (validKeys.isEmpty() && keys.isNotEmpty()) {
            return false
        }

        setReservedKeys(validKeys)
        return true
    }

    fun clearCustomReservedKeys() {
        setReservedKeys(emptySet())
    }

    fun getReservedKeysCount(): Int = getReservedKeys().size

    fun hasReservedKeys(): Boolean = getReservedKeys().isNotEmpty()

    fun isReservedKeyValid(key: String): Boolean = isValidReservedKey(key.trim())

    fun getLogRetentionDays(): Int = context.config.syncLogRetentionDays

    suspend fun updateLogRetentionDays(days: Int): Boolean = withContext(Dispatchers.IO) {
        if (days < 1 || days > 365) {
            return@withContext false
        }
        context.config.syncLogRetentionDays = days
        val cutoff = Instant.now().minus(Duration.ofDays(days.toLong()))
        db.syncLogDao().deleteOlderThan(cutoff)
        true
    }

    private fun isValidReservedKey(key: String): Boolean {
        if (key.isBlank()) return false
        if (key.length > 100) return false
        if (key.any { !it.isLetterOrDigit() && it != '_' && it != '-' }) {
            return false
        }
        return true
    }

    companion object {
        val DEFAULT_RESERVED_KEYS = Config.DEFAULT_RESERVED_KEYS
    }

    /**
     * Get detailed error information for UI display
     * Returns a map with error details that can be displayed to users
     */
    fun getDiagnosticInfo(): Map<String, String> {
        return mapOf(
            "timestamp" to System.currentTimeMillis().toString(),
            "client_version" to "1.8.7",
            "supported_endpoints" to listOf("/v1/users/current", "/v1/projects").joinToString(", "),
            "timeout_seconds" to "15",
            "user_agent" to "Fossify-Phone/1.8.7"
        )
    }

    /**
     * Parse HTTP error code to user-friendly message
     */
    fun getErrorMessageForCode(httpCode: Int): String {
        return when (httpCode) {
            200 -> "Connection successful"
            400 -> "Bad request - Invalid URL or parameters"
            401 -> "Unauthorized - Invalid username or password"
            403 -> "Forbidden - Insufficient permissions"
            404 -> "Not found - Server or endpoint not found"
            405 -> "Method not allowed - Try different HTTP method"
            429 -> "Too many requests - Server rate limiting"
            500 -> "Internal server error - Contact server administrator"
            502 -> "Bad gateway - Server connectivity issues"
            503 -> "Service unavailable - Server down for maintenance"
            504 -> "Gateway timeout - Server taking too long to respond"
            else -> "HTTP error $httpCode - ${getGenericErrorMessage(httpCode)}"
        }
    }

    private fun getGenericErrorMessage(httpCode: Int): String {
        return when {
            httpCode in 200..299 -> "Success"
            httpCode in 400..499 -> "Client error"
            httpCode in 500..599 -> "Server error"
            else -> "Unknown error"
        }
    }
}
