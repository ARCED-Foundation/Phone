package org.fossify.phone.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.util.UUID
import org.fossify.phone.models.PartialSurveyData

@Dao
interface PartialSurveyDataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(data: PartialSurveyData)

    @Query("SELECT * FROM partial_survey_data WHERE call_log_id = :callLogId LIMIT 1")
    suspend fun getByCallLog(callLogId: UUID): PartialSurveyData?

    @Query("DELETE FROM partial_survey_data WHERE call_log_id = :callLogId")
    suspend fun deleteForCallLog(callLogId: UUID)

    @Query("SELECT * FROM partial_survey_data WHERE is_complete = 0 ORDER BY created_at ASC LIMIT 1")
    suspend fun getOldestIncomplete(): PartialSurveyData?
}
