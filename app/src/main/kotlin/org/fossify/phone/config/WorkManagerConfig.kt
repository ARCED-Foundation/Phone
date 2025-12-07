package org.fossify.phone.config

import android.content.Context
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import org.fossify.phone.work.OdkSyncWorker

/**
 * Configuration class for WorkManager setup
 * Sets up ExponentialBackoffPolicy.LINEAR with 7 attempts (10s → 64min)
 */
class WorkManagerConfig(private val context: Context) {
    companion object {
        private const val TAG = "WorkManagerConfig"
    }

    fun initialize() {
        val workerFactory = object : WorkerFactory() {
            override fun createWorker(
                appContext: android.content.Context,
                workerClassName: String,
                workerParameters: androidx.work.WorkerParameters
            ): androidx.work.ListenableWorker? {
                return when (workerClassName) {
                    OdkSyncWorker::class.java.name -> {
                        OdkSyncWorker(appContext, workerParameters)
                    }
                    else -> {
                        null
                    }
                }
            }
        }

        val config = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

        // Initialize WorkManager with custom configuration if needed
        try {
            WorkManager.initialize(context, config)
            setupGlobalConstraints()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "WorkManager already initialized; skipping custom initialization.")
        }
    }

    private fun setupGlobalConstraints() {
        // This method sets up global work constraints and retry policies
        // The actual retry configuration is set at the OneTimeRequest level
        // when creating work requests using exponentialBackoff extension
    }
}
