package org.fossify.phone.work

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import org.fossify.phone.utils.BatteryOptimizer
import java.util.concurrent.TimeUnit

/**
 * Helper class for managing WorkManager operations
 * Provides convenient methods for enqueueing, monitoring, and cancelling ODK sync work
 */
object WorkManagerHelper {

    private const val TAG = "WorkManagerHelper"
    private const val SYNC_WORK_TAG = "odk_sync"
    private const val PERIODIC_SYNC_TAG = "odk_periodic_sync"
    private const val MAX_ATTEMPTS = 7
    private const val INITIAL_BACKOFF_DELAY = 10_000L // 10 seconds
    private const val MAX_BACKOFF_DELAY = 3_840_000L // 64 minutes
    private const val PERIODIC_INTERVAL_HOURS = 24L // 24 hour interval as specified in Research.md

    internal fun createNetworkConstraints(requireBatteryNotLow: Boolean = false): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(requireBatteryNotLow)
            .build()

    /**
     * Enqueues a one-time ODK sync work request with exponential backoff
     * Uses exponential schedule (10s → 64min cap, 7 attempts)
     */
    fun enqueueOdkSyncWork(context: Context, initialDelayMs: Long = 0L, forceNow: Boolean = false) {
        val delayMs = initialDelayMs
        try {
            val workRequest = OdkSyncWorker.createSyncRequest(
                initialDelayMs = delayMs,
                requireBatteryNotLow = !forceNow && BatteryOptimizer.isBatteryLevelAcceptable(context)
            )
            WorkManager.getInstance(context).enqueueUniqueWork(
                SYNC_WORK_TAG,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            Log.d(TAG, "ODK sync work enqueued with exponential backoff policy")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue ODK sync work", e)
        }
    }

    /**
     * Cancels all pending ODK sync work requests
     */
    fun cancelOdkSyncWork(context: Context) {
        try {
            WorkManager.getInstance(context).cancelAllWorkByTag(SYNC_WORK_TAG)
            Log.d(TAG, "Cancelled all pending ODK sync work requests")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel ODK sync work", e)
        }
    }

    /**
     * Schedules a periodic sync every 24 hours with required constraints.
     */
    fun schedulePeriodicSync(context: Context) {
        val initialDelay = if (BatteryOptimizer.isBatteryLevelAcceptable(context)) {
            0L
        } else {
            BatteryOptimizer.getDeferDelayMillis()
        }
        val request = PeriodicWorkRequestBuilder<OdkSyncWorker>(
            PERIODIC_INTERVAL_HOURS, TimeUnit.HOURS
        )
            .setConstraints(createNetworkConstraints(requireBatteryNotLow = true))
            .addTag(PERIODIC_SYNC_TAG)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                INITIAL_BACKOFF_DELAY,
                TimeUnit.MILLISECONDS
            )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_SYNC_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Gets the current status of ODK sync work requests
     */
    fun getOdkSyncStatus(context: Context) {
        try {
            val workManager = WorkManager.getInstance(context)
            val workInfo = workManager.getWorkInfosByTag(SYNC_WORK_TAG)

            // Note: This would typically be used with LiveData or Flow for real-time updates
            // For now, this is a placeholder for status monitoring
            Log.d(TAG, "ODK sync work status requested")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get ODK sync work status", e)
        }
    }

    /**
     * Helper method to calculate expected retry delays
     * Returns a list of expected delays in milliseconds for each attempt
     */
    fun getExpectedRetryDelays(): List<Long> {
        val delays = mutableListOf<Long>()
        var currentDelay = INITIAL_BACKOFF_DELAY

        for (i in 0 until MAX_ATTEMPTS - 1) { // -1 because the first attempt has no delay
            delays.add(currentDelay)
            currentDelay = (currentDelay * 2.0f).toLong()
            if (currentDelay > MAX_BACKOFF_DELAY) {
                currentDelay = MAX_BACKOFF_DELAY
            }
        }

        return delays
    }

    /**
     * Logs the retry schedule for debugging purposes
     */
    fun logRetrySchedule() {
        val delays = getExpectedRetryDelays()
        Log.d(TAG, "ODK sync retry schedule:")
        delays.forEachIndexed { index, delay ->
            val minutes = delay / 60_000
            val seconds = (delay % 60_000) / 1000
            Log.d(TAG, "  Attempt ${index + 2}: ${minutes}m ${seconds}s delay")
        }
    }
}
