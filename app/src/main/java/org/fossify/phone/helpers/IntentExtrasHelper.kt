package org.fossify.phone.helpers

import android.content.Context
import android.content.Intent
import android.os.Bundle
import org.json.JSONObject

/**
 * Helper class for reading and filtering intent extras for ODK Central integration
 *
 * Handles dynamic configuration reading from ODK Collect intent extras and filters
 * out reserved keys that should not be sent to ODK Central.
 */
object IntentExtrasHelper {

    /**
     * System prefix for reserved keys (configurable)
     */
    private const val SYSTEM_PREFIX = "system_"

    /**
     * Read and filter intent extras excluding reserved keys
     *
     * @param context Application context for AdminSettingsHelper access
     * @param intent The intent containing extras from ODK Collect
     * @return Map of filtered extras with reserved keys removed
     */
    fun getFilteredExtras(
        context: Context,
        intent: Intent
    ): Map<String, Any> {
        val extras = intent.extras ?: return emptyMap()
        val adminSettingsHelper = AdminSettingsHelper(context)
        val reservedKeys = adminSettingsHelper.getReservedKeysWithDefaults()

        return extras.keySet()
            .filter { key -> !isReservedKey(key, reservedKeys) }
            .associateWith { key ->
                val value = extras.get(key)
                when (value) {
                    is String -> value
                    is Int -> value
                    is Long -> value
                    is Boolean -> value
                    is Float -> value
                    is Double -> value
                    else -> value.toString()
                }
            }
    }

    /**
     * Read and filter intent extras excluding reserved keys (legacy method)
     *
     * @param intent The intent containing extras from ODK Collect
     * @param reservedKeysConfig Configuration for custom reserved keys
     * @return Map of filtered extras with reserved keys removed
     * @deprecated Use getFilteredExtras(context, intent) for dynamic reserved keys
     */
    @Deprecated(
        message = "Use getFilteredExtras(context, intent) for dynamic reserved keys configuration",
        replaceWith = ReplaceWith("getFilteredExtras(context, intent)")
    )
    fun getFilteredExtras(
        intent: Intent,
        reservedKeysConfig: ReservedKeyConfig = ReservedKeyConfig()
    ): Map<String, Any> {
        val extras = intent.extras ?: return emptyMap()
        val reservedKeys = getReservedKeys(reservedKeysConfig)

        return extras.keySet()
            .filter { key -> !isReservedKey(key, reservedKeys) }
            .associateWith { key ->
                val value = extras.get(key)
                when (value) {
                    is String -> value
                    is Int -> value
                    is Long -> value
                    is Boolean -> value
                    is Float -> value
                    is Double -> value
                    else -> value.toString()
                }
            }
    }

    /**
     * Convert filtered extras to JSON string for sync payload
     *
     * @param context Application context for AdminSettingsHelper access
     * @param intent The intent containing extras
     * @return JSON string of filtered extras
     */
    fun extrasToJson(context: Context, intent: Intent): String {
        val filteredExtras = getFilteredExtras(context, intent)
        return JSONObject(filteredExtras).toString()
    }

    /**
     * Convert filtered extras to JSON string for sync payload (legacy method)
     *
     * @param intent The intent containing extras
     * @param reservedKeysConfig Configuration for custom reserved keys
     * @return JSON string of filtered extras
     * @deprecated Use extrasToJson(context, intent) for dynamic reserved keys
     */
    @Deprecated(
        message = "Use extrasToJson(context, intent) for dynamic reserved keys configuration",
        replaceWith = ReplaceWith("extrasToJson(context, intent)")
    )
    fun extrasToJson(intent: Intent, reservedKeysConfig: ReservedKeyConfig = ReservedKeyConfig()): String {
        val filteredExtras = getFilteredExtras(intent, reservedKeysConfig)
        return JSONObject(filteredExtras).toString()
    }

    /**
     * Extract ODK Central configuration from intent extras
     *
     * @param intent The intent containing configuration
     * @return OdkConfig with extracted values or null if required fields are missing
     */
    fun extractOdkConfig(intent: Intent): OdkConfig? {
        val extras = intent.extras ?: return null

        val baseUrl = extractString(extras, "centralBaseUrl") ?: return null
        val projectId = extractString(extras, "centralProjectId") ?: return null
        val datasetName = extractString(extras, "centralDatasetName") ?: return null

        return OdkConfig(
            baseUrl = baseUrl,
            projectId = projectId,
            datasetName = datasetName,
            instanceId = extras.getString("odkCollectInstanceId"),
            phoneNumber = extras.getString("phoneNumber")
        )
    }

    private fun extractString(bundle: Bundle, key: String): String? {
        val raw = bundle.get(key) ?: return null
        val value = when (raw) {
            is String -> raw
            is Number -> {
                val asDouble = raw.toDouble()
                if (asDouble % 1 == 0.0) {
                    asDouble.toLong().toString()
                } else {
                    raw.toString()
                }
            }
            else -> raw.toString()
        }
        return value.takeIf { it.isNotBlank() }
    }

    /**
     * Check if a key should be reserved (filtered out)
     *
     * @param key The key to check
     * @param reservedKeys Set of reserved keys
     * @return true if the key should be filtered out
     */
    private fun isReservedKey(key: String, reservedKeys: Set<String>): Boolean {
        return key in reservedKeys || key.startsWith(SYSTEM_PREFIX)
    }

    /**
     * Get the complete set of reserved keys including defaults and custom ones
     *
     * @param config Configuration for custom reserved keys
     * @return Set of all reserved keys
     */
    private fun getReservedKeys(config: ReservedKeyConfig): Set<String> {
        return AdminSettingsHelper.DEFAULT_RESERVED_KEYS + config.customReservedKeys
    }

    /**
     * Configuration for reserved keys filtering
     */
    data class ReservedKeyConfig(
        val customReservedKeys: Set<String> = emptySet()
    )

    /**
     * ODK Central configuration extracted from intent extras
     */
    data class OdkConfig(
        val baseUrl: String,
        val projectId: String,
        val datasetName: String,
        val instanceId: String? = null,
        val phoneNumber: String? = null
    )

    fun parseExtrasJson(json: String?): Map<String, Any> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { key -> obj.opt(key) ?: "" }
        }.getOrDefault(emptyMap())
    }
}
