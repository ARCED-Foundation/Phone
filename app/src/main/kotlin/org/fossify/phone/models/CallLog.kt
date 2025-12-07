@file:UseSerializers(UUIDSerializer::class, InstantSerializer::class)

package org.fossify.phone.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant
import java.util.UUID
import org.fossify.phone.utils.TimestampUtils
import org.fossify.phone.utils.UuidUtils
import org.fossify.phone.serialization.InstantSerializer
import org.fossify.phone.serialization.UUIDSerializer

@Entity(
    tableName = "call_logs",
    indices = [
        Index(value = ["synced"], name = "idx_call_logs_synced"),
        Index(value = ["call_start_utc"], name = "idx_call_logs_start")
    ]
)
@Serializable
data class CallLog(
    @PrimaryKey
    @ColumnInfo(name = "call_log_id")
    val callLogId: UUID = UuidUtils.generateCallLogId(),

    @ColumnInfo(name = "direction")
    val direction: String,

    @ColumnInfo(name = "call_start_utc")
    val callStartUtc: Instant = TimestampUtils.now(),

    @ColumnInfo(name = "call_end_utc")
    val callEndUtc: Instant = TimestampUtils.now(),

    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Double = 0.0,

    @ColumnInfo(name = "outcome")
    val outcome: String = CallOutcome.NO_ANSWER.value,

    @ColumnInfo(name = "outcome_detail")
    val outcomeDetail: String? = null,

    @ColumnInfo(name = "device_id")
    val deviceId: String,

    @ColumnInfo(name = "instance_id")
    val instanceId: String? = null,

    @ColumnInfo(name = "phone_number")
    val phoneNumber: String? = null,

    @ColumnInfo(name = "attempt_number")
    val attemptNumber: Int? = null,

    @ColumnInfo(name = "enumerator_id")
    val enumeratorId: String? = null,

    @ColumnInfo(name = "extras_json")
    val extrasJson: String = "{}",

    @ColumnInfo(name = "survey_id")
    val surveyId: String? = null,

    @ColumnInfo(name = "additional_notes")
    val additionalNotes: String? = null,

    @ColumnInfo(name = "synced")
    val synced: Boolean = false,

    @ColumnInfo(name = "sync_attempts")
    val syncAttempts: Int = 0,

    @ColumnInfo(name = "last_sync_error")
    val lastSyncError: String? = null
) {
    init {
        require(durationSeconds >= 0) { "Duration must be non-negative" }
        require(CallOutcome.entries.any { it.value == outcome }) { "Invalid call outcome: $outcome" }
        require(callStartUtc <= callEndUtc) { "Call start time must be before or equal to end time" }
        require(direction in listOf("incoming", "outgoing")) { "Direction must be 'incoming' or 'outgoing'" }
    }

    fun withOutcome(outcome: CallOutcome, detail: String? = null): CallLog =
        copy(outcome = outcome.value, outcomeDetail = detail)

    fun withTimestamps(start: Instant, end: Instant): CallLog =
        copy(
            callStartUtc = start,
            callEndUtc = end,
            durationSeconds = TimestampUtils.calculateDurationSeconds(start, end)
        )

    fun needsSync(maxAttempts: Int = 3): Boolean = !synced && syncAttempts < maxAttempts
}
