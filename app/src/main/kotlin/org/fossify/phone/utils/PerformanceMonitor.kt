package org.fossify.phone.utils

import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

/**
 * Tracks key performance metrics for call detection and background sync.
 * Logs warnings when operations exceed their budgets and keeps a short moving history.
 */
object PerformanceMonitor {
    private const val CALL_DETECTION_THRESHOLD_MS = 100L
    private const val SYNC_THRESHOLD_MS = 5 * 60 * 1000L // 5 minutes
    private const val HISTORY_LIMIT = 50

    private val callDetectionHistory = ArrayDeque<Long>(HISTORY_LIMIT)
    private val syncHistory = ArrayDeque<Long>(HISTORY_LIMIT)

    fun startCallDetection(): Long = System.nanoTime()

    fun endCallDetection(startNanos: Long) {
        val elapsedMs = nanosToMillis(System.nanoTime() - startNanos)
        pushHistory(callDetectionHistory, elapsedMs)
        if (elapsedMs > CALL_DETECTION_THRESHOLD_MS) {
            Logger.callDetection("Call detection slow: ${elapsedMs}ms", "w")
        }
    }

    fun startSync(): Long = System.nanoTime()

    fun endSync(startNanos: Long) {
        val elapsedMs = nanosToMillis(System.nanoTime() - startNanos)
        pushHistory(syncHistory, elapsedMs)
        if (elapsedMs > SYNC_THRESHOLD_MS) {
            Logger.odkSync("Sync slower than ${SYNC_THRESHOLD_MS}ms: ${elapsedMs}ms", "w")
        }
    }

    private fun pushHistory(history: ArrayDeque<Long>, value: Long) {
        history.addLast(value)
        if (history.size > HISTORY_LIMIT) {
            history.removeFirst()
        }
    }

    private fun nanosToMillis(nanos: Long) = TimeUnit.NANOSECONDS.toMillis(nanos)

    fun getAverageCallDetectionLatency(): Long =
        if (callDetectionHistory.isEmpty()) 0L else callDetectionHistory.average().toLong()

    fun getAverageSyncLatency(): Long =
        if (syncHistory.isEmpty()) 0L else syncHistory.average().toLong()
}
