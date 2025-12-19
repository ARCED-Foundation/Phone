package org.fossify.phone.testutil

import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.sqlite.db.SupportSQLiteOpenHelper
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.util.UUID
import org.fossify.phone.database.AppDatabase
import org.fossify.phone.database.dao.CallLogDao
import org.fossify.phone.database.dao.CentralCredentialsDao
import org.fossify.phone.database.dao.PartialSurveyDataDao
import org.fossify.phone.database.dao.PendingSyncDao
import org.fossify.phone.models.CallLog
import org.fossify.phone.models.CentralCredentials
import org.fossify.phone.models.PartialSurveyData
import org.fossify.phone.models.PendingSync

class FakeAppDatabase(
    private val callLogs: InMemoryCallLogDao = InMemoryCallLogDao(),
    private val credentials: InMemoryCentralCredentialsDao = InMemoryCentralCredentialsDao(),
    private val pendingSyncs: InMemoryPendingSyncDao = InMemoryPendingSyncDao(),
    private val partialSurveys: InMemoryPartialSurveyDataDao = InMemoryPartialSurveyDataDao()
) : AppDatabase() {
    override fun callLogDao(): CallLogDao = callLogs
    override fun centralCredentialsDao(): CentralCredentialsDao = credentials
    override fun pendingSyncDao(): PendingSyncDao = pendingSyncs
    override fun partialSurveyDataDao(): PartialSurveyDataDao = partialSurveys

    override fun clearAllTables() {
        callLogs.clear()
        pendingSyncs.clear()
        partialSurveys.clear()
        kotlinx.coroutines.runBlocking { credentials.clear() }
    }

    override fun createOpenHelper(config: DatabaseConfiguration): SupportSQLiteOpenHelper {
        throw UnsupportedOperationException("Not required for in-memory fakes")
    }

    override fun createInvalidationTracker(): InvalidationTracker {
        return InvalidationTracker(
            this,
            "call_logs",
            "central_credentials",
            "pending_sync",
            "partial_survey_data"
        )
    }

    override fun close() {
        clearAllTables()
    }
}

class InMemoryCallLogDao : CallLogDao {
    private val items = mutableListOf<CallLog>()

    override suspend fun insertCallLog(callLog: CallLog) {
        items.removeAll { it.callLogId == callLog.callLogId }
        items.add(callLog)
    }

    override suspend fun getById(id: UUID): CallLog? = items.firstOrNull { it.callLogId == id }

    override suspend fun getRecentCallCount(phoneNumber: String, windowStart: Long, isOdkCall: Boolean): Int {
        return items.count {
            it.phoneNumber == phoneNumber &&
                it.callStartUtc.toEpochMilli() >= windowStart &&
                it.isOdkCall == isOdkCall
        }
    }

    override suspend fun markAsSynced(id: UUID) {
        val current = getById(id) ?: return
        insertCallLog(current.copy(synced = true, lastSyncError = null))
    }

    override suspend fun markSyncFailed(id: UUID, error: String?) {
        val current = getById(id) ?: return
        insertCallLog(
            current.copy(
                synced = false,
                syncAttempts = current.syncAttempts + 1,
                lastSyncError = error
            )
        )
    }

    override suspend fun getUnsynced(): List<CallLog> =
        items.filter { !it.synced }.sortedBy { it.callStartUtc }

    override suspend fun getSyncedCount(): Int = items.count { it.synced }

    override suspend fun getTotalCount(): Int = items.size

    override suspend fun deleteSyncedBefore(cutoff: Instant): Int {
        val before = items.size
        items.removeAll { it.synced && it.callStartUtc.isBefore(cutoff) }
        return before - items.size
    }

    override suspend fun updateSurveyData(id: UUID, surveyId: String?, additionalNotes: String?) {
        val current = getById(id) ?: return
        insertCallLog(
            current.copy(
                surveyId = surveyId,
                additionalNotes = additionalNotes,
                synced = false,
                lastSyncError = null
            )
        )
    }

    override suspend fun updatePostCallMetadata(
        id: UUID,
        enumeratorId: String?,
        surveyId: String?,
        additionalNotes: String?,
        extrasJson: String
    ) {
        val current = getById(id) ?: return
        insertCallLog(
            current.copy(
                enumeratorId = enumeratorId,
                surveyId = surveyId,
                additionalNotes = additionalNotes,
                extrasJson = extrasJson,
                synced = false,
                lastSyncError = null
            )
        )
    }

    override fun observeLatestSyncError() = flowOf(items.maxByOrNull { it.callStartUtc }?.lastSyncError)

    override suspend fun updateLastSyncError(id: UUID, error: String?) {
        val current = getById(id) ?: return
        insertCallLog(current.copy(lastSyncError = error))
    }

    override suspend fun getCallNeedingForm(): CallLog? =
        items.filter { !it.isOdkCall && !it.formCompleted && !it.synced }.minByOrNull { it.callStartUtc }

    override suspend fun markFormCompleted(id: UUID) {
        val current = getById(id) ?: return
        insertCallLog(current.copy(formCompleted = true))
    }

    override suspend fun markFormAttempted(id: UUID) {
        val current = getById(id) ?: return
        insertCallLog(current.copy(formAttempted = true))
    }

    override suspend fun getCallWithIncompleteForm(): CallLog? =
        items.filter { !it.isOdkCall && !it.formCompleted && it.formAttempted && !it.synced }
            .minByOrNull { it.callStartUtc }

    override suspend fun resetFormState(id: UUID) {
        val current = getById(id) ?: return
        insertCallLog(current.copy(formAttempted = false, formCompleted = false))
    }

    fun clear() = items.clear()
}

class InMemoryCentralCredentialsDao : CentralCredentialsDao {
    private var stored: CentralCredentials? = null

    override suspend fun insert(credentials: CentralCredentials) {
        stored = credentials.copy(id = 1)
    }

    override suspend fun getCredentials(): CentralCredentials? = stored

    override suspend fun getValidatedCredentials(): CentralCredentials? = stored?.takeIf { it.validated }

    override suspend fun getByUrlAndProject(centralUrl: String, projectId: String): CentralCredentials? {
        return stored?.takeIf { it.centralUrl == centralUrl && it.projectId == projectId }
    }

    override suspend fun update(credentials: CentralCredentials) {
        stored = credentials
    }

    override suspend fun clear() {
        stored = null
    }

    override suspend fun updateValidation(validated: Boolean, lastValidation: Long?) {
        stored = stored?.copy(validated = validated, lastValidation = lastValidation)
    }
}

class InMemoryPendingSyncDao : PendingSyncDao {
    private val items = mutableListOf<PendingSync>()

    override suspend fun insert(pendingSync: PendingSync) {
        items.removeAll { it.id == pendingSync.id }
        items.add(pendingSync)
    }

    override suspend fun update(pendingSync: PendingSync) {
        insert(pendingSync)
    }

    override suspend fun delete(id: UUID) {
        items.removeAll { it.id == id }
    }

    override suspend fun deleteForCallLog(callLogId: UUID) {
        items.removeAll { it.callLogId == callLogId }
    }

    override suspend fun getByCallLog(callLogId: UUID): PendingSync? =
        items.firstOrNull { it.callLogId == callLogId }

    override suspend fun getReadyForSync(now: Instant, limit: Int): List<PendingSync> {
        return items
            .filter { it.nextRetry == null || !it.nextRetry!!.isAfter(now) }
            .sortedWith(compareBy<PendingSync> { it.retryCount }.thenBy { it.nextRetry ?: Instant.EPOCH })
            .take(limit)
    }

    override suspend fun count(): Int = items.size

    override fun observePendingCount() = flowOf(items.size)

    override suspend fun getEarliestNextRetry(): Instant? =
        items.mapNotNull { it.nextRetry }.minOrNull()

    override suspend fun resetAllForImmediateSync(now: Instant) {
        items.replaceAll { it.copy(nextRetry = now, retryCount = 0, errorType = null) }
    }

    fun clear() = items.clear()
}

class InMemoryPartialSurveyDataDao : PartialSurveyDataDao {
    private val items = mutableListOf<PartialSurveyData>()

    override suspend fun insert(data: PartialSurveyData) {
        items.removeAll { it.callLogId == data.callLogId }
        items.add(data)
    }

    override suspend fun getByCallLog(callLogId: UUID): PartialSurveyData? =
        items.firstOrNull { it.callLogId == callLogId }

    override suspend fun deleteForCallLog(callLogId: UUID) {
        items.removeAll { it.callLogId == callLogId }
    }

    override suspend fun getOldestIncomplete(): PartialSurveyData? =
        items.filter { !it.isComplete }.minByOrNull { it.createdAt }

    fun clear() = items.clear()
}
