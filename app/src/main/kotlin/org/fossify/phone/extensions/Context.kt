package org.fossify.phone.extensions

import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Context.KEYGUARD_SERVICE
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.PowerManager
import android.telecom.TelecomManager
import android.util.Base64
import org.fossify.commons.extensions.launchActivityIntent
import org.fossify.commons.extensions.telecomManager
import org.fossify.commons.helpers.KEY_PHONE
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.phone.helpers.Config
import org.fossify.phone.models.SIMAccount
import org.fossify.phone.models.ODKSession
import org.fossify.phone.models.SessionState
import org.fossify.phone.models.ConcatenatedValue
import org.fossify.phone.models.OdkCallRecord
import org.fossify.phone.models.OdkCallTrackingInfo
import org.fossify.phone.extensions.clearOdkSessionState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.config: Config get() = Config.newInstance(applicationContext)

val Context.audioManager: AudioManager
    get() = getSystemService(Context.AUDIO_SERVICE) as AudioManager

val Context.powerManager: PowerManager
    get() = getSystemService(Context.POWER_SERVICE) as PowerManager

val Context.keyguardManager: KeyguardManager
    get() = getSystemService(KEYGUARD_SERVICE) as KeyguardManager

@SuppressLint("MissingPermission")
fun Context.getAvailableSIMCardLabels(): List<SIMAccount> {
    val simAccounts = mutableListOf<SIMAccount>()
    try {
        telecomManager.callCapablePhoneAccounts.forEachIndexed { index, account ->
            val phoneAccount = telecomManager.getPhoneAccount(account)
            var label = phoneAccount.label.toString()
            var address = phoneAccount.address.toString()
            if (address.startsWith("tel:") && address.substringAfter("tel:").isNotEmpty()) {
                address = Uri.decode(address.substringAfter("tel:"))
                label += " ($address)"
            }

            simAccounts.add(
                SIMAccount(
                    id = index + 1,
                    handle = phoneAccount.accountHandle,
                    label = label,
                    phoneNumber = address.substringAfter("tel:"),
                    color = phoneAccount.highlightColor
                )
            )
        }
    } catch (ignored: Exception) {
    }

    return simAccounts
}

@SuppressLint("MissingPermission")
fun Context.areMultipleSIMsAvailable(): Boolean {
    return try {
        telecomManager.callCapablePhoneAccounts.size > 1
    } catch (ignored: Exception) {
        false
    }
}

fun Context.clearMissedCalls() {
    ensureBackgroundThread {
        try {
            // notification cancellation triggers MissedCallNotifier.clearMissedCalls() which, in turn,
            // should update the database and reset the cached missed call count in MissedCallNotifier.java
            // https://android.googlesource.com/platform/packages/services/Telecomm/+/master/src/com/android/server/telecom/ui/MissedCallNotifierImpl.java#170
            telecomManager.cancelMissedCallsNotification()
        } catch (ignored: Exception) {
        }
    }
}

fun Context.canLaunchAccountsConfiguration(): Boolean {
    return Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS)
        .resolveActivity(packageManager) != null
}

fun Context.launchAccountsConfiguration() {
    startActivity(Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS))
}

fun Activity.startAddContactIntent(phoneNumber: String) {
    Intent().apply {
        action = Intent.ACTION_INSERT_OR_EDIT
        type = "vnd.android.cursor.item/contact"
        putExtra(KEY_PHONE, phoneNumber)
        launchActivityIntent(this)
    }
}

// ODK Session SharedPreferences Utilities
private fun Context.odkSessionPrefs() = getSharedPreferences(ODK_SESSION_PREFS_NAME, Context.MODE_PRIVATE)

/**
 * Saves ODK session state to SharedPreferences
 */
fun Context.saveOdkSessionState(
    isActive: Boolean = false,
    phoneNumber: String? = null,
    existingValue: String? = null,
    startTime: Long = System.currentTimeMillis(),
    variant: String? = null
) {
    odkSessionPrefs().edit().apply {
        putBoolean(ODK_SESSION_IS_ACTIVE_KEY, isActive)
        putString(ODK_SESSION_PHONE_NUMBER_KEY, phoneNumber)
        putString(ODK_SESSION_EXISTING_VALUE_KEY, existingValue)
        putLong(ODK_SESSION_START_TIME_KEY, startTime)
        putString(ODK_SESSION_VARIANT_KEY, variant)
        apply()
    }
}

/**
 * Loads ODK session state from SharedPreferences
 */
fun Context.loadOdkSessionState(): OdkSessionState? {
    return if (odkSessionPrefs().contains(ODK_SESSION_IS_ACTIVE_KEY)) {
        OdkSessionState(
            isActive = odkSessionPrefs().getBoolean(ODK_SESSION_IS_ACTIVE_KEY, false),
            phoneNumber = odkSessionPrefs().getString(ODK_SESSION_PHONE_NUMBER_KEY, null),
            existingValue = odkSessionPrefs().getString(ODK_SESSION_EXISTING_VALUE_KEY, null),
            startTime = odkSessionPrefs().getLong(ODK_SESSION_START_TIME_KEY, 0),
            variant = odkSessionPrefs().getString(ODK_SESSION_VARIANT_KEY, null)
        )
    } else {
        null
    }
}

/**
 * Clears ODK session state from SharedPreferences
 */
fun Context.saveLastOdkValue(value: String) {
    odkSessionPrefs().edit().putString("last_odk_return_value", value).apply()
    android.util.Log.d("ODK_INTEGRATION", "Saved last ODK value: $value")
}

fun Context.loadLastOdkValue(): String? {
    val value = odkSessionPrefs().getString("last_odk_return_value", null)?.takeIf { it.isNotBlank() }
    if (value != null) android.util.Log.d("ODK_INTEGRATION", "Loaded fallback last ODK value: $value")
    return value
}

fun Context.clearLastOdkValue() {
    odkSessionPrefs().edit().remove("last_odk_return_value").apply()
}

fun Context.clearOdkSessionState() {
    odkSessionPrefs().edit().apply {
        clear()
        apply()
    }
}

/**
 * Saves call records to SharedPreferences as JSON
 */
fun Context.saveCallRecords(records: List<OdkCallRecord>) {
    try {
        val json = Json.encodeToString(records)
        val encoded = Base64.encode(json.toByteArray(), Base64.DEFAULT)
        odkSessionPrefs().edit().apply {
            putString(ODK_SESSION_CALL_RECORDS_KEY, String(encoded))
            apply()
        }
    } catch (e: Exception) {
        // Handle serialization error, possibly clear corrupted data
        clearOdkSessionState()
    }
}

/**
 * Loads call records from SharedPreferences as JSON
 */
fun Context.loadCallRecords(): List<OdkCallRecord> {
    return try {
        val encoded = odkSessionPrefs().getString(ODK_SESSION_CALL_RECORDS_KEY, null)
        if (encoded != null) {
            val json = String(Base64.decode(encoded, Base64.DEFAULT))
            Json.decodeFromString<List<OdkCallRecord>>(json)
        } else {
            emptyList<OdkCallRecord>()
        }
    } catch (e: Exception) {
        // Handle deserialization error, return empty list
        emptyList<OdkCallRecord>()
    }
}

/**
 * Data class representing ODK session state
 */
data class OdkSessionState(
    val isActive: Boolean,
    val phoneNumber: String?,
    val existingValue: String?,
    val startTime: Long,
    val variant: String?
)

// JSON Serialization Utilities
private val jsonSerializer = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

/**
 * Serializes ODKSession to JSON string
 */
fun Context.serializeOdkSession(session: ODKSession): String? {
    return try {
        jsonSerializer.encodeToString(session)
    } catch (e: Exception) {
        null
    }
}

/**
 * Deserializes ODKSession from JSON string
 */
fun Context.deserializeOdkSession(json: String): ODKSession? {
    return try {
        jsonSerializer.decodeFromString<ODKSession>(json)
    } catch (e: Exception) {
        null
    }
}

/**
 * Serializes SessionState to JSON string
 */
fun Context.serializeSessionState(state: SessionState): String? {
    return try {
        jsonSerializer.encodeToString(state)
    } catch (e: Exception) {
        null
    }
}

/**
 * Deserializes SessionState from JSON string
 */
fun Context.deserializeSessionState(json: String): SessionState? {
    return try {
        jsonSerializer.decodeFromString<SessionState>(json)
    } catch (e: Exception) {
        null
    }
}

/**
 * Serializes ConcatenatedValue to JSON string
 */
fun Context.serializeConcatenatedValue(value: ConcatenatedValue): String? {
    return try {
        jsonSerializer.encodeToString(value)
    } catch (e: Exception) {
        null
    }
}

/**
 * Deserializes ConcatenatedValue from JSON string
 */
fun Context.deserializeConcatenatedValue(json: String): ConcatenatedValue? {
    return try {
        jsonSerializer.decodeFromString<ConcatenatedValue>(json)
    } catch (e: Exception) {
        null
    }
}

/**
 * Saves ODKSession to SharedPreferences as JSON
 */
fun Context.saveOdkSession(session: ODKSession) {
    try {
        val json = serializeOdkSession(session)
        if (json != null) {
            val encoded = Base64.encode(json.toByteArray(), Base64.DEFAULT)
            odkSessionPrefs().edit().apply {
                putString(ODK_SESSION_STATE_KEY, String(encoded))
                apply()
            }
        }
    } catch (e: Exception) {
        // Handle serialization error, possibly clear corrupted data
        clearOdkSessionState()
    }
}

/**
 * Loads ODKSession from SharedPreferences as JSON
 */
fun Context.loadOdkSession(): ODKSession? {
    return try {
        val encoded = odkSessionPrefs().getString(ODK_SESSION_STATE_KEY, null)
        if (encoded != null) {
            val json = String(Base64.decode(encoded, Base64.DEFAULT))
            deserializeOdkSession(json)
        } else {
            // Fallback to legacy state format if JSON not available
            val legacyState = loadOdkSessionState()
            if (legacyState != null) {
                ODKSession(
                    phoneNumber = legacyState.phoneNumber,
                    existingValue = legacyState.existingValue,
                    sessionStartTime = legacyState.startTime,
                    sessionVariant = legacyState.variant,
                    isActive = legacyState.isActive,
                    callRecords = loadCallRecords(),
                    activeCalls = emptyMap<String, OdkCallTrackingInfo>()
                )
            } else {
                null
            }
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Saves SessionState to SharedPreferences as JSON
 */
fun Context.saveSessionState(state: SessionState) {
    try {
        val json = serializeSessionState(state)
        if (json != null) {
            odkSessionPrefs().edit().apply {
                putString("session_state_details", json)
                apply()
            }
        }
    } catch (e: Exception) {
        // Handle serialization error
    }
}

/**
 * Loads SessionState from SharedPreferences as JSON
 */
fun Context.loadSessionState(): SessionState? {
    return try {
        val json = odkSessionPrefs().getString("session_state_details", null)
        if (json != null) {
            deserializeSessionState(json)
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Saves ConcatenatedValue to SharedPreferences as JSON
 */
fun Context.saveConcatenatedValue(value: ConcatenatedValue) {
    try {
        val json = serializeConcatenatedValue(value)
        if (json != null) {
            odkSessionPrefs().edit().apply {
                putString("concatenated_value", json)
                apply()
            }
        }
    } catch (e: Exception) {
        // Handle serialization error
    }
}

/**
 * Loads ConcatenatedValue from SharedPreferences as JSON
 */
fun Context.loadConcatenatedValue(): ConcatenatedValue? {
    return try {
        val json = odkSessionPrefs().getString("concatenated_value", null)
        if (json != null) {
            deserializeConcatenatedValue(json)
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}
