package org.fossify.phone

import android.app.Application
import android.util.Log
import org.fossify.phone.config.WorkManagerConfig
import org.fossify.phone.utils.CrashTracker

/**
 * Custom Application class for the Phone app
 * Initializes WorkManager with exponential backoff configuration
 */
class PhoneApplication : Application() {

    private lateinit var workManagerConfig: WorkManagerConfig

    override fun onCreate() {
        super.onCreate()
        try {
            Log.d("PhoneApplication", "Starting application initialization")
            CrashTracker.initialize(this)
            initializeWorkManager()
            Log.d("PhoneApplication", "Application initialization completed successfully")
        } catch (e: Exception) {
            Log.e("PhoneApplication", "Fatal error during application initialization", e)
            // Re-throw to ensure the app crashes with a visible error
            throw RuntimeException("Failed to initialize application: ${e.message}", e)
        }
    }

    private fun initializeWorkManager() {
        try {
            Log.d("PhoneApplication", "Initializing WorkManager")
            workManagerConfig = WorkManagerConfig(this)
            workManagerConfig.initialize()
            Log.d("PhoneApplication", "WorkManager initialization completed")
        } catch (e: Exception) {
            Log.e("PhoneApplication", "Failed to initialize WorkManager", e)
            throw e // Re-throw to be caught by the outer try-catch
        }
    }
}
