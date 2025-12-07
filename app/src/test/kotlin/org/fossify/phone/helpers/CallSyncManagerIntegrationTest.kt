package org.fossify.phone.helpers

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.fossify.phone.api.ApiError
import org.fossify.phone.api.ApiResponse
import org.fossify.phone.api.OdkCentralApiClient
import org.fossify.phone.database.dao.CallLogDao
import org.fossify.phone.database.dao.CentralCredentialsDao
import org.fossify.phone.database.dao.PendingSyncDao
import org.fossify.phone.models.CallLog
import org.fossify.phone.models.CallOutcome
import org.fossify.phone.models.CentralCredentials
import org.fossify.phone.helpers.CallSyncPayload
import org.fossify.phone.testutil.FakeAppDatabase
import org.fossify.phone.testutil.TestApplication
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class CallSyncManagerIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: FakeAppDatabase
    private lateinit var apiClient: FakeOdkCentralApiClient

    @Before
    fun setUp() {
        db = FakeAppDatabase()
        apiClient = FakeOdkCentralApiClient()
    }

    @After
    fun tearDown() {
        db.clearAllTables()
    }

    @Test
    fun `syncCallLog returns false when config missing`() = runBlocking {
        val manager = createManager()
        val callLog = sampleCallLog()
        db.callLogDao().insertCallLog(callLog)

        val result = manager.syncCallLog(callLog, Intent())

        assertFalse(result)
        assertEquals(0, db.pendingSyncDao().count())
        assertFalse(db.callLogDao().getById(callLog.callLogId)?.synced ?: false)
        assertNull(apiClient.lastPayload)
    }

    @Test
    fun `successful sync marks log as synced and filters extras`() = runBlocking {
        val manager = createManager()
        val callLog = sampleCallLog()
        db.callLogDao().insertCallLog(callLog)
        insertCredentials()
        apiClient.enqueue(ApiResponse(success = true, data = "ok"))

        val intent = Intent().apply {
            putExtra("centralBaseUrl", "https://central.example.com")
            putExtra("centralProjectId", "proj1")
            putExtra("centralDatasetName", "datasetA")
            putExtra("custom_field", "abc123")
            putExtra("system_trace", "remove_me")
        }

        val result = manager.syncCallLog(callLog, intent)

        assertTrue(result)
        val stored = db.callLogDao().getById(callLog.callLogId)
        assertTrue(stored?.synced == true)
        assertEquals(0, db.pendingSyncDao().count())

        val dataSent = apiClient.lastPayload?.data ?: emptyMap<String, String>()
        assertEquals("abc123", dataSent["custom_field"])
        assertFalse(dataSent.containsKey("centralBaseUrl"))
        assertFalse(dataSent.containsKey("system_trace"))
    }

    @Test
    fun `failed sync queues pending payload with filtered extras and error type`() = runBlocking {
        val manager = createManager()
        val callLog = sampleCallLog()
        db.callLogDao().insertCallLog(callLog)
        insertCredentials()
        apiClient.enqueue(ApiResponse(success = false, error = ApiError.NetworkError("network down")))

        val intent = Intent().apply {
            putExtra("centralBaseUrl", "https://central.example.com")
            putExtra("centralProjectId", "proj1")
            putExtra("centralDatasetName", "datasetA")
            putExtra("custom_field", "abc123")
            putExtra("system_startTime", "should_filter")
        }

        val result = manager.syncCallLog(callLog, intent)

        assertFalse(result)
        val pending = db.pendingSyncDao().getByCallLog(callLog.callLogId)
        assertNotNull(pending)

        val payload = JSONObject(pending!!.syncPayloadJson)
        assertEquals("https://central.example.com", payload.getString("central_base_url"))
        assertEquals("proj1", payload.getString("project_id"))
        assertEquals("datasetA", payload.getString("dataset"))
        val data = payload.getJSONObject("data")
        assertEquals(callLog.callLogId.toString(), data.getString("call_log_id"))
        assertEquals("abc123", data.getString("custom_field"))
        assertFalse(data.has("system_startTime"))

        assertEquals("network", pending.errorType)
        val stored = db.callLogDao().getById(callLog.callLogId)
        assertFalse(stored?.synced ?: true)
    }

    private fun createManager(): CallSyncManager {
        val constructor = CallSyncManager::class.java.getDeclaredConstructor(
            Context::class.java,
            CallLogDao::class.java,
            CentralCredentialsDao::class.java,
            PendingSyncDao::class.java,
            org.fossify.phone.api.OdkCentralApiClient::class.java,
            IntentExtrasHelper::class.java
        )
        constructor.isAccessible = true
        return constructor.newInstance(
            context,
            db.callLogDao(),
            db.centralCredentialsDao(),
            db.pendingSyncDao(),
            apiClient,
            IntentExtrasHelper
        )
    }

    private fun sampleCallLog(): CallLog = CallLog(
        direction = "incoming",
        callStartUtc = Instant.now().minusSeconds(10),
        callEndUtc = Instant.now(),
        durationSeconds = 10.0,
        outcome = CallOutcome.ANSWERED.value,
        deviceId = "device-1",
        phoneNumber = "+1002003000",
        extrasJson = "{}"
    )

    private suspend fun insertCredentials() {
        db.centralCredentialsDao().insert(
            CentralCredentials(
                centralUrl = "https://central.example.com",
                projectId = "proj1",
                username = "user",
                passwordHash = "pass",
                validated = true
            )
        )
    }

    private class FakeOdkCentralApiClient : OdkCentralApiClient {
        private val responses = ArrayDeque<ApiResponse<String>>()
        var lastPayload: CallSyncPayload? = null

        fun enqueue(response: ApiResponse<String>) {
            responses.add(response)
        }

        override suspend fun sendCallLog(
            payload: CallSyncPayload,
            credentials: CentralCredentials
        ): ApiResponse<String> {
            lastPayload = payload
            return if (responses.isNotEmpty()) responses.removeFirst() else ApiResponse(success = true)
        }

        override suspend fun testConnection(
            baseUrl: String,
            projectId: String,
            credentials: CentralCredentials
        ): Boolean = true
    }
}
