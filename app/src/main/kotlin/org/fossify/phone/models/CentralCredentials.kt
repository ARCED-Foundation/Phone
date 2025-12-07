package org.fossify.phone.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "central_credentials")
@Serializable
data class CentralCredentials(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "central_url")
    val centralUrl: String,

    @ColumnInfo(name = "project_id")
    val projectId: String,

    @ColumnInfo(name = "username")
    val username: String,

    @ColumnInfo(name = "password_hash")
    val passwordHash: String,

    @ColumnInfo(name = "validated")
    val validated: Boolean = false,

    @ColumnInfo(name = "last_validation")
    val lastValidation: Long? = null
)
