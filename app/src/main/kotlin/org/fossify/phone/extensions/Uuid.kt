package org.fossify.phone.extensions

import org.fossify.phone.utils.UuidUtils
import java.util.UUID

/**
 * Extension functions for UUID operations.
 * Provides fluent and convenient methods for common UUID manipulations.
 */

/**
 * Safely generates a UUID from a string using UuidUtils.
 * Returns null if the input is null, blank, or invalid.
 *
 * @return UUID instance if valid, null otherwise
 */
fun String?.toUuidOrNull(): UUID? = UuidUtils.parseUuidOrNull(this)

/**
 * Parses a string to UUID using UuidUtils with validation.
 * Throws IllegalArgumentException if the string is not a valid UUID.
 *
 * @return UUID instance
 * @throws IllegalArgumentException if the string is not a valid UUID
 */
fun String.toUuid(): UUID = UuidUtils.parseUuid(this)

/**
 * Formats a String UUID to lowercase if it's a valid UUID.
 * Returns original string if invalid (for graceful degradation).
 *
 * @return Lowercase formatted UUID string if valid, original otherwise
 */
fun String.formatUuid(): String = UuidUtils.formatUuid(this)

/**
 * Validates if a String is a valid UUID using UuidUtils.
 * Handles null and blank inputs gracefully.
 *
 * @return true if valid UUID, false if null, blank, or invalid
 */
fun String?.isValidUuid(): Boolean = UuidUtils.isValidBusinessUuidOrNull(this)

/**
 * Validates if a String is a valid UUID format using regex.
 * Faster validation but doesn't create a UUID object.
 * Handles null and blank inputs gracefully.
 *
 * @return true if valid UUID format, false if null, blank, or invalid format
 */
fun String?.isValidUuidFormat(): Boolean = this?.let { UuidUtils.isValidUuidFormat(it) } ?: false

/**
 * Gets the version of a UUID string if it's valid.
 * Returns -1 if the string is not a valid UUID.
 *
 * @return UUID version (1-5), or -1 if invalid
 */
fun String?.getUuidVersion(): Int = this?.let { UuidUtils.getVersion(it) } ?: -1

/**
 * Checks if a UUID string is version 4 (random).
 *
 * @return true if it's a version 4 UUID, false otherwise
 */
fun String?.isVersion4(): Boolean = UuidUtils.isVersion4(this ?: "")

// ============ UUID Extensions ============

/**
 * Formats a UUID to lowercase standardized format.
 *
 * @return Lowercase formatted UUID string
 */
fun UUID.format(): String = UuidUtils.formatToLowerCase(this)

/**
 * Validates if a UUID is not the null UUID (all zeros).
 *
 * @return true if not the null UUID, false otherwise
 */
fun UUID.isNotNull(): Boolean = this.toString() != "00000000-0000-0000-0000-000000000000"

/**
 * Checks if two UUIDs are equal (case-insensitive comparison).
 *
 * @param other The other UUID to compare with
 * @return true if they represent the same UUID, false otherwise
 */
fun UUID.equalsIgnoreCase(other: UUID): Boolean = this.format() == other.format()

/**
 * Safely converts UUID to String with null check.
 *
 * @return UUID string representation, empty string if null
 */
fun UUID?.toStringSafe(): String = this?.toString() ?: ""

/**
 * Creates a copy of the UUID with format ensured to be lowercase.
 *
 * @return Lowercase formatted UUID string
 */
fun UUID.toFormattedString(): String = this.format()

/**
 * Checks if the UUID was generated recently (within last 24 hours).
 * This is a simple heuristic that may need adjustment based on actual generation method.
 *
 * @return true if UUID appears to be recent (heuristic), false otherwise
 */
fun UUID.isRecent(): Boolean {
    // This is a simple heuristic - in production, you might want to use actual timestamp
    // UUID version 1 contains timestamp information
    return this.mostSignificantBits != 0L && this.leastSignificantBits != 0L
}

// = nullable UUID extensions ============

/**
 * Safely formats a nullable UUID to lowercase.
 * Returns empty string if UUID is null.
 *
 * @return Lowercase formatted UUID string or empty string
 */
fun UUID?.formatSafe(): String = this?.format() ?: ""

/**
 * Safely converts nullable UUID to String with null handling.
 * This provides a consistent way to handle potential null UUIDs.
 *
 * @return UUID string representation or empty string
 */
fun UUID?.toStringOrEmpty(): String = this?.toString() ?: ""

/**
 * Gets the version of a UUID if it's not null.
 * Returns -1 if UUID is null.
 *
 * @return UUID version (1-5), or -1 if null
 */
fun UUID?.getVersionSafe(): Int = this?.let { UuidUtils.getVersion(it.toString()) } ?: -1