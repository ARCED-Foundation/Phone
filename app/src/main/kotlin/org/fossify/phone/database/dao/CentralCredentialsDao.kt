package org.fossify.phone.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import org.fossify.phone.models.CentralCredentials

@Dao
interface CentralCredentialsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(credentials: CentralCredentials)

    @Query("SELECT * FROM central_credentials LIMIT 1")
    suspend fun getCredentials(): CentralCredentials?

    @Query("SELECT * FROM central_credentials WHERE validated = 1 LIMIT 1")
    suspend fun getValidatedCredentials(): CentralCredentials?

    @Query("SELECT * FROM central_credentials WHERE central_url = :centralUrl AND project_id = :projectId LIMIT 1")
    suspend fun getByUrlAndProject(centralUrl: String, projectId: String): CentralCredentials?

    @Update
    suspend fun update(credentials: CentralCredentials)

    @Query("DELETE FROM central_credentials")
    suspend fun clear()

    @Query("UPDATE central_credentials SET validated = :validated, last_validation = :lastValidation")
    suspend fun updateValidation(validated: Boolean, lastValidation: Long?)
}
