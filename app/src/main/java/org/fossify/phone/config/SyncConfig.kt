package org.fossify.phone.config

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import org.fossify.phone.work.OdkSyncWorker
import java.util.concurrent.TimeUnit

/**
 * Configuration for network constraints and periodic sync requests
 *
 * Based on research.md requirements:
 * - Constraints: Network.CONNECTED + BatteryNotLow
 * - Periodic sync: 24h interval
 * - Immediate sync enqueues available
 */
object SyncConfig {

    // Sync configuration constants
    const val SYNC_INTERVAL_HOURS = 24L
    const val SYNC_INTERVAL_FLEX_HOURS = 4L
    const val SYNC_TAG = "odk_periodic_sync"

    /**
     * Creates network constraints for ODK sync operations
     * Requires connected network and adequate battery level
     */
    fun createNetworkConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // Requires internet connection
            .setRequiresBatteryNotLow(true) // Prevents sync when battery is low
            .setRequiresStorageNotLow(false) // Storage is less critical for sync
            .build()
    }

    /**
     * Creates a periodic work request for automatic sync
     * Runs every 24 hours with 4 hours flex interval
     */
    fun createPeriodicSyncRequest(): PeriodicWorkRequest {
        return PeriodicWorkRequestBuilder<OdkSyncWorker>(
            SYNC_INTERVAL_HOURS,
            TimeUnit.HOURS,
            SYNC_INTERVAL_FLEX_HOURS,
            TimeUnit.HOURS
        )
            .setConstraints(createNetworkConstraints())
            .setInitialDelay(10, TimeUnit.MINUTES) // Initial delay to allow app startup
            .addTag(SYNC_TAG)
            .addTag("periodic_sync")
            .build()
    }

    /**
     * Enqueues a periodic sync request
     * This should be called once during app initialization
     */
    fun schedulePeriodicSync(context: Context) {
        try {
            val periodicSyncRequest = createPeriodicSyncRequest()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                SYNC_TAG,
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                periodicSyncRequest
            )
            // Log via Android Logcat since we don't have logging framework yet
            android.util.Log.d("SyncConfig", "Periodic ODK sync scheduled (${SYNC_INTERVAL_HOURS}h interval)")
        } catch (e: Exception) {
            android.util.Log.e("SyncConfig", "Failed to schedule periodic sync", e)
        }
    }

    /**
     * Cancels the periodic sync request
     * Useful for maintenance or temporary disabling
     */
    fun cancelPeriodicSync(context: Context) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(SYNC_TAG)
            android.util.Log.d("SyncConfig", "Periodic ODK sync cancelled")
        } catch (e: Exception) {
            android.util.Log.e("SyncConfig", "Failed to cancel periodic sync", e)
        }
    }

    /**
     * Enqueues an immediate sync request
     * Use this for manual triggers or after new call logs are created
     */
    fun enqueueImmediateSync(context: Context) {
        try {
            val immediateRequest = OdkSyncWorker.createSyncRequest()
            WorkManager.getInstance(context).enqueue(immediateRequest)
            android.util.Log.d("SyncConfig", "Immediate ODK sync enqueued")
        } catch (e: Exception) {
            android.util.Log.e("SyncConfig", "Failed to enqueue immediate sync", e)
        }
    }

    /**
     * Checks if periodic sync is scheduled
     * Returns true if work is scheduled, false otherwise
     */
    fun isPeriodicSyncScheduled(context: Context): Boolean {
        return try {
            val workInfo = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(SYNC_TAG)
                .get()

            workInfo.any { info ->
                info.state == WorkInfo.State.RUNNING ||
                    info.state == WorkInfo.State.ENQUEUED
            }
        } catch (e: Exception) {
            android.util.Log.e("SyncConfig", "Failed to check periodic sync status", e)
            false
        }
    }

    /**
     * Gets the current status of periodic sync
     * Returns a human-readable status string
     */
    fun getSyncStatus(context: Context): String {
        return try {
            val workInfo = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(SYNC_TAG)
                .get()

            when {
                workInfo.isEmpty() -> "Not scheduled"
                workInfo.any { it.state == WorkInfo.State.RUNNING } -> "Running"
                workInfo.any { it.state == WorkInfo.State.ENQUEUED } -> "Scheduled"
                workInfo.any { it.state == WorkInfo.State.FAILED } -> "Failed"
                else -> "Unknown"
            }
        } catch (e: Exception) {
            "Error checking status"
        }
    }
}
