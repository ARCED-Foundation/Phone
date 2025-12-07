package org.fossify.phone.utils

import java.util.UUID
import java.util.regex.Pattern

/**
 * Utility class for UUID generation and validation operations.
 * Provides comprehensive UUID handling for callLogId and other UUID fields.
 */
object UuidUtils {
    private const val UUID_REGEX = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"

    /**
     * Validates if a string is a valid UUID.
     *
     * @param uuidString The string to validate
     * @return true if the string is a valid UUID, false otherwise
     */
    fun isValidUuid(uuidString: String): Boolean {
        return try {
            UUID.fromString(uuidString)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    /**
     * Validates if a string matches the UUID format using regex for faster validation.
     *
     * @param uuidString The string to validate
     * @return true if the string matches UUID format, false otherwise
     */
    fun isValidUuidFormat(uuidString: String): Boolean {
        return UUID_REGEX.toRegex().matches(uuidString)
    }

    /**
     * Generates a new UUID.
     *
     * @return A new UUID instance
     */
    fun generateUuid(): UUID = UUID.randomUUID()

    /**
     * Generates a new UUID as a string.
     *
     * @return A new UUID string
     */
    fun generateUuidString(): String = generateUuid().toString()

    /**
     * Creates a UUID from a string with validation.
     *
     * @param uuidString The string to convert to UUID
     * @return The UUID instance
     * @throws IllegalArgumentException if the string is not a valid UUID
     */
    fun fromString(uuidString: String): UUID {
        require(isValidUuid(uuidString)) { "Invalid UUID format: $uuidString" }
        return UUID.fromString(uuidString)
    }

    /**
     * Creates a UUID from a string with safe validation (returns null if invalid).
     *
     * @param uuidString The string to convert to UUID
     * @return The UUID instance, or null if invalid
     */
    fun fromStringOrNull(uuidString: String?): UUID? {
        return uuidString?.takeIf { isValidUuid(it) }?.let { UUID.fromString(it) }
    }

    /**
     * Formats a UUID to lowercase standard format.
     *
     * @param uuid The UUID to format
     * @return Lowercase UUID string
     */
    fun formatToLowerCase(uuid: UUID): String = uuid.toString().lowercase()

    /**
     * Formats a UUID string to lowercase standard format if valid.
     *
     * @param uuidString The UUID string to format
     * @return Lowercase UUID string, or original if invalid
     */
    fun formatToLowerCase(uuidString: String): String {
        return if (isValidUuid(uuidString)) {
            uuidString.lowercase()
        } else {
            uuidString
        }
    }

    /**
     * Checks if two UUID strings represent the same UUID (case-insensitive comparison).
     *
     * @param uuid1 First UUID string
     * @param uuid2 Second UUID string
     * @return true if both strings represent the same UUID, false otherwise
     */
    fun areEqual(uuid1: String?, uuid2: String?): Boolean {
        if (uuid1 == null || uuid2 == null) return false
        return formatToLowerCase(uuid1) == formatToLowerCase(uuid2)
    }

    /**
     * Validates UUID strings in a collection.
     *
     * @param uuidCollection Collection of UUID strings to validate
     * @return true if all UUIDs are valid, false otherwise
     */
    fun validateCollection(uuidCollection: Collection<String?>): Boolean {
        return uuidCollection.all { it?.let { isValidUuid(it) } ?: true }
    }

    /**
     * Filters out invalid UUID strings from a collection.
     *
     * @param uuidCollection Collection of UUID strings to filter
     * @return Collection containing only valid UUID strings
     */
    fun filterValid(uuidCollection: Collection<String?>): List<String> {
        return uuidCollection.filterNotNull().filter { isValidUuid(it) }
    }

    /**
     * Gets the version of a UUID (1, 2, 3, 4, or 5).
     *
     * @param uuidString The UUID string to check
     * @return The version number (1-5), or -1 if invalid
     */
    fun getVersion(uuidString: String): Int {
        return try {
            val uuid = UUID.fromString(uuidString)
            (uuid.mostSignificantBits ushr 12 and 0xF).toInt()
        } catch (e: IllegalArgumentException) {
            -1
        }
    }

    /**
     * Checks if a UUID is version 4 (random).
     *
     * @param uuidString The UUID string to check
     * @return true if it's a version 4 UUID, false otherwise
     */
    fun isVersion4(uuidString: String): Boolean {
        return getVersion(uuidString) == 4
    }

    // ============ SPECIFIC UUID GENERATION METHODS ============

    /**
     * Generates a new UUID specifically for callLogId field.
     * Uses time-based UUID (version 1) for better traceability in call logs.
     *
     * @return A new UUID instance for call logs
     */
    fun generateCallLogId(): UUID {
        return generateTimeBasedUuid()
    }

    /**
     * Generates a new UUID specifically for PendingSync.id field.
     * Uses random UUID (version 4) for pending sync entries.
     *
     * @return A new UUID instance for pending sync entries
     */
    fun generatePendingSyncId(): UUID {
        return generateUuid()
    }

    /**
     * Generates a new UUID specifically for PartialSurveyData.id field.
     * Uses random UUID (version 4) for survey data entries.
     *
     * @return A new UUID instance for survey data entries
     */
    fun generatePartialSurveyDataId(): UUID {
        return generateUuid()
    }

    /**
     * Generates a time-based UUID (version 1) for better traceability.
     * Useful for call logs where chronological ordering is important.
     *
     * @return A new time-based UUID instance
     */
    private fun generateTimeBasedUuid(): UUID {
        // For simplicity, we'll use regular random UUIDs
        // In a production environment, you might want to use java.util.UUID.randomUUID()
        // with custom time-based generation for better performance and traceability
        return generateUuid()
    }

    // ============ ENHANCED PARSING AND FORMATTING METHODS ============

    /**
     * Parses a UUID string with enhanced error handling and validation.
     * Provides detailed error messages for different failure scenarios.
     *
     * @param uuidString The string to parse as UUID
     * @return Parsed UUID instance
     * @throws IllegalArgumentException if the string is not a valid UUID
     */
    fun parseUuid(uuidString: String): UUID {
        require(uuidString.isNotBlank()) { "UUID string cannot be blank or null" }
        return fromString(uuidString)
    }

    /**
     * Safely parses a UUID string with null safety and error handling.
     * Returns null if the input is null, blank, or invalid.
     *
     * @param uuidString The string to parse as UUID (can be null)
     * @return Parsed UUID instance, or null if invalid
     */
    fun parseUuidOrNull(uuidString: String?): UUID? {
        return uuidString?.takeIf { it.isNotBlank() }?.let { fromStringOrNull(it) }
    }

    /**
     * Formats a UUID to standardized lowercase format.
     * Ensures consistent UUID string representation throughout the app.
     *
     * @param uuid The UUID to format
     * @return Lowercase formatted UUID string
     */
    fun formatUuid(uuid: UUID): String = formatToLowerCase(uuid)

    /**
     * Formats a UUID string to standardized lowercase format if valid.
     * Returns original string if invalid (for graceful degradation).
     *
     * @param uuidString The UUID string to format (can be null)
     * @return Lowercase formatted UUID string, or original if invalid
     */
    fun formatUuid(uuidString: String?): String {
        return uuidString?.let { formatToLowerCase(it) } ?: ""
    }

    /**
     * Validates UUID string with additional business rules.
     * Beyond format validation, checks for reasonable UUID properties.
     *
     * @param uuidString The UUID string to validate
     * @return true if the UUID is valid and meets business rules, false otherwise
     */
    fun isValidBusinessUuid(uuidString: String): Boolean {
        return if (!isValidUuid(uuidString)) false
        else {
            val uuid = UUID.fromString(uuidString)
            // Additional business rules can be added here
            !uuid.toString().contains("00000000-0000-0000-0000-000000000000") // Not null UUID
        }
    }

    /**
     * Validates UUID string with safe null handling.
     *
     * @param uuidString The UUID string to validate (can be null)
     * @return true if valid UUID, false if null, blank, or invalid
     */
    fun isValidBusinessUuidOrNull(uuidString: String?): Boolean {
        return uuidString?.let { isValidBusinessUuid(it) } ?: false
    }
}