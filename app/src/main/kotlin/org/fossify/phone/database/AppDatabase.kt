package org.fossify.phone.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.fossify.phone.database.dao.CallLogDao
import org.fossify.phone.database.dao.CentralCredentialsDao
import org.fossify.phone.database.dao.PartialSurveyDataDao
import org.fossify.phone.database.dao.PendingSyncDao
import org.fossify.phone.database.dao.SyncLogDao
import org.fossify.phone.models.CallLog
import org.fossify.phone.models.CentralCredentials
import org.fossify.phone.models.PartialSurveyData
import org.fossify.phone.models.PendingSync
import org.fossify.phone.models.SyncLogEntry

@Database(
    entities = [
        CallLog::class,
        CentralCredentials::class,
        PendingSync::class,
        PartialSurveyData::class,
        SyncLogEntry::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(AppTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun callLogDao(): CallLogDao
    abstract fun centralCredentialsDao(): CentralCredentialsDao
    abstract fun pendingSyncDao(): PendingSyncDao
    abstract fun partialSurveyDataDao(): PartialSurveyDataDao
    abstract fun syncLogDao(): SyncLogDao

    companion object {
        private const val DATABASE_NAME = "phone.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
