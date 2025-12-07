@file:UseSerializers(UUIDSerializer::class, InstantSerializer::class)

package org.fossify.phone.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.fossify.phone.serialization.InstantSerializer
import org.fossify.phone.serialization.UUIDSerializer
import org.fossify.phone.utils.TimestampUtils
import org.fossify.phone.utils.UuidUtils

@Entity(
    tableName = "sync_logs",
    indices = [
        Index(value = ["created_at"], name = "idx_sync_logs_created_at"),
        Index(value = ["call_log_id"], name = "idx_sync_logs_call_log_id")
    ]
)
@Serializable
data class SyncLogEntry(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: UUID = UuidUtils.generateCallLogId(),

    @ColumnInfo(name = "call_log_id")
    val callLogId: UUID? = null,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "message")
    val message: String? = null,

    @ColumnInfo(name = "phone_number")
    val phoneNumber: String? = null,

    @ColumnInfo(name = "direction")
    val direction: String? = null,

    @ColumnInfo(name = "dataset")
    val datasetName: String? = null,

    @ColumnInfo(name = "project_id")
    val projectId: String? = null,

    @ColumnInfo(name = "base_url")
    val baseUrl: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant = TimestampUtils.now(),

    // CallLog snapshot fields
    @ColumnInfo(name = "call_start_utc")
    val callStartUtc: Instant? = null,

    @ColumnInfo(name = "call_end_utc")
    val callEndUtc: Instant? = null,

    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Double? = null,

    @ColumnInfo(name = "outcome")
    val outcome: String? = null,

    @ColumnInfo(name = "outcome_detail")
    val outcomeDetail: String? = null,

    @ColumnInfo(name = "instance_id")
    val instanceId: String? = null,

    @ColumnInfo(name = "attempt_number")
    val attemptNumber: Int? = null,

    @ColumnInfo(name = "enumerator_id")
    val enumeratorId: String? = null,

    @ColumnInfo(name = "survey_id")
    val surveyId: String? = null,

    @ColumnInfo(name = "additional_notes")
    val additionalNotes: String? = null,

    @ColumnInfo(name = "extras_json")
    val extrasJson: String? = null,

    @ColumnInfo(name = "synced_flag")
    val syncedFlag: Boolean? = null,

    @ColumnInfo(name = "sync_attempts")
    val syncAttempts: Int? = null,

    @ColumnInfo(name = "last_sync_error")
    val lastSyncError: String? = null
)

enum class SyncLogStatus {
    PENDING,
    SUCCESS,
    FAILED
}
