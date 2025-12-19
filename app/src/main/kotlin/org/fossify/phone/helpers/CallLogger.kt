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
import org.fossify.phone.helpers.OdkIntentStateHolder
import org.fossify.phone.helpers.OdkIntentState

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
        isOdkCall: Boolean = false,
        deferSync: Boolean = false,
        intentState: OdkIntentState? = null
    ): CallLog? {
        // Duplicate prevention: check for recent call within 2 minutes unless explicitly skipped (ODK)
        val stateSnapshot = intentState ?: OdkIntentStateHolder.current()
        if (!skipDuplicateCheck) {
            val windowStart = endTime - 120000L // 2 minutes
            try {
                val recentCount = db.callLogDao().getRecentCallCount(phoneNumber, windowStart, isOdkCall)
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
            instanceId = instanceId ?: stateSnapshot.config?.instanceId,
            phoneNumber = phoneNumber,
            enumeratorId = enumeratorId,
            extrasJson = JSONObject(stateSnapshot.extras).toString(),
            synced = false,
            syncAttempts = 0
        )
        callLog.isOdkCall = isOdkCall
        callLog.formCompleted = isOdkCall
        callLog.syncDeferred = deferSync || (!isOdkCall && !callLog.formCompleted)

        return try {
            db.callLogDao().insertCallLog(callLog)
            org.fossify.phone.utils.Logger.callDetection("Call log created successfully: ${callLog.callLogId}")

            // Create pending sync record unless deferred
            if (!deferSync) {
                createPendingSync(callLog, isOdkCall, stateSnapshot)
            } else {
                org.fossify.phone.utils.Logger.callDetection("Sync deferred for ODK call: ${callLog.callLogId}")
            }

            callLog
        } catch (e: Exception) {
            CallDetectionAnalytics.recordFailure(context, "call_logger_insert", e.message)
            CrashTracker.recordDatabaseException("CallLogger.insert", e)
            org.fossify.phone.utils.Logger.callDetection("Failed to create call log ${callLog.callLogId}: ${e.message}")
            null
        }
    }

    suspend fun createPendingSync(callLog: CallLog, isOdkCall: Boolean, intentState: OdkIntentState? = null) {
        try {
            if (callLog.synced) return
            val stateSnapshot = intentState ?: OdkIntentStateHolder.current()

            // Check if sync already exists to prevent duplicates
            val existingSync = db.pendingSyncDao().getByCallLog(callLog.callLogId)
            if (existingSync != null) {
                org.fossify.phone.utils.Logger.callDetection("Sync already exists for call ${callLog.callLogId}, skipping duplicate creation")
                return
            }

            db.pendingSyncDao().deleteForCallLog(callLog.callLogId)
            val syncPayload = createSyncPayload(callLog, stateSnapshot)

            // If sync payload is null, handle fallback creation for both ODK and non-ODK calls
            if (syncPayload == null) {
                if (isOdkCall) {
                    val state = OdkIntentStateHolder.current()
                    // For ODK calls, try to create at least a basic sync with fallback
                    val hasOdkIndicators = state.extras["odk_call"]?.toString()?.toBooleanStrictOrNull() == true ||
                        state.extras["odkCollectInstanceId"] != null

                    if (hasOdkIndicators) {
                        // Create a minimal ODK sync payload with available data
                        val minimalPayload = mapOf(
                            "call_id" to callLog.callLogId.toString(),
                            "phone_number" to callLog.phoneNumber,
                            "direction" to callLog.direction,
                            "start_time" to callLog.callStartUtc.toString(),
                            "end_time" to callLog.callEndUtc.toString(),
                            "duration" to callLog.durationSeconds,
                            "outcome" to callLog.outcome,
                            "outcome_detail" to callLog.outcomeDetail,
                            "timestamp" to Instant.now().toString(),
                            "odk_call" to "true",
                            "odk_session" to "fallback_mode"
                        )
                        val pendingSync = PendingSync(
                            callLogId = callLog.callLogId,
                            syncPayloadJson = JSONObject(minimalPayload).toString()
                        )
                        db.pendingSyncDao().insert(pendingSync)
                        syncLogHelper.logStatus(callLog, null, SyncLogStatus.PENDING, "Queued ODK call with fallback config")
                        org.fossify.phone.utils.Logger.callDetection("Created fallback ODK sync payload: ${callLog.callLogId}")
                    } else {
                        // No ODK indicators found, this shouldn't happen for ODK calls
                        db.callLogDao().updateLastSyncError(callLog.callLogId, "ODK call but no indicators found")
                        syncLogHelper.logStatus(callLog, null, SyncLogStatus.FAILED, "ODK call but no indicators found")
                        org.fossify.phone.utils.Logger.callDetection("ODK call has no indicators, creating sync record anyway: ${callLog.callLogId}")
                        // Still create a basic sync to ensure no data loss
                        val basicSync = createBasicSyncPayload(callLog)
                        db.pendingSyncDao().insert(basicSync)
                        syncLogHelper.logStatus(callLog, null, SyncLogStatus.PENDING, "Created basic sync for orphaned ODK call")
                    }
                } else {
                    // Non-ODK calls get basic sync payload
                    val basicSync = createBasicSyncPayload(callLog, awaitMetadata = !callLog.formCompleted)
                    db.pendingSyncDao().insert(basicSync)
                    val status = if (!callLog.formCompleted) "Waiting for metadata form" else "Queued non-ODK call for sync"
                    syncLogHelper.logStatus(callLog, null, SyncLogStatus.PENDING, status)
                    org.fossify.phone.utils.Logger.callDetection("Created basic sync payload for non-ODK call: ${callLog.callLogId}")
                }
            } else {
                val payloadMap = syncPayload.toPersistenceMap().toMutableMap()
                if (!isOdkCall && !callLog.formCompleted) {
                    payloadMap["await_metadata"] = true
                }
                val pendingSync = PendingSync(
                    callLogId = callLog.callLogId,
                    syncPayloadJson = JSONObject(payloadMap).toString()
                )
                db.pendingSyncDao().insert(pendingSync)
                val statusMessage = if (!isOdkCall && !callLog.formCompleted) {
                    "Waiting for metadata form"
                } else {
                    "Queued for sync"
                }
                syncLogHelper.logStatus(callLog, syncPayload, SyncLogStatus.PENDING, statusMessage)
            }

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

    private fun createSyncPayload(callLog: CallLog, state: OdkIntentState): CallSyncPayload? {
        val config = state.config ?: resolvePersistedConfig()

        // If we have ODK indicators but no config, create a basic config from extras
        val finalConfig = config ?: createBasicOdkConfigFromExtras(state.extras, callLog)
        return finalConfig?.let { CallSyncPayloadBuilder.build(callLog, state.extras, it) }
    }

    /**
     * Creates a basic ODK config from extras when full configuration is missing
     * This ensures ODK calls can always create sync records even without Central config
     */
    private fun createBasicOdkConfigFromExtras(extras: Map<String, Any>, callLog: CallLog): OdkConfig? {
        // Only create basic config if this appears to be an ODK call
        val hasOdkCallFlag = extras["odk_call"]?.toString()?.toBooleanStrictOrNull() == true
        val hasInstanceId = extras["odkCollectInstanceId"] != null
        val hasPhoneNumber = callLog.phoneNumber?.isNotBlank() == true

        if (!hasOdkCallFlag && !hasInstanceId) {
            org.fossify.phone.utils.Logger.callDetection("No ODK indicators found in extras, skipping basic config creation")
            return null
        }

        org.fossify.phone.utils.Logger.callDetection("Creating basic ODK config from extras (hasOdkCallFlag: $hasOdkCallFlag, hasInstanceId: $hasInstanceId)")

        // Use placeholder values for missing required fields
        val baseUrl = "https://central.getodk.org" // Default Central URL
        val projectId = "project_${System.currentTimeMillis()}" // Unique project ID
        val datasetName = "phone_calls_${System.currentTimeMillis()}" // Unique dataset name

        return OdkConfig(
            baseUrl = baseUrl,
            projectId = projectId,
            datasetName = datasetName,
            instanceId = extras["odkCollectInstanceId"]?.toString(),
            phoneNumber = callLog.phoneNumber
        )
    }

    /**
     * Creates a basic sync payload for calls without ODK configuration
     */
    private fun createBasicSyncPayload(callLog: CallLog, awaitMetadata: Boolean = false): PendingSync {
        val basicPayload = mutableMapOf(
            "call_id" to callLog.callLogId.toString(),
            "phone_number" to callLog.phoneNumber,
            "direction" to callLog.direction,
            "start_time" to callLog.callStartUtc.toString(),
            "end_time" to callLog.callEndUtc.toString(),
            "duration" to callLog.durationSeconds,
            "outcome" to callLog.outcome,
            "outcome_detail" to callLog.outcomeDetail,
            "timestamp" to Instant.now().toString()
        )
        if (awaitMetadata) {
            basicPayload["await_metadata"] = true
        }
        return PendingSync(
            callLogId = callLog.callLogId,
            syncPayloadJson = JSONObject(basicPayload).toString()
        )
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
