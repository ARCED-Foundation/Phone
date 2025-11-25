package org.fossify.phone.extensions

import android.content.Intent
import android.net.Uri
import org.fossify.phone.helpers.Config

/**
 * ODK Intent Validation Utilities
 * Provides utilities for validating and processing ODK-specific intents
 */

/**
 * Checks if the intent is an ODK launch intent
 */
fun Intent.isOdkIntent(): Boolean {
    val action = this.action ?: return false
    val result = when {
        action.startsWith("org.fossify.phone") -> true
        action.startsWith("org.opendatakit.phone") -> true
        (action == "android.intent.action.VIEW" || action == "android.intent.action.DIAL") && (hasExtra("phone") || (data?.scheme == "tel")) -> true
        else -> false
    }
    android.util.Log.d("ODK_INTEGRATION", "isOdkIntent: action=$action, result=$result")
    return result
}

/**
 * Validates that the intent has required ODK structure
 */
fun Intent.isValidOdkIntent(): Boolean {
    val isOdk = isOdkIntent()
    if (!isOdk) return false

    // For ODK integration, we're more flexible - just need phone number or value
    val hasPhoneExtra = hasExtra("phone")
    val hasValueExtra = hasExtra("value")
    val hasUriData = data != null && data.toString().contains("tel:")

    android.util.Log.d("ODK_INTEGRATION", "isValidOdkIntent: hasPhoneExtra=$hasPhoneExtra, hasValueExtra=$hasValueExtra, hasUriData=$hasUriData")

    // Valid if it has at least one of: phone extra, value extra, or tel: URI
    val result = hasPhoneExtra || hasValueExtra || hasUriData
    android.util.Log.d("ODK_INTEGRATION", "isValidOdkIntent final result: $result")
    return result
}

/**
 * Extracts phone number from ODK intent with proper validation
 */
fun Intent.getOdkPhoneNumber(): String? {
    // Try phone extra first
    if (hasExtra("phone")) {
        val phone = getStringExtra("phone")
        // Validate and clean phone number
        phone?.takeIf { it.isNotBlank() }?.let { cleanPhoneNumber(it) }?.also {
            android.util.Log.d("ODK_INTEGRATION", "Got phone from extra: $it")
            return it
        }
    }

    // Try to extract from URI data
    data?.let { uri ->
        uri.scheme?.let { scheme ->
            if (scheme == "tel") {
                val phoneNumber = uri.host + uri.path
                val cleaned = cleanPhoneNumber(phoneNumber)
                android.util.Log.d("ODK_INTEGRATION", "Got phone from URI: $cleaned")
                return cleaned
            }
        }
    }

    android.util.Log.d("ODK_INTEGRATION", "No phone number found in intent")
    return null
}

/**
 * Extracts existing value from ODK intent
 */
fun Intent.getOdkExistingValue(): String? {
    return if (hasExtra("value")) {
        val value = getStringExtra("value")
        value?.takeIf { it.isNotBlank() }
    } else {
        null
    }
}

/**
 * Determines the build variant from ODK intent action
 */
fun Intent.getOdkBuildVariant(): String {
    return when {
        action?.contains("debug") == true -> "debug"
        action?.contains("release") == true -> "release"
        else -> "standard"
    }
}

/**
 * Validates that the phone number is in E.164 format
 */
fun isValidPhoneNumber(phoneNumber: String?): Boolean {
    if (phoneNumber == null || phoneNumber.isBlank()) return false

    // Basic E.164 validation
    // + followed by 1-15 digits
    return phoneNumber.matches(Regex("^\\+[1-9]\\d{1,14}$"))
}

/**
 * Cleans phone number by removing tel: prefix and whitespace
 */
fun cleanPhoneNumber(phoneNumber: String): String {
    return phoneNumber
        .replace("tel:", "")
        .trim()
}

/**
 * Validates ODK intent and returns structured result
 */
fun Intent.validateOdkIntent(): OdkIntentValidationResult {
    val isValid = isValidOdkIntent()
    val phoneNumber = if (isValid) getOdkPhoneNumber() else null
    val existingValue = if (isValid) getOdkExistingValue() else null
    val variant = if (isValid) getOdkBuildVariant() else "invalid"

    return OdkIntentValidationResult(
        isValid = isValid,
        phoneNumber = phoneNumber,
        existingValue = existingValue,
        variant = variant,
        errors = if (!isValid) listOf("Invalid ODK intent structure") else emptyList()
    )
}

/**
 * Creates a result intent for ODK with the specified value
 */
fun Intent.createOdkResult(value: String): Intent {
    return Intent().apply {
        putExtra("value", value)
        addCategory(Intent.CATEGORY_DEFAULT)
    }
}

/**
 * Gets the result code for ODK completion
 */
fun Intent.getOdkResultCode(): Int {
    return when {
        hasExtra("value") -> android.app.Activity.RESULT_OK
        else -> android.app.Activity.RESULT_CANCELED
    }
}

/**
 * Data class representing ODK intent validation result
 */
data class OdkIntentValidationResult(
    val isValid: Boolean,
    val phoneNumber: String?,
    val existingValue: String?,
    val variant: String,
    val errors: List<String>
) {
    /**
     * Whether this is a valid debug intent
     */
    val isDebugIntent: Boolean
        get() = isValid && variant == "debug"

    /**
     * Whether this is a valid release intent
     */
    val isReleaseIntent: Boolean
        get() = isValid && variant == "release"

    /**
     * Whether this intent contains a phone number
     */
    val hasPhoneNumber: Boolean
        get() = phoneNumber != null && phoneNumber.isNotBlank()

    /**
     * Whether this intent contains an existing value
     */
    val hasExistingValue: Boolean
        get() = existingValue != null && existingValue.isNotBlank()

    /**
     * Whether this intent has both phone number and existing value
     */
    val hasBothExtras: Boolean
        get() = hasPhoneNumber && hasExistingValue

    /**
     * Whether this intent has no extras
     */
    val hasNoExtras: Boolean
        get() = !hasPhoneNumber && !hasExistingValue

    /**
     * Returns validation errors or empty list if valid
     */
    fun getValidationErrors(): List<String> {
        return if (isValid) {
            emptyList()
        } else {
            errors
        }
    }

    /**
     * Creates ODK session parameters from validation result
     */
    fun toOdkSessionParams(): OdkSessionParams {
        return OdkSessionParams(
            phoneNumber = phoneNumber,
            existingValue = existingValue,
            variant = variant.takeIf { it != "invalid" }
        )
    }
}

/**
 * Data class representing ODK session parameters
 */
data class OdkSessionParams(
    val phoneNumber: String?,
    val existingValue: String?,
    val variant: String?
) {
    /**
     * Whether all parameters are valid
     */
    val isValid: Boolean
        get() = variant != null && variant != "invalid"

    /**
     * Whether this session should start with a phone number
     */
    val startWithPhoneNumber: Boolean
        get() = phoneNumber != null && phoneNumber.isNotBlank()

    /**
     * Whether this session should preserve an existing value
     */
    val preserveExistingValue: Boolean
        get() = existingValue != null && existingValue.isNotBlank()

    /**
     * The display name for the variant
     */
    val variantDisplayName: String
        get() = when (variant) {
            "debug" -> "Debug"
            "release" -> "Release"
            else -> "Unknown"
        }
}