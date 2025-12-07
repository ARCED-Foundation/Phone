package org.fossify.phone.helpers

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import org.fossify.phone.database.AppDatabase

object SyncStatusTracker {
    data class SyncStatus(val pendingCount: Int, val lastError: String?)

    fun create(context: Context): Flow<SyncStatus> {
        val db = AppDatabase.getInstance(context)
        return combine(
            db.pendingSyncDao().observePendingCount(),
            db.callLogDao().observeLatestSyncError()
        ) { pending, error ->
            SyncStatus(pending, error)
        }.distinctUntilChanged()
    }
}
