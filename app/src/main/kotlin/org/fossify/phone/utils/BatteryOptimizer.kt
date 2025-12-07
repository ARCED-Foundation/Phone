package org.fossify.phone.utils

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.util.concurrent.TimeUnit

/**
 * Utility used by WorkManager to delay sync work when battery is low or the device is idle.
 */
object BatteryOptimizer {
    private const val MIN_BATTERY_PERCENT = 35
    private const val DEFER_DELAY_MS = 10 * 60 * 1000L // 10 minutes

    fun isBatteryLevelAcceptable(context: Context): Boolean {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return true
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)

        val percent = if (level >= 0 && scale > 0) (level * 100) / scale else -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val acceptable = isCharging || percent >= MIN_BATTERY_PERCENT
        Logger.odkSync("Battery check: charge=$isCharging, level=$percent%, acceptable=$acceptable")
        return acceptable
    }

    fun getDeferDelayMillis(): Long = DEFER_DELAY_MS

    fun getDelayDescription(): String = "${TimeUnit.MILLISECONDS.toMinutes(DEFER_DELAY_MS)}min"
}
