@file:UseSerializers(UUIDSerializer::class, InstantSerializer::class)

package org.fossify.phone.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.fossify.phone.utils.UuidUtils
import org.fossify.phone.serialization.InstantSerializer
import org.fossify.phone.serialization.UUIDSerializer

@Entity(
    tableName = "pending_sync",
    foreignKeys = [
        ForeignKey(
            entity = CallLog::class,
            parentColumns = ["call_log_id"],
            childColumns = ["call_log_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["call_log_id"], name = "idx_pending_sync_call_log"),
        Index(value = ["next_retry"], name = "idx_pending_sync_next_retry")
    ]
)
@Serializable
data class PendingSync(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: UUID = UuidUtils.generateCallLogId(),

    @ColumnInfo(name = "call_log_id")
    val callLogId: UUID,

    @ColumnInfo(name = "sync_payload_json")
    val syncPayloadJson: String,

    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    @ColumnInfo(name = "next_retry")
    val nextRetry: Instant? = null,

    @ColumnInfo(name = "error_type")
    val errorType: String? = null
) {
    fun withRetry(nextRetry: Instant, errorType: String?): PendingSync =
        copy(retryCount = retryCount + 1, nextRetry = nextRetry, errorType = errorType)

    fun reset(): PendingSync = copy(retryCount = 0, nextRetry = null, errorType = null)
}
