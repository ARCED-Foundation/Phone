package org.fossify.phone.helpers

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.fossify.phone.api.OdkCentralApiClient
import org.fossify.phone.database.dao.CallLogDao
import org.fossify.phone.database.dao.CentralCredentialsDao
import org.fossify.phone.database.dao.PendingSyncDao
import org.fossify.phone.helpers.SyncLogHelper
import org.fossify.phone.extensions.config
import org.fossify.phone.models.CallLog
import org.fossify.phone.models.PendingSync
import org.fossify.phone.models.SyncLogStatus
import org.fossify.phone.services.CallSyncService
import org.fossify.phone.utils.CrashTracker

/**
 * Coordinates syncing CallLog records to ODK Central and manages retry queue.
 */
class CallSyncManager private constructor(
    private val context: Context,
    private val callLogDao: CallLogDao,
    private val centralCredentialsDao: CentralCredentialsDao,
    private val pendingSyncDao: PendingSyncDao,
    private val apiClient: OdkCentralApiClient,
    private val intentExtrasHelper: IntentExtrasHelper
) {
    private val syncLogHelper = SyncLogHelper(context)
    companion object {
        @Volatile
        private var instance: CallSyncManager? = null

        fun getInstance(
            context: Context,
            callLogDao: CallLogDao,
            centralCredentialsDao: CentralCredentialsDao,
            pendingSyncDao: PendingSyncDao,
            apiClient: OdkCentralApiClient,
            intentExtrasHelper: IntentExtrasHelper
        ): CallSyncManager {
            return instance ?: synchronized(this) {
                instance ?: CallSyncManager(
                    context.applicationContext,
                    callLogDao,
                    centralCredentialsDao,
                    pendingSyncDao,
                    apiClient,
                    intentExtrasHelper
                ).also { instance = it }
            }
        }
    }

    suspend fun syncCallLog(callLog: CallLog, intent: Intent): Boolean = withContext(Dispatchers.IO) {
        val intentConfig = intentExtrasHelper.extractOdkConfig(intent)
        val persistedConfig = resolvePersistedConfig()
        val odkConfig = intentConfig ?: persistedConfig
            ?: return@withContext false.also {
                callLogDao.markSyncFailed(callLog.callLogId, "Missing ODK config in intent")
                syncLogHelper.logStatus(callLog, null, SyncLogStatus.FAILED, "Missing ODK config in intent")
                Log.w("CallSyncManager", "Missing ODK config in intent")
            }

        val credentials = centralCredentialsDao.getByUrlAndProject(odkConfig.baseUrl, odkConfig.projectId)
            ?: centralCredentialsDao.getValidatedCredentials()
            ?: return@withContext false.also {
                callLogDao.markSyncFailed(callLog.callLogId, "No credentials for ${odkConfig.baseUrl}")
                syncLogHelper.logStatus(callLog, null, SyncLogStatus.FAILED, "No credentials for ${odkConfig.baseUrl}")
                Log.w("CallSyncManager", "No credentials for ${odkConfig.baseUrl}")
            }

        pendingSyncDao.deleteForCallLog(callLog.callLogId)
        val extras = intentExtrasHelper.getFilteredExtras(context, intent)
            .ifEmpty { intentExtrasHelper.parseExtrasJson(callLog.extrasJson) }
        val payload = createSyncPayload(callLog, extras, odkConfig)
            ?: return@withContext false.also {
                callLogDao.markSyncFailed(callLog.callLogId, "Unable to build sync payload for ${callLog.callLogId}")
                syncLogHelper.logStatus(callLog, null, SyncLogStatus.FAILED, "Unable to build sync payload")
                Log.w("CallSyncManager", "Unable to build sync payload for ${callLog.callLogId}")
            }

        val response = apiClient.sendCallLog(
            payload = payload,
            credentials = credentials
        )

        if (response.success) {
            callLogDao.markAsSynced(callLog.callLogId)
            pendingSyncDao.deleteForCallLog(callLog.callLogId)
            syncLogHelper.logStatus(callLog, payload, SyncLogStatus.SUCCESS, "Synced to Central")
            true
        } else {
            queuePending(callLog, payload, response.error?.toString())
            false
        }
    }

    private suspend fun queuePending(callLog: CallLog, payload: CallSyncPayload, error: String?) {
        val syncPayloadJson = JSONObject(payload.toPersistenceMap()).toString()
        pendingSyncDao.deleteForCallLog(callLog.callLogId)
        val pending = PendingSync(
            callLogId = callLog.callLogId,
            syncPayloadJson = syncPayloadJson,
            retryCount = 0,
            nextRetry = Instant.now(),
            errorType = error?.let { categorizeError(it) }
        )
        pendingSyncDao.insert(pending)
        if (!error.isNullOrBlank()) {
            callLogDao.updateLastSyncError(callLog.callLogId, error)
        }
        syncLogHelper.logStatus(
            callLog = callLog,
            payload = payload,
            status = SyncLogStatus.PENDING,
            message = error?.takeIf { it.isNotBlank() } ?: "Queued for retry"
        )
        scheduleBackgroundSync()
    }

    private fun scheduleBackgroundSync() {
        try {
            val intent = Intent(context, CallSyncService::class.java)
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            CrashTracker.recordSyncException("CallSyncManager.scheduleBackgroundSync", e)
            Log.e("CallSyncManager", "Failed to enqueue background sync", e)
        }
    }

    private fun createSyncPayload(
        callLog: CallLog,
        extras: Map<String, Any>,
        odkConfig: IntentExtrasHelper.OdkConfig
    ): CallSyncPayload? {
        return CallSyncPayloadBuilder.build(callLog, extras, odkConfig)
    }

    private fun resolvePersistedConfig(): IntentExtrasHelper.OdkConfig? {
        val cfg = context.config
        val base = cfg.lastOdkBaseUrl
        val project = cfg.lastOdkProjectId
        val dataset = cfg.lastOdkDataset
        return if (!base.isNullOrBlank() && !project.isNullOrBlank() && !dataset.isNullOrBlank()) {
            IntentExtrasHelper.OdkConfig(base, project, dataset)
        } else {
            null
        }
    }

    private fun categorizeError(message: String): String {
        return when {
            message.contains("401", true) || message.contains("unauthorized", true) -> "authentication"
            message.contains("timeout", true) || message.contains("network", true) -> "network"
            message.contains("429", true) || message.contains("server", true) -> "server"
            else -> "unknown"
        }
    }

    suspend fun getSyncStats(): SyncStats = withContext(Dispatchers.IO) {
        val pending = pendingSyncDao.count()
        val synced = callLogDao.getSyncedCount()
        val total = callLogDao.getTotalCount()
        SyncStats(
            total = total,
            synced = synced,
            pending = pending,
            failed = total - synced - pending
        )
    }

    data class SyncStats(
        val total: Int,
        val synced: Int,
        val pending: Int,
        val failed: Int
    )
}
