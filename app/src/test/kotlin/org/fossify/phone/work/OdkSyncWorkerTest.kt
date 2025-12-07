package org.fossify.phone.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import java.time.Instant
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.fossify.phone.database.AppDatabase
import org.fossify.phone.models.CallLog
import org.fossify.phone.models.CallOutcome
import org.fossify.phone.models.CentralCredentials
import org.fossify.phone.models.PendingSync
import org.fossify.phone.testutil.FakeAppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.fossify.phone.testutil.TestApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class OdkSyncWorkerTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var server: MockWebServer

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        db = FakeAppDatabase()
        setDatabaseInstance(db)

        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
        setDatabaseInstance(null)
    }

    @Test
    fun processesPendingSyncAndMarksCallLogSynced() = runBlocking {
        val baseUrl = server.url("/").toString().trimEnd('/')
        val callLog = insertCallLog()
        insertCredentials(baseUrl, "proj-1")
        insertPending(callLog, payloadFor(callLog, baseUrl, "proj-1", "dataset-1"))
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))

        val worker = TestListenableWorkerBuilder<OdkSyncWorker>(context).build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(db.callLogDao().getById(callLog.callLogId)?.synced == true)
        assertEquals(0, db.pendingSyncDao().count())
    }

    @Test
    fun updatesRetryBackoffOnFailure() = runBlocking {
        val baseUrl = server.url("/").toString().trimEnd('/')
        val callLog = insertCallLog()
        insertCredentials(baseUrl, "proj-1")
        insertPending(callLog, payloadFor(callLog, baseUrl, "proj-1", "dataset-1"))
        server.enqueue(MockResponse().setResponseCode(500))
        val start = Instant.now()

        val worker = TestListenableWorkerBuilder<OdkSyncWorker>(context).build()
        worker.doWork()

        val pending = db.pendingSyncDao().getReadyForSync(Instant.now().plusSeconds(30)).first()
        assertEquals(1, pending.retryCount)
        assertNotNull(pending.nextRetry)
        val delayMs = pending.nextRetry!!.toEpochMilli() - start.toEpochMilli()
        assertTrue("Backoff should be about 10s", delayMs in 9_000..12_000)
        assertFalse(db.callLogDao().getById(callLog.callLogId)?.synced ?: true)
    }

    @Test
    fun dropsItemWhenCredentialsMissing() = runBlocking {
        val baseUrl = server.url("/").toString().trimEnd('/')
        val callLog = insertCallLog()
        insertPending(callLog, payloadFor(callLog, baseUrl, "proj-1", "dataset-1"))

        val worker = TestListenableWorkerBuilder<OdkSyncWorker>(context).build()
        worker.doWork()

        val call = db.callLogDao().getById(callLog.callLogId)
        assertEquals(0, db.pendingSyncDao().count())
        assertNotNull(call)
        assertFalse(call!!.synced)
        assertTrue(call.lastSyncError?.contains("Missing credentials") == true)
    }

    private suspend fun insertCallLog(): CallLog {
        val now = Instant.now()
        val callLog = CallLog(
            direction = "outgoing",
            callStartUtc = now.minusSeconds(30),
            callEndUtc = now,
            durationSeconds = 30.0,
            outcome = CallOutcome.ANSWERED.value,
            deviceId = "device-1",
            phoneNumber = "+1234567890"
        )
        db.callLogDao().insertCallLog(callLog)
        return callLog
    }

    private suspend fun insertCredentials(baseUrl: String, projectId: String) {
        db.centralCredentialsDao().insert(
            CentralCredentials(
                centralUrl = baseUrl,
                projectId = projectId,
                username = "user",
                passwordHash = "pass",
                validated = true,
                lastValidation = System.currentTimeMillis()
            )
        )
    }

    private suspend fun insertPending(callLog: CallLog, payload: String) {
        db.pendingSyncDao().insert(
            PendingSync(
                callLogId = callLog.callLogId,
                syncPayloadJson = payload,
                retryCount = 0,
                nextRetry = Instant.now(),
                errorType = null
            )
        )
    }

    private fun payloadFor(
        callLog: CallLog,
        baseUrl: String,
        projectId: String,
        dataset: String
    ): String {
        val data = JSONObject().apply {
            put("call_log_id", callLog.callLogId.toString())
            put("direction", callLog.direction)
            put("call_start_utc", callLog.callStartUtc.toString())
            put("call_end_utc", callLog.callEndUtc.toString())
            put("duration_seconds", callLog.durationSeconds)
            put("outcome", callLog.outcome)
            put("phone_number", callLog.phoneNumber)
            put("outcome_detail", callLog.outcomeDetail ?: "")
        }

        val payload = JSONObject().apply {
            put("label", "call-${callLog.callLogId}")
            put("data", data)
            put("central_base_url", baseUrl)
            put("project_id", projectId)
            put("dataset", dataset)
        }
        return payload.toString()
    }

    private fun setDatabaseInstance(instance: AppDatabase?) {
        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, instance)
    }
}
