package org.fossify.phone.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.fossify.phone.api.ApiError
import org.fossify.phone.api.OdkCentralApiClient
import org.fossify.phone.api.OkHttpOdkCentralApiClient
import org.fossify.phone.api.isRetryableError
import org.fossify.phone.database.AppDatabase
import org.fossify.phone.helpers.CallSyncPayload
import org.fossify.phone.helpers.SyncLogHelper
import org.fossify.phone.models.PendingSync
import org.json.JSONObject
import org.fossify.phone.extensions.config
import org.fossify.phone.utils.PerformanceMonitor
import org.fossify.phone.extensions.toMap
import org.fossify.phone.models.SyncLogStatus
import org.fossify.phone.helpers.IntentExtrasHelper
import org.fossify.phone.helpers.IntentExtrasHelper.OdkConfig
import org.fossify.phone.helpers.CallSyncPayloadBuilder
import org.fossify.phone.database.dao.LastSuccessfulConfig

/**
 * WorkManager worker for ODK synchronization.
 *
 * - Uses exponential backoff (10s → 64min) capped at 7 attempts
 * - Respects network/battery constraints
 * - Processes PendingSync queue and updates CallLog state
 * - Posts notifications for authentication and terminal failures
 */
class OdkSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val db by lazy { AppDatabase.getInstance(applicationContext) }
    private val callLogDao by lazy { db.callLogDao() }
    private val pendingSyncDao by lazy { db.pendingSyncDao() }
    private val credentialsDao by lazy { db.centralCredentialsDao() }
    private val syncLogDao by lazy { db.syncLogDao() }
    private val apiClient: OdkCentralApiClient by lazy { OkHttpOdkCentralApiClient() }
    private val syncLogHelper by lazy { SyncLogHelper(applicationContext) }
    private val workerLock = mutex

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        workerLock.withLock {
            val syncStart = PerformanceMonitor.startSync()
            try {
                val now = Instant.now()
                val pending = pendingSyncDao.getReadyForSync(now).distinctBy { it.callLogId }
                if (pending.isEmpty()) {
                    Log.d(TAG, "No pending sync items found")
                    scheduleFutureRetry(now)
                    return@withLock Result.success()
                }

                val processedCalls = mutableSetOf<java.util.UUID>()
                var earliestNextRetry: Instant? = null
                pending.forEach { item ->
                    val result = processItem(item, now, processedCalls)
                    if (result.result == ItemResult.RETRY) {
                        val next = result.nextRetry ?: now
                        earliestNextRetry = earliestNextRetry?.let { minOf(it, next) } ?: next
                    }
                }

                earliestNextRetry?.let { next ->
                    val delayMs = (next.toEpochMilli() - Instant.now().toEpochMilli()).coerceAtLeast(0L)
                    enqueueSync(applicationContext, delayMs)
                }

                Result.success()
            } finally {
                PerformanceMonitor.endSync(syncStart)
            }
        }
    }

    private suspend fun scheduleFutureRetry(now: Instant) {
        val nextRetry = pendingSyncDao.getEarliestNextRetry() ?: return
        if (nextRetry.isAfter(now)) {
            val delayMs = (nextRetry.toEpochMilli() - now.toEpochMilli()).coerceAtLeast(0L)
            enqueueSync(applicationContext, delayMs)
        }
    }

    private suspend fun processItem(item: PendingSync, now: Instant, processedCalls: MutableSet<java.util.UUID>): ProcessResult {
        val callLog = callLogDao.getById(item.callLogId)
        if (callLog == null) {
            pendingSyncDao.delete(item.id)
            return ProcessResult(ItemResult.DROPPED, null)
        }
        if (!callLog.isOdkCall && !callLog.formCompleted) {
            val next = now.plusSeconds(300)
            pendingSyncDao.update(item.withRetry(next, "metadata"))
            syncLogHelper.logStatus(callLog, null, SyncLogStatus.PENDING, "Waiting for metadata form")
            return ProcessResult(ItemResult.RETRY, next)
        }
        if (processedCalls.contains(item.callLogId)) {
            pendingSyncDao.delete(item.id)
            syncLogHelper.logStatus(callLog, null, SyncLogStatus.FAILED, "Duplicate pending entry discarded")
            return ProcessResult(ItemResult.DROPPED, null)
        }
        processedCalls.add(item.callLogId)
        if (callLog.synced) {
            pendingSyncDao.delete(item.id)
            return ProcessResult(ItemResult.DROPPED, null)
        }

        val payload = runCatching { JSONObject(item.syncPayloadJson) }.getOrElse {
            callLogDao.markSyncFailed(callLog.callLogId, "Invalid payload")
            pendingSyncDao.delete(item.id)
            syncLogHelper.logStatus(callLog, null, SyncLogStatus.FAILED, "Invalid payload")
            return ProcessResult(ItemResult.DROPPED, null)
        }

        if (payload.optBoolean("await_config", false)) {
            val rebuilt = rebuildPayloadWithConfig(callLog)
            if (rebuilt == null) {
                val next = now.plusSeconds(300)
                pendingSyncDao.update(item.copy(nextRetry = next, errorType = "missing_config"))
                syncLogHelper.logStatus(callLog, null, SyncLogStatus.PENDING, "Waiting for ODK config")
                return ProcessResult(ItemResult.RETRY, next)
            } else {
                val updated = item.reset().copy(
                    syncPayloadJson = JSONObject(rebuilt.toPersistenceMap()).toString(),
                    nextRetry = now
                )
                pendingSyncDao.insert(updated)
                syncLogHelper.logStatus(callLog, rebuilt, SyncLogStatus.PENDING, "Config restored, retrying")
                return ProcessResult(ItemResult.RETRY, now)
            }
        }
        if (payload.optBoolean("await_metadata", false) && !callLog.formCompleted) {
            val next = now.plusSeconds(300)
            pendingSyncDao.update(item.copy(nextRetry = next, errorType = "metadata"))
            syncLogHelper.logStatus(callLog, null, SyncLogStatus.PENDING, "Waiting for metadata form")
            return ProcessResult(ItemResult.RETRY, next)
        } else if (payload.optBoolean("await_metadata", false) && callLog.formCompleted) {
            payload.remove("await_metadata")
            pendingSyncDao.insert(item.reset().copy(syncPayloadJson = payload.toString(), nextRetry = now))
        }

        val payloadMap = payload.toMap()
        val entityPayload = CallSyncPayload.fromPersistenceMap(payloadMap)
            ?: run {
                callLogDao.markSyncFailed(callLog.callLogId, "Missing payload metadata")
                pendingSyncDao.delete(item.id)
                syncLogHelper.logStatus(callLog, null, SyncLogStatus.FAILED, "Missing payload metadata")
                return ProcessResult(ItemResult.DROPPED, null)
            }

        val credentials = credentialsDao.getByUrlAndProject(entityPayload.baseUrl, entityPayload.projectId)
            ?: credentialsDao.getValidatedCredentials()

        if (credentials == null) {
            callLogDao.markSyncFailed(callLog.callLogId, "Missing credentials or configuration")
            pendingSyncDao.delete(item.id)
            syncLogHelper.logStatus(callLog, entityPayload, SyncLogStatus.FAILED, "Missing credentials or configuration")
            notifyAuthFailure()
            return ProcessResult(ItemResult.DROPPED, null)
        }

        if (callLog.synced) {
            pendingSyncDao.delete(item.id)
            return ProcessResult(ItemResult.DROPPED, null)
        }

        val response = apiClient.sendCallLog(
            payload = entityPayload,
            credentials = credentials
        )

        return if (response.success) {
            callLogDao.markAsSynced(callLog.callLogId)
            pendingSyncDao.delete(item.id)
            syncLogHelper.logStatus(callLog, entityPayload, SyncLogStatus.SUCCESS, "Synced after ${item.retryCount} attempt(s)")
            ProcessResult(ItemResult.SUCCESS, null)
        } else {
            val errorType = categorizeError(response.error)
            val errorMessage = response.error?.toString() ?: "Unknown error"
            val shouldRetry = response.error?.let { isRetryableError(it) } ?: true
            if (item.retryCount + 1 >= MAX_ATTEMPTS || !shouldRetry) {
                callLogDao.markSyncFailed(callLog.callLogId, errorMessage)
                pendingSyncDao.delete(item.id)
                syncLogHelper.logStatus(callLog, entityPayload, SyncLogStatus.FAILED, errorMessage)
                notifyFailure(errorType)
                ProcessResult(ItemResult.DROPPED, null)
            } else {
                val next = now.plusMillis(computeBackoff(item.retryCount))
                pendingSyncDao.update(item.withRetry(next, errorType))
                callLogDao.markSyncFailed(callLog.callLogId, errorMessage)
                syncLogHelper.logStatus(
                    callLog,
                    entityPayload,
                    SyncLogStatus.PENDING,
                    errorMessage.ifBlank { "Retry scheduled" }
                )
                ProcessResult(ItemResult.RETRY, next)
            }
        }
    }

    private fun resolveConfig(): OdkConfig? {
        val cfg = applicationContext.config
        val base = cfg.lastOdkBaseUrl
        val project = cfg.lastOdkProjectId
        val dataset = cfg.lastOdkDataset
        if (!base.isNullOrBlank() && !project.isNullOrBlank() && !dataset.isNullOrBlank()) {
            return OdkConfig(base, project, dataset)
        }

        val lastSuccess: LastSuccessfulConfig? = runCatching { syncLogDao.getLastSuccessfulConfig() }.getOrNull()
        return lastSuccess?.let {
            cfg.lastOdkBaseUrl = it.baseUrl
            cfg.lastOdkProjectId = it.projectId
            cfg.lastOdkDataset = it.datasetName
            OdkConfig(it.baseUrl, it.projectId, it.datasetName)
        }
    }

    private fun rebuildPayloadWithConfig(callLog: org.fossify.phone.models.CallLog): CallSyncPayload? {
        val config = resolveConfig() ?: return null
        val extras = IntentExtrasHelper.parseExtrasJson(callLog.extrasJson)
        return CallSyncPayloadBuilder.build(callLog, extras, config)
    }

    private fun categorizeError(error: ApiError?): String {
        return when (error) {
            is ApiError.AuthenticationError, is ApiError.AuthorizationError -> "authentication"
            is ApiError.NetworkError -> "network"
            is ApiError.ServerError -> "server"
            else -> "unknown"
        }
    }

    private fun computeBackoff(retryCount: Int): Long {
        if (retryCount >= MAX_ATTEMPTS - 1) return MAX_BACKOFF_DELAY
        val delay = INITIAL_BACKOFF_DELAY * (1L shl retryCount)
        return delay.coerceAtMost(MAX_BACKOFF_DELAY)
    }

    private fun notifyFailure(errorType: String) {
        ensureChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Call sync failed")
            .setContentText("Last error: $errorType. Will not retry until resolved.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_FAILURE_ID, notification)
    }

    private fun notifyAuthFailure() {
        ensureChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Authentication required")
            .setContentText("Update ODK Central credentials to resume syncing.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_AUTH_ID, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Call Sync",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        private val mutex = Mutex()
        private const val TAG = "OdkSyncWorker"
        private const val CHANNEL_ID = "call_sync_channel"
        private const val NOTIFICATION_FAILURE_ID = 1001
        private const val NOTIFICATION_AUTH_ID = 1002
        private const val WORK_TAG = "odk_sync"
        private const val MAX_ATTEMPTS = 7
        private const val INITIAL_BACKOFF_DELAY = 10_000L // 10 seconds
        private const val MAX_BACKOFF_DELAY = 3_840_000L // 64 minutes

        fun createSyncRequest(initialDelayMs: Long = 0L, requireBatteryNotLow: Boolean = false): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<OdkSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(requireBatteryNotLow)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    INITIAL_BACKOFF_DELAY,
                    TimeUnit.MILLISECONDS
                )
                .addTag(WORK_TAG)
                .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
                .build()
        }

        fun enqueueSync(context: Context, initialDelayMs: Long = 0L) {
            try {
                val request = createSyncRequest(initialDelayMs)
                WorkManager.getInstance(context).enqueue(request)
                Log.d(TAG, "ODK sync enqueued (delay=${initialDelayMs}ms)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enqueue ODK sync work", e)
            }
        }

        fun cancelSync(context: Context) {
            try {
                WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cancel ODK sync work", e)
            }
        }
    }

    private data class ProcessResult(
        val result: ItemResult,
        val nextRetry: Instant?
    )

    private enum class ItemResult { SUCCESS, RETRY, DROPPED }
}
