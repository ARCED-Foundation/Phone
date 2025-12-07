package org.fossify.phone.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import org.fossify.phone.R
import org.fossify.phone.database.AppDatabase
import org.fossify.phone.work.WorkManagerHelper
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Foreground service that triggers immediate and periodic call sync work.
 * Keeps the process alive long enough for WorkManager to schedule jobs with
 * required constraints.
 */
class CallSyncService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        forcePendingSyncsNow()
        WorkManagerHelper.enqueueOdkSyncWork(this, forceNow = true)
        WorkManagerHelper.schedulePeriodicSync(this)
        stopForeground(true)
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?) = null

    private fun buildNotification(): Notification {
        ensureChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.call_sync_in_progress))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Call Sync",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "call_sync_channel"
        private const val NOTIFICATION_ID = 2001
    }

    private fun forcePendingSyncsNow() {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val db = AppDatabase.getInstance(this@CallSyncService)
                db.pendingSyncDao().resetAllForImmediateSync(Instant.now())
            }
        }
    }
}
