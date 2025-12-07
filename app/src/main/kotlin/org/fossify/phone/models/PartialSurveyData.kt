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
    tableName = "partial_survey_data",
    foreignKeys = [
        ForeignKey(
            entity = CallLog::class,
            parentColumns = ["call_log_id"],
            childColumns = ["call_log_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["call_log_id"], name = "idx_partial_survey_call_log"),
        Index(value = ["is_complete"], name = "idx_partial_survey_complete")
    ]
)
@Serializable
data class PartialSurveyData(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: UUID = UuidUtils.generatePartialSurveyDataId(),

    @ColumnInfo(name = "call_log_id")
    val callLogId: UUID,

    @ColumnInfo(name = "survey_id")
    val surveyId: String? = null,

    @ColumnInfo(name = "additional_notes")
    val additionalNotes: String? = null,

    @ColumnInfo(name = "is_complete")
    val isComplete: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Instant.now()
)
