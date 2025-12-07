package org.fossify.phone.api

import java.time.Instant
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.fossify.phone.models.CallLog
import org.fossify.phone.models.CallOutcome
import org.fossify.phone.models.CentralCredentials
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.json.JSONObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.fossify.phone.testutil.TestApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class OkHttpOdkCentralApiClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun sendsCallLogWithExtrasAndAuthHeader() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{\"id\":\"123\"}"))

        val client = OkHttpOdkCentralApiClient(OkHttpClient())
        val callLog = sampleCallLog()
        val extras = mapOf("field_id" to "42")
        val credentials = CentralCredentials(
            centralUrl = server.url("/").toString().trimEnd('/'),
            projectId = "project-1",
            username = "user",
            passwordHash = "pass123",
            validated = true
        )

        val response = client.sendCallLog(
            baseUrl = credentials.centralUrl,
            projectId = credentials.projectId,
            datasetName = "dataset-1",
            callLog = callLog,
            extras = extras,
            credentials = credentials
        )

        assertTrue(response.success)
        val request = server.takeRequest()
        assertEquals("/v1/projects/project-1/entityTypes/dataset-1/entities", request.path)
        assertTrue(request.getHeader("Authorization")!!.startsWith("Basic "))

        val payload = JSONObject(request.body.readUtf8())
        assertEquals(callLog.callLogId.toString(), payload.getString("call_log_id"))
        assertEquals("42", payload.getString("field_id"))
        assertEquals(callLog.extrasJson, payload.getString("extras"))
    }

    @Test
    fun handlesServerErrorResponse() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("failure"))

        val client = OkHttpOdkCentralApiClient(OkHttpClient())
        val response = client.sendCallLog(
            baseUrl = server.url("/").toString().trimEnd('/'),
            projectId = "proj",
            datasetName = "ds",
            callLog = sampleCallLog(),
            extras = emptyMap(),
            credentials = CentralCredentials(
                centralUrl = "ignored",
                projectId = "proj",
                username = "user",
                passwordHash = "pass",
                validated = false
            )
        )

        assertTrue("Server error should be reported as failure", !response.success)
        assertTrue(response.error is ApiError.ServerError)
    }

    private fun sampleCallLog(): CallLog = CallLog(
        direction = "outgoing",
        callStartUtc = Instant.now(),
        callEndUtc = Instant.now(),
        durationSeconds = 1.0,
        outcome = CallOutcome.ANSWERED.value,
        deviceId = "device-id",
        phoneNumber = "+1"
    )
}
