package org.fossify.phone.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import java.util.UUID
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import org.fossify.phone.models.PendingSync

@Dao
interface PendingSyncDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pendingSync: PendingSync)

    @Update
    suspend fun update(pendingSync: PendingSync)

    @Query("DELETE FROM pending_sync WHERE id = :id")
    suspend fun delete(id: UUID)

    @Query("DELETE FROM pending_sync WHERE call_log_id = :callLogId")
    suspend fun deleteForCallLog(callLogId: UUID)

    @Query("SELECT * FROM pending_sync WHERE call_log_id = :callLogId LIMIT 1")
    suspend fun getByCallLog(callLogId: UUID): PendingSync?

    @Query("SELECT * FROM pending_sync WHERE next_retry IS NULL OR next_retry <= :now ORDER BY retry_count ASC LIMIT :limit")
    suspend fun getReadyForSync(now: Instant, limit: Int = 20): List<PendingSync>

    @Query("SELECT COUNT(*) FROM pending_sync")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM pending_sync")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT MIN(next_retry) FROM pending_sync WHERE next_retry IS NOT NULL")
    suspend fun getEarliestNextRetry(): Instant?

    @Query("UPDATE pending_sync SET next_retry = :now, retry_count = 0")
    suspend fun resetAllForImmediateSync(now: Instant)
}
