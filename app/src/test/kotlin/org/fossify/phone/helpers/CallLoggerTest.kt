package org.fossify.phone.helpers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.fossify.phone.database.AppDatabase
import org.fossify.phone.models.CallLog
import org.fossify.phone.models.CallOutcome
import org.fossify.phone.utils.UuidUtils
import org.fossify.phone.testutil.FakeAppDatabase
import org.fossify.phone.testutil.TestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class CallLoggerTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = FakeAppDatabase()
        setDatabaseInstance(db)
    }

    @After
    fun tearDown() {
        db.close()
        setDatabaseInstance(null)
    }

    @Test
    fun callLogValidationEnforcesConstraints() {
        val now = Instant.now()
        assertThrows(IllegalArgumentException::class.java) {
            CallLog(
                direction = "sideways",
                callStartUtc = now,
                callEndUtc = now,
                deviceId = "device-1"
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            CallLog(
                direction = "incoming",
                callStartUtc = now.plusSeconds(10),
                callEndUtc = now,
                deviceId = "device-1"
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            CallLog(
                direction = "incoming",
                callStartUtc = now,
                callEndUtc = now,
                durationSeconds = -1.0,
                deviceId = "device-1"
            )
        }
    }

    @Test
    fun createCallLogPersistsAndQueuesPendingSync() = runBlocking {
        val logger = CallLogger(context)
        val start = Instant.now().minusSeconds(60)
        val end = Instant.now()

        val created = logger.createCallLog(
            phoneNumber = "+12025550123",
            isOutgoing = true,
            startTime = start.toEpochMilli(),
            endTime = end.toEpochMilli(),
            durationSeconds = 60.0,
            outcome = CallOutcome.ANSWERED,
            outcomeDetail = "connected",
            instanceId = "instance-1",
            enumeratorId = "enum-1"
        )

        assertNotNull(created)
        val stored = db.callLogDao().getById(created!!.callLogId)
        assertNotNull(stored)
        assertEquals(1, db.pendingSyncDao().count())
        val pending = db.pendingSyncDao().getReadyForSync(Instant.now())
        assertEquals(created.callLogId, pending.first().callLogId)
    }

    @Test
    fun duplicateCallLogSkippedWhenRecent() = runBlocking {
        val logger = CallLogger(context)
        val start = Instant.now().minusSeconds(90)
        val end = start.plusSeconds(10)
        val existing = CallLog(
            callLogId = UuidUtils.generateCallLogId(),
            direction = "incoming",
            callStartUtc = start,
            callEndUtc = end,
            durationSeconds = 10.0,
            outcome = CallOutcome.ANSWERED.value,
            deviceId = "device-1",
            phoneNumber = "+18005550199"
        )
        db.callLogDao().insertCallLog(existing)

        val duplicate = logger.createCallLog(
            phoneNumber = "+18005550199",
            isOutgoing = false,
            startTime = end.toEpochMilli(),
            endTime = end.plusSeconds(30).toEpochMilli(),
            durationSeconds = 30.0,
            outcome = CallOutcome.BUSY
        )

        assertNull(duplicate)
        assertEquals(1, db.callLogDao().getTotalCount())
    }

    private fun setDatabaseInstance(instance: AppDatabase?) {
        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, instance)
    }
}
