package org.fossify.phone.helpers

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.fossify.phone.models.CallLog
import org.fossify.phone.models.CallOutcome
import org.fossify.phone.models.PartialSurveyData
import org.fossify.phone.models.PendingSync
import org.fossify.phone.testutil.FakeAppDatabase
import org.fossify.phone.testutil.TestApplication
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class SurveyDataCompletionHelperTest {

    private lateinit var db: FakeAppDatabase
    private lateinit var helper: SurveyDataCompletionHelper
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setup() {
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.ERROR)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        db = FakeAppDatabase()
        helper = SurveyDataCompletionHelper.createForTest(context, db)
    }

    @Test
    fun `saveDraft persists values and clears empty drafts`() = runBlocking {
        val callLogId = UUID.randomUUID()

        helper.saveDraft(callLogId, "SURVEY-1", "note")
        val stored = db.partialSurveyDataDao().getByCallLog(callLogId)
        assertEquals("SURVEY-1", stored?.surveyId)
        assertEquals("note", stored?.additionalNotes)

        helper.saveDraft(callLogId, "", "")
        assertNull(db.partialSurveyDataDao().getByCallLog(callLogId))
    }

    @Test
    fun `completeSurvey updates call log, clears drafts, and refreshes pending payload`() = runBlocking {
        val callLogId = UUID.randomUUID()
        val callLog = CallLog(
            callLogId = callLogId,
            direction = "incoming",
            callStartUtc = Instant.now().minusSeconds(5),
            callEndUtc = Instant.now(),
            durationSeconds = 5.0,
            outcome = CallOutcome.ANSWERED.value,
            deviceId = "device_1",
            extrasJson = "{}"
        )
        db.callLogDao().insertCallLog(callLog)
        db.partialSurveyDataDao().insert(
            PartialSurveyData(callLogId = callLogId, surveyId = "OLD", additionalNotes = "old", isComplete = false)
        )
        val payload = JSONObject().apply {
            put("label", "survey-$callLogId")
            put("data", JSONObject())
            put("central_base_url", "https://central.example.com")
            put("project_id", "1")
            put("dataset", "call_logs")
        }.toString()
        db.pendingSyncDao().insert(
            PendingSync(
                callLogId = callLogId,
                syncPayloadJson = payload
            )
        )

        val success = helper.completeSurvey(callLogId, "SURV-999", "updated")
        assertTrue(success)

        val updatedLog = db.callLogDao().getById(callLogId)
        assertEquals("SURV-999", updatedLog?.surveyId)
        assertEquals("updated", updatedLog?.additionalNotes)
        assertNull(db.partialSurveyDataDao().getByCallLog(callLogId))

        val pending = db.pendingSyncDao().getByCallLog(callLogId)
        val pendingPayload = JSONObject(pending!!.syncPayloadJson)
        val data = pendingPayload.getJSONObject("data")
        assertEquals("SURV-999", data.getString("survey_id"))
        assertEquals("updated", data.getString("additional_notes"))
    }
}
