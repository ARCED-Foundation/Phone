package org.fossify.phone.helpers

import android.content.Context
import androidx.core.content.ContextCompat
import org.json.JSONObject
import org.fossify.phone.database.AppDatabase
import org.fossify.phone.extensions.config
import org.fossify.phone.helpers.IntentExtrasHelper.OdkConfig
import org.fossify.phone.models.CallLog
import org.fossify.phone.models.CallOutcome
import org.fossify.phone.models.PendingSync
import org.fossify.phone.models.SyncLogStatus
import org.fossify.phone.services.CallSyncService
import org.fossify.phone.utils.CallDetectionAnalytics
import org.fossify.phone.utils.CrashTracker
import org.fossify.phone.utils.TimestampUtils
import org.fossify.phone.utils.UuidUtils
import org.fossify.phone.work.WorkManagerHelper
import java.time.Instant
import java.util.UUID
import kotlin.math.max

class CallLogger(private val context: Context) {
    private val db by lazy { AppDatabase.getInstance(context) }
    private val syncLogHelper by lazy { SyncLogHelper(context) }
    private val syncLogDao by lazy { AppDatabase.getInstance(context).syncLogDao() }

    suspend fun createCallLog(
        phoneNumber: String,
        isOutgoing: Boolean,
        startTime: Long,
        endTime: Long,
        durationSeconds: Double,
        outcome: CallOutcome,
        outcomeDetail: String? = null,
        instanceId: String? = null,
        enumeratorId: String? = null,
        skipDuplicateCheck: Boolean = false,
        isOdkCall: Boolean = false
    ): CallLog? {
        // Duplicate prevention: check for recent call within 2 minutes unless explicitly skipped (ODK)
        if (!skipDuplicateCheck) {
            val windowStart = endTime - 120000L // 2 minutes
            try {
                val recentCount = db.callLogDao().getRecentCallCount(phoneNumber, windowStart)
                if (recentCount > 0) {
                    org.fossify.phone.utils.Logger.callDetection("Duplicate call log skipped for $phoneNumber (recent count: $recentCount)")
                    return null
                }
            } catch (e: Exception) {
                CrashTracker.recordDatabaseException("CallLogger.duplicateCheck", e)
                org.fossify.phone.utils.Logger.callDetection("Error checking duplicates for $phoneNumber: ${e.message}")
            }
        }

        val direction = if (isOutgoing) "outgoing" else "incoming"
        val deviceId = "device_${android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)}" // Placeholder device ID
        val intentState = OdkIntentStateHolder.current()

        val startInstant = Instant.ofEpochMilli(startTime)
        val endInstant = Instant.ofEpochMilli(endTime)
        val measuredDuration = TimestampUtils.calculateDurationSeconds(startInstant, endInstant)
        val duration = max(measuredDuration, durationSeconds)

        val callLog = CallLog(
            callLogId = UuidUtils.generateCallLogId(),
            direction = direction,
            callStartUtc = startInstant,
            callEndUtc = endInstant,
            durationSeconds = duration,
            outcome = outcome.value,
            outcomeDetail = outcomeDetail,
            deviceId = deviceId,
            instanceId = instanceId ?: intentState.config?.instanceId,
            phoneNumber = phoneNumber,
            enumeratorId = enumeratorId,
            extrasJson = JSONObject(intentState.extras).toString(),
            synced = false,
            syncAttempts = 0
        )

        return try {
            db.callLogDao().insertCallLog(callLog)
            org.fossify.phone.utils.Logger.callDetection("Call log created successfully: ${callLog.callLogId}")

            // Create pending sync record for the new call log
            createPendingSync(callLog, isOdkCall)

            callLog
        } catch (e: Exception) {
            CallDetectionAnalytics.recordFailure(context, "call_logger_insert", e.message)
            CrashTracker.recordDatabaseException("CallLogger.insert", e)
            org.fossify.phone.utils.Logger.callDetection("Failed to create call log ${callLog.callLogId}: ${e.message}")
            null
        }
    }

    private suspend fun createPendingSync(callLog: CallLog, isOdkCall: Boolean) {
        try {
            if (callLog.synced) return
            db.pendingSyncDao().deleteForCallLog(callLog.callLogId)
            val syncPayload = createSyncPayload(callLog)
            if (syncPayload == null) {
                if (isOdkCall) {
                    db.callLogDao().updateLastSyncError(callLog.callLogId, "Missing ODK configuration or payload")
                    syncLogHelper.logStatus(callLog, null, SyncLogStatus.FAILED, "Missing ODK configuration or payload")
                }
                // For non-ODK calls, wait for form/config to create pending
                return
            }

            val pendingSync = PendingSync(
                callLogId = callLog.callLogId,
                syncPayloadJson = JSONObject(syncPayload.toPersistenceMap()).toString()
            )

            db.pendingSyncDao().insert(pendingSync)
            syncLogHelper.logStatus(callLog, syncPayload, SyncLogStatus.PENDING, "Queued for sync")
            // Ensure background work kicks off immediately, even on low battery
            WorkManagerHelper.enqueueOdkSyncWork(context, initialDelayMs = 0L, forceNow = true)
            ContextCompat.startForegroundService(context, android.content.Intent(context, CallSyncService::class.java))
        } catch (e: Exception) {
            CrashTracker.recordDatabaseException("CallLogger.pendingSyncInsert", e)
            android.util.Log.e("CallLogger", "Failed to create pending sync for call log ${callLog.callLogId}: ${e.message}")
            db.callLogDao().updateLastSyncError(callLog.callLogId, "Failed to queue sync: ${e.message}")
            syncLogHelper.logStatus(callLog, null, SyncLogStatus.FAILED, "Failed to queue sync: ${e.message}")
        }
    }

    private fun createSyncPayload(callLog: CallLog): CallSyncPayload? {
        val state = OdkIntentStateHolder.current()
        val config = state.config ?: resolvePersistedConfig()
        return config?.let { CallSyncPayloadBuilder.build(callLog, state.extras, it) }
    }

    private fun resolvePersistedConfig(): OdkConfig? {
        val cfg = context.config
        val base = cfg.lastOdkBaseUrl
        val project = cfg.lastOdkProjectId
        val dataset = cfg.lastOdkDataset
        if (!base.isNullOrBlank() && !project.isNullOrBlank() && !dataset.isNullOrBlank()) {
            return OdkConfig(base, project, dataset)
        }

        val lastSuccess = runCatching { syncLogDao.getLastSuccessfulConfig() }.getOrNull()
        return lastSuccess?.let {
            cfg.lastOdkBaseUrl = it.baseUrl
            cfg.lastOdkProjectId = it.projectId
            cfg.lastOdkDataset = it.datasetName
            OdkConfig(it.baseUrl, it.projectId, it.datasetName)
        }
    }
}
