package org.fossify.phone.testutil

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor

class TestApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .setMinimumLoggingLevel(Log.ERROR)
            .build()
}
