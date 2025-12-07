package org.fossify.phone.helpers

import android.content.Context
import android.net.Uri
import android.telecom.PhoneAccountHandle
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.fossify.commons.helpers.BaseConfig
import org.fossify.phone.extensions.getPhoneAccountHandleModel
import org.fossify.phone.extensions.putPhoneAccountHandle
import org.fossify.phone.models.SpeedDial
import androidx.core.content.edit
import java.util.Locale

class Config(context: Context) : BaseConfig(context) {
    companion object {
        fun newInstance(context: Context) = Config(context)

        const val ADMIN_PIN_HASH = "admin_pin_hash"
        const val ADMIN_PIN_FAILED_ATTEMPTS = "admin_pin_failed_attempts"
        const val ADMIN_PIN_LOCKOUT_UNTIL = "admin_pin_lockout_until"
        const val RESERVED_KEYS = "reserved_keys"
        const val CENTRAL_BASE_URL_OVERRIDE = "central_base_url_override"
        const val SYNC_LOG_RETENTION_DAYS = "sync_log_retention_days"
        const val LAST_ODK_BASE_URL = "last_odk_base_url"
        const val LAST_ODK_PROJECT_ID = "last_odk_project_id"
        const val LAST_ODK_DATASET = "last_odk_dataset"

        val DEFAULT_RESERVED_KEYS = setOf(
            "centralBaseUrl",
            "central_base_url",
            "centralProjectId",
            "central_project_id",
            "centralDatasetName",
            "central_dataset_name",
            "system_*",
            "odk_field_id",
            "auto_return_disconnect",
            "autoReturnDisconnect",
            "callingPackage",
            "value",
            "existingValue",
            "variant",
            "phone",
            "total_duration",
            "successful_calls",
            "form_valid",
            "records"
        )
    }

    private val regionHint: String by lazy {
        val telephonyManager = context.getSystemService(TelephonyManager::class.java)
        listOf(
            telephonyManager?.simCountryIso,
            telephonyManager?.networkCountryIso,
            Locale.getDefault().country
        )
            .firstOrNull { !it.isNullOrBlank() }
            ?.uppercase(Locale.US)
            .orEmpty()
    }

    fun getSpeedDialValues(): ArrayList<SpeedDial> {
        val speedDialType = object : TypeToken<List<SpeedDial>>() {}.type
        val speedDialValues = Gson().fromJson<ArrayList<SpeedDial>>(speedDial, speedDialType) ?: ArrayList(1)

        for (i in 1..9) {
            val speedDial = SpeedDial(i, "", "")
            if (speedDialValues.firstOrNull { it.id == i } == null) {
                speedDialValues.add(speedDial)
            }
        }

        return speedDialValues
    }

    fun saveCustomSIM(number: String, handle: PhoneAccountHandle) {
        prefs.edit().putPhoneAccountHandle(
            key = getKeyForCustomSIM(number),
            parcelable = handle
        ).apply()
    }

    fun getCustomSIM(number: String): PhoneAccountHandle? {
        val key = getKeyForCustomSIM(number)
        prefs.getPhoneAccountHandleModel(key, null)?.let {
            return it.toPhoneAccountHandle()
        }

        // fallback for old unstable keys. should be removed in future versions
        val migratedHandle = prefs.all.keys
            .filterIsInstance<String>()
            .filter { it.startsWith(REMEMBER_SIM_PREFIX) }
            .firstOrNull {
                @Suppress("DEPRECATION")
                PhoneNumberUtils.compare(
                    it.removePrefix(REMEMBER_SIM_PREFIX),
                    normalizeCustomSIMNumber(number)
                )
            }?.let { legacyKey ->
                prefs.getPhoneAccountHandleModel(legacyKey, null)?.let {
                    val handle = it.toPhoneAccountHandle()
                    prefs.edit {
                        remove(legacyKey)
                        putPhoneAccountHandle(key, handle)
                    }
                    handle
                }
            }

        return migratedHandle
    }

    fun removeCustomSIM(number: String) {
        prefs.edit().remove(getKeyForCustomSIM(number)).apply()
    }

    private fun getKeyForCustomSIM(number: String): String {
        return REMEMBER_SIM_PREFIX + normalizeCustomSIMNumber(number)
    }

    private fun normalizeCustomSIMNumber(number: String): String {
        val decoded = Uri.decode(number).removePrefix("tel:")
        val formatted = PhoneNumberUtils.formatNumberToE164(decoded, regionHint)
        return formatted ?: PhoneNumberUtils.normalizeNumber(decoded)
    }

    var showTabs: Int
        get() = prefs.getInt(SHOW_TABS, ALL_TABS_MASK)
        set(showTabs) = prefs.edit().putInt(SHOW_TABS, showTabs).apply()

    var groupSubsequentCalls: Boolean
        get() = prefs.getBoolean(GROUP_SUBSEQUENT_CALLS, true)
        set(groupSubsequentCalls) = prefs.edit().putBoolean(GROUP_SUBSEQUENT_CALLS, groupSubsequentCalls).apply()

    var openDialPadAtLaunch: Boolean
        get() = prefs.getBoolean(OPEN_DIAL_PAD_AT_LAUNCH, false)
        set(openDialPad) = prefs.edit().putBoolean(OPEN_DIAL_PAD_AT_LAUNCH, openDialPad).apply()

    var disableProximitySensor: Boolean
        get() = prefs.getBoolean(DISABLE_PROXIMITY_SENSOR, false)
        set(disableProximitySensor) = prefs.edit().putBoolean(DISABLE_PROXIMITY_SENSOR, disableProximitySensor).apply()

    var disableSwipeToAnswer: Boolean
        get() = prefs.getBoolean(DISABLE_SWIPE_TO_ANSWER, false)
        set(disableSwipeToAnswer) = prefs.edit().putBoolean(DISABLE_SWIPE_TO_ANSWER, disableSwipeToAnswer).apply()

    var wasOverlaySnackbarConfirmed: Boolean
        get() = prefs.getBoolean(WAS_OVERLAY_SNACKBAR_CONFIRMED, false)
        set(wasOverlaySnackbarConfirmed) = prefs.edit().putBoolean(WAS_OVERLAY_SNACKBAR_CONFIRMED, wasOverlaySnackbarConfirmed).apply()

    var dialpadVibration: Boolean
        get() = prefs.getBoolean(DIALPAD_VIBRATION, true)
        set(dialpadVibration) = prefs.edit().putBoolean(DIALPAD_VIBRATION, dialpadVibration).apply()

    var hideDialpadNumbers: Boolean
        get() = prefs.getBoolean(HIDE_DIALPAD_NUMBERS, false)
        set(hideDialpadNumbers) = prefs.edit().putBoolean(HIDE_DIALPAD_NUMBERS, hideDialpadNumbers).apply()

    var dialpadBeeps: Boolean
        get() = prefs.getBoolean(DIALPAD_BEEPS, true)
        set(dialpadBeeps) = prefs.edit().putBoolean(DIALPAD_BEEPS, dialpadBeeps).apply()

    var alwaysShowFullscreen: Boolean
        get() = prefs.getBoolean(ALWAYS_SHOW_FULLSCREEN, false)
        set(alwaysShowFullscreen) = prefs.edit().putBoolean(ALWAYS_SHOW_FULLSCREEN, alwaysShowFullscreen).apply()

    var adminPinHash: String?
        get() = prefs.getString(ADMIN_PIN_HASH, null)
        set(value) = prefs.edit().putString(ADMIN_PIN_HASH, value).apply()

    var adminPinFailedAttempts: Int
        get() = prefs.getInt(ADMIN_PIN_FAILED_ATTEMPTS, 0)
        set(value) = prefs.edit().putInt(ADMIN_PIN_FAILED_ATTEMPTS, value).apply()

    var adminPinLockoutUntil: Long
        get() = prefs.getLong(ADMIN_PIN_LOCKOUT_UNTIL, 0L)
        set(value) = prefs.edit().putLong(ADMIN_PIN_LOCKOUT_UNTIL, value).apply()

    var reservedKeys: Set<String>
        get() = prefs.getStringSet(RESERVED_KEYS, DEFAULT_RESERVED_KEYS) ?: DEFAULT_RESERVED_KEYS
        set(value) = prefs.edit().putStringSet(RESERVED_KEYS, value).apply()

    var centralBaseUrlOverride: String?
        get() = prefs.getString(CENTRAL_BASE_URL_OVERRIDE, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(CENTRAL_BASE_URL_OVERRIDE, value?.takeIf { it.isNotBlank() }).apply()

    var syncLogRetentionDays: Int
        get() = prefs.getInt(SYNC_LOG_RETENTION_DAYS, 30).coerceIn(1, 365)
        set(value) = prefs.edit().putInt(SYNC_LOG_RETENTION_DAYS, value.coerceIn(1, 365)).apply()

    var lastOdkBaseUrl: String?
        get() = prefs.getString(LAST_ODK_BASE_URL, null)
        set(value) = prefs.edit().putString(LAST_ODK_BASE_URL, value?.takeIf { it.isNotBlank() }).apply()

    var lastOdkProjectId: String?
        get() = prefs.getString(LAST_ODK_PROJECT_ID, null)
        set(value) = prefs.edit().putString(LAST_ODK_PROJECT_ID, value?.takeIf { it.isNotBlank() }).apply()

    var lastOdkDataset: String?
        get() = prefs.getString(LAST_ODK_DATASET, null)
        set(value) = prefs.edit().putString(LAST_ODK_DATASET, value?.takeIf { it.isNotBlank() }).apply()

}
