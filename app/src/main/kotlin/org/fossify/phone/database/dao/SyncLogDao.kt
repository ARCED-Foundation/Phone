package org.fossify.phone.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import org.fossify.phone.models.SyncLogEntry

@Dao
interface SyncLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: SyncLogEntry)

    @Query("DELETE FROM sync_logs WHERE created_at < :cutoff")
    suspend fun deleteOlderThan(cutoff: Instant): Int

    @Query("DELETE FROM sync_logs WHERE call_log_id = :callLogId AND status = :status")
    suspend fun deleteByStatus(callLogId: UUID, status: String)

    @Query("SELECT * FROM sync_logs WHERE created_at >= :cutoff ORDER BY created_at DESC")
    fun observeSince(cutoff: Instant): Flow<List<SyncLogEntry>>

    @Query("SELECT status FROM sync_logs WHERE call_log_id = :callLogId ORDER BY created_at DESC LIMIT 1")
    suspend fun getLastStatus(callLogId: UUID): String?

    @Query(
        """
        SELECT base_url AS baseUrl, project_id AS projectId, dataset AS datasetName
        FROM sync_logs
        WHERE status = :status
          AND base_url IS NOT NULL
          AND project_id IS NOT NULL
          AND dataset IS NOT NULL
        ORDER BY created_at DESC
        LIMIT 1
        """
    )
    fun getLastSuccessfulConfig(status: String = "SUCCESS"): LastSuccessfulConfig?
}
