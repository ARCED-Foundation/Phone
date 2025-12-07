package org.fossify.phone.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import org.fossify.phone.models.CallLog

@Dao
interface CallLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLog(callLog: CallLog)

    @Query("SELECT * FROM call_logs WHERE call_log_id = :id LIMIT 1")
    suspend fun getById(id: UUID): CallLog?

    @Query("SELECT COUNT(*) FROM call_logs WHERE phone_number = :phoneNumber AND call_start_utc >= :windowStart")
    suspend fun getRecentCallCount(phoneNumber: String, windowStart: Long): Int

    @Query("UPDATE call_logs SET synced = 1, last_sync_error = NULL WHERE call_log_id = :id")
    suspend fun markAsSynced(id: UUID)

    @Query("UPDATE call_logs SET synced = 0, sync_attempts = sync_attempts + 1, last_sync_error = :error WHERE call_log_id = :id")
    suspend fun markSyncFailed(id: UUID, error: String?)

    @Query("SELECT * FROM call_logs WHERE synced = 0 ORDER BY call_start_utc ASC")
    suspend fun getUnsynced(): List<CallLog>

    @Query("SELECT COUNT(*) FROM call_logs WHERE synced = 1")
    suspend fun getSyncedCount(): Int

    @Query("SELECT COUNT(*) FROM call_logs")
    suspend fun getTotalCount(): Int

    @Query("DELETE FROM call_logs WHERE synced = 1 AND call_start_utc < :cutoff")
    suspend fun deleteSyncedBefore(cutoff: Instant): Int

    @Query(
        """
        UPDATE call_logs
        SET survey_id = :surveyId,
            additional_notes = :additionalNotes,
            synced = 0,
            last_sync_error = NULL
        WHERE call_log_id = :id
        """
    )
    suspend fun updateSurveyData(id: UUID, surveyId: String?, additionalNotes: String?)

    @Query(
        """
        UPDATE call_logs
        SET enumerator_id = :enumeratorId,
            survey_id = :surveyId,
            additional_notes = :additionalNotes,
            extras_json = :extrasJson,
            synced = 0,
            last_sync_error = NULL
        WHERE call_log_id = :id
        """
    )
    suspend fun updatePostCallMetadata(
        id: UUID,
        enumeratorId: String?,
        surveyId: String?,
        additionalNotes: String?,
        extrasJson: String
    )

    @Query("SELECT last_sync_error FROM call_logs ORDER BY call_start_utc DESC LIMIT 1")
    fun observeLatestSyncError(): Flow<String?>

    @Query("UPDATE call_logs SET last_sync_error = :error WHERE call_log_id = :id")
    suspend fun updateLastSyncError(id: UUID, error: String?)
}
