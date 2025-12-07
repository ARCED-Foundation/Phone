package org.fossify.phone.helpers

import android.content.Context
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.fossify.phone.database.AppDatabase
import org.fossify.phone.extensions.config
import org.fossify.phone.models.CallLog
import org.fossify.phone.models.SyncLogEntry
import org.fossify.phone.models.SyncLogStatus

class SyncLogHelper(private val context: Context) {
    private val db by lazy { AppDatabase.getInstance(context) }
    private val syncLogDao by lazy { db.syncLogDao() }

    suspend fun logStatus(
        callLog: CallLog?,
        payload: CallSyncPayload?,
        status: SyncLogStatus,
        message: String? = null
    ) = withContext(Dispatchers.IO) {
        if (callLog?.callLogId != null) {
            val last = syncLogDao.getLastStatus(callLog.callLogId)
            // Skip duplicate status for same call unless transitioning to SUCCESS/FAILED
            if (last == status.name && status == SyncLogStatus.PENDING) return@withContext
        }
        val entry = SyncLogEntry(
            callLogId = callLog?.callLogId,
            status = status.name,
            message = message,
            phoneNumber = callLog?.phoneNumber,
            direction = callLog?.direction,
            datasetName = payload?.datasetName,
            projectId = payload?.projectId,
            baseUrl = payload?.baseUrl,
            callStartUtc = callLog?.callStartUtc,
            callEndUtc = callLog?.callEndUtc,
            durationSeconds = callLog?.durationSeconds,
            outcome = callLog?.outcome,
            outcomeDetail = callLog?.outcomeDetail,
            instanceId = callLog?.instanceId,
            attemptNumber = callLog?.attemptNumber,
            enumeratorId = callLog?.enumeratorId,
            surveyId = callLog?.surveyId,
            additionalNotes = callLog?.additionalNotes,
            extrasJson = callLog?.extrasJson,
            syncedFlag = callLog?.synced,
            syncAttempts = callLog?.syncAttempts,
            lastSyncError = callLog?.lastSyncError
        )
        syncLogDao.insert(entry)
        if (status == SyncLogStatus.SUCCESS && callLog?.callLogId != null) {
            // Remove stale "pending" entries for this call once we have a definitive success.
            syncLogDao.deleteByStatus(callLog.callLogId, SyncLogStatus.PENDING.name)
        }
        cleanupOldEntries()
    }

    fun observeRecentLogs(): Flow<List<SyncLogEntry>> {
        return syncLogDao.observeSince(currentCutoff())
            .map { logs -> logs.sortedByDescending { it.createdAt } }
    }

    suspend fun cleanupOldEntries() = withContext(Dispatchers.IO) {
        syncLogDao.deleteOlderThan(currentCutoff())
    }

    private fun currentCutoff(): Instant {
        val days = context.config.syncLogRetentionDays.coerceAtLeast(1)
        return Instant.now().minus(Duration.ofDays(days.toLong()))
    }
}
