package org.fossify.phone.helpers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.fossify.phone.database.AppDatabase
import org.fossify.phone.models.CallLog
import org.fossify.phone.models.CallOutcome
import org.fossify.phone.testutil.FakeAppDatabase
import org.fossify.phone.testutil.TestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class ManualCallRecordHelperTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var helper: ManualCallRecordHelper

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = FakeAppDatabase()
        setDatabaseInstance(db)
        helper = ManualCallRecordHelper(context)
    }

    @After
    fun tearDown() {
        db.close()
        setDatabaseInstance(null)
    }

    @Test
    fun manualRecordCreatesCallLogAndPendingSync() = runBlocking {
        val start = Instant.now().minusSeconds(30)
        val end = Instant.now()
        val data = ManualCallRecordData(
            phoneNumber = "+10000000000",
            isOutgoing = true,
            startTime = start.toEpochMilli(),
            endTime = end.toEpochMilli(),
            durationSeconds = 30.0,
            outcome = CallOutcome.ANSWERED,
            outcomeDetail = "manual"
        )

        val result = helper.createManualRecord(data)
        assertTrue(result is ManualCallRecordResult.Success)
        assertEquals(1, db.callLogDao().getTotalCount())
        assertEquals(1, db.pendingSyncDao().count())
    }

    @Test
    fun manualRecordDuplicateIsSkipped() = runBlocking {
        val start = Instant.now().minusSeconds(15)
        val end = Instant.now()
        val existing = CallLog(
            direction = "incoming",
            callStartUtc = start,
            callEndUtc = end,
            durationSeconds = 15.0,
            outcome = CallOutcome.ANSWERED.value,
            deviceId = "device-1",
            phoneNumber = "+10000000000"
        )
        db.callLogDao().insertCallLog(existing)

        val data = ManualCallRecordData(
            phoneNumber = "+10000000000",
            isOutgoing = false,
            startTime = start.toEpochMilli(),
            endTime = end.toEpochMilli(),
            durationSeconds = 15.0,
            outcome = CallOutcome.ANSWERED,
            outcomeDetail = "manual"
        )

        val result = helper.createManualRecord(data)
        assertTrue(result is ManualCallRecordResult.Duplicate)
        assertEquals(1, db.callLogDao().getTotalCount())
    }

    private fun setDatabaseInstance(instance: AppDatabase?) {
        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, instance)
    }
}
