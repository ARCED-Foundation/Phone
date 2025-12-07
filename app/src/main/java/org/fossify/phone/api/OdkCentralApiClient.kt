package org.fossify.phone.api

import android.util.Base64
import android.util.Log
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.fossify.phone.models.CentralCredentials
import org.fossify.phone.helpers.CallSyncPayload
import org.json.JSONObject

interface OdkCentralApiClient {
    suspend fun sendCallLog(
        payload: CallSyncPayload,
        credentials: CentralCredentials
    ): ApiResponse<String>

    suspend fun testConnection(
        baseUrl: String,
        projectId: String,
        credentials: CentralCredentials
    ): Boolean
}

class OkHttpOdkCentralApiClient(
    private val httpClient: OkHttpClient = defaultClient()
) : OdkCentralApiClient {

    override suspend fun sendCallLog(
        payload: CallSyncPayload,
        credentials: CentralCredentials
    ): ApiResponse<String> {
        return try {
            val requestBody = JSONObject(payload.toEntityRequestBody()).toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(
                    "${payload.baseUrl.trimEnd('/')}/v1/projects/${payload.projectId}" +
                        "/datasets/${payload.datasetName}/entities"
                )
                .post(requestBody)
                .addHeader("Authorization", basicAuth(credentials))
                .addHeader("Content-Type", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (response.isSuccessful) {
                    ApiResponse(success = true, data = responseBody)
                } else {
                    val message = extractErrorMessage(responseBody, response.code)
                    val error = statusCodeToApiError(response.code, message)
                    ApiResponse(success = false, error = error)
                }
            }
        } catch (e: Exception) {
            Log.e("OdkCentralApiClient", "Failed to send call log", e)
            ApiResponse(success = false, error = ApiError.NetworkError(e.message ?: "Unknown error", e))
        }
    }

    private fun extractErrorMessage(body: String?, statusCode: Int): String {
        if (!body.isNullOrBlank()) {
            try {
                val json = JSONObject(body)
                val message = json.optString("message")
                if (message.isNotBlank()) {
                    return message
                }
            } catch (_: Exception) {
                // ignore parsing errors, fallback to default
            }
        }
        return "HTTP $statusCode"
    }

    override suspend fun testConnection(
        baseUrl: String,
        projectId: String,
        credentials: CentralCredentials
    ): Boolean {
        return try {
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/v1/projects/$projectId/forms")
                .head()
                .addHeader("Authorization", basicAuth(credentials))
                .build()
            httpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.w("OdkCentralApiClient", "Connection test failed", e)
            false
        }
    }

    private fun basicAuth(credentials: CentralCredentials): String {
        val token = "${credentials.username}:${credentials.passwordHash}"
        return "Basic ${Base64.encodeToString(token.toByteArray(), Base64.NO_WRAP)}"
    }

    companion object {
        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
    }
}
