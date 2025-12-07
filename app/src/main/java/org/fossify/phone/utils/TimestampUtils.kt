package org.fossify.phone.utils

import java.time.Instant
import java.time.Duration
import java.time.ZoneId
import kotlin.math.pow
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Timestamp utilities for call logging system
 * Provides consistent timestamp handling across all temporal fields
 */
object TimestampUtils {

    // Formatters for consistent timestamp display
    private const val TIMESTAMP_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
    private const val DISPLAY_PATTERN = "MMM dd, yyyy HH:mm:ss"
    private const val TIME_AGO_PATTERN = "'just now' | 'min ago' | 'hours ago' | 'days ago' | 'weeks ago' | 'months ago' | 'years ago'"

    private val utcFormatter = DateTimeFormatter.ofPattern(TIMESTAMP_PATTERN).withZone(ZoneId.of("UTC"))
    private val displayFormatter = DateTimeFormatter.ofPattern(DISPLAY_PATTERN, Locale.getDefault())
        .withZone(ZoneId.systemDefault())
    private val durationFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    /**
     * Get current timestamp in UTC
     */
    fun now(): Instant = Instant.now()

    /**
     * Format timestamp for API calls and database storage
     */
    fun formatApiTimestamp(timestamp: Instant): String = utcFormatter.format(timestamp)

    /**
     * Format timestamp for display in UI
     */
    fun formatDisplayTimestamp(timestamp: Instant): String = displayFormatter.format(timestamp)

    /**
     * Format timestamp for relative time display (e.g., "5 min ago")
     */
    fun formatTimeAgo(timestamp: Instant): String {
        val now = Instant.now()
        val duration = Duration.between(timestamp, now)

        return when {
            duration.toMinutes() == 0L -> "just now"
            duration.toMinutes() < 60L -> "${duration.toMinutes()} min ago"
            duration.toHours() < 24L -> "${duration.toHours()} hours ago"
            duration.toDays() < 7L -> "${duration.toDays()} days ago"
            duration.toDays() < 30L -> "${duration.toDays() / 7} weeks ago"
            duration.toDays() < 365L -> "${duration.toDays() / 30} months ago"
            else -> "${duration.toDays() / 365} years ago"
        }
    }

    /**
     * Calculate duration between two timestamps in seconds
     */
    fun calculateDurationSeconds(start: Instant, end: Instant): Double {
        require(start <= end) { "Start timestamp must be before or equal to end timestamp" }
        val duration = Duration.between(start, end)
        return duration.toMillis() / 1000.0
    }

    /**
     * Format duration in seconds to human-readable format
     */
    fun formatDuration(durationSeconds: Double): String {
        val duration = Duration.ofSeconds(durationSeconds.toLong())

        return when {
            durationSeconds < 60 -> String.format("%.1f seconds", durationSeconds)
            durationSeconds < 3600 -> {
                val minutes = duration.toMinutes()
                val remainingSeconds = durationSeconds % 60
                if (remainingSeconds > 0) {
                    String.format("%d min %.1f sec", minutes, remainingSeconds)
                } else {
                    String.format("%d min", minutes)
                }
            }
            else -> {
                val hours = duration.toHours()
                val minutes = duration.toMinutes() % 60
                if (minutes > 0) {
                    String.format("%d hr %d min", hours, minutes)
                } else {
                    String.format("%d hr", hours)
                }
            }
        }
    }

    /**
     * Validate if timestamp is within reasonable bounds
     */
    fun isValidTimestamp(timestamp: Instant): Boolean {
        val now = Instant.now()
        val oneYearAgo = now.minus(365, ChronoUnit.DAYS)
        val oneYearFuture = now.plus(365, ChronoUnit.DAYS)

        return timestamp in oneYearAgo..oneYearFuture
    }

    /**
     * Check if timestamp represents a recent call (within last 24 hours)
     */
    fun isRecentCall(timestamp: Instant): Boolean {
        val now = Instant.now()
        val oneDayAgo = now.minus(24, ChronoUnit.HOURS)
        return timestamp.isAfter(oneDayAgo)
    }

    /**
     * Check if timestamp represents a future time
     */
    fun isFutureTimestamp(timestamp: Instant): Boolean {
        return timestamp.isAfter(Instant.now())
    }

    /**
     * Add retry delay to timestamp for next retry attempt
     * Uses exponential backoff: baseDelay * (2 ^ attemptNumber)
     */
    fun calculateNextRetry(currentTime: Instant, attemptNumber: Int, baseDelaySeconds: Long = 10): Instant {
        val delayMultiplier = 2.0.pow(attemptNumber.toDouble()).toLong()
        val delaySeconds = baseDelaySeconds * delayMultiplier
        return currentTime.plusSeconds(delaySeconds)
    }

    /**
     * Parse ISO 8601 timestamp string to Instant
     */
    fun parseTimestamp(timestampString: String): Instant {
        return try {
            Instant.parse(timestampString)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid timestamp format: $timestampString", e)
        }
    }

    /**
     * Safe parsing of timestamp with null handling
     */
    fun parseTimestampOrNull(timestampString: String?): Instant? {
        return if (timestampString != null) {
            try {
                Instant.parse(timestampString)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    /**
     * Get the start of day for a given timestamp (UTC)
     */
    fun startOfDay(timestamp: Instant): Instant {
        return timestamp.truncatedTo(ChronoUnit.DAYS)
    }

    /**
     * Get the end of day for a given timestamp (UTC)
     */
    fun endOfDay(timestamp: Instant): Instant {
        return startOfDay(timestamp).plus(23, ChronoUnit.HOURS).plus(59, ChronoUnit.MINUTES).plus(59, ChronoUnit.SECONDS)
    }

    /**
     * Check if timestamp is within today (UTC)
     */
    fun isToday(timestamp: Instant): Boolean {
        val now = Instant.now()
        val todayStart = startOfDay(now)
        val todayEnd = endOfDay(now)
        return timestamp in todayStart..todayEnd
    }

    /**
     * Check if timestamp is within yesterday (UTC)
     */
    fun isYesterday(timestamp: Instant): Boolean {
        val now = Instant.now()
        val yesterdayStart = startOfDay(now).minus(1, ChronoUnit.DAYS)
        val yesterdayEnd = endOfDay(now).minus(1, ChronoUnit.DAYS)
        return timestamp in yesterdayStart..yesterdayEnd
    }
}
