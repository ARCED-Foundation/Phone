package org.fossify.phone.helpers

import org.fossify.phone.extensions.config
import kotlin.synchronized

/**
 * Holds the latest ODK intent configuration and filtered extras so that callers outside the
 * dialpad activity can include the dynamic Central configuration when syncing call logs.
 */
object OdkIntentStateHolder {
    @Volatile
    private var state = OdkIntentState()

    fun update(config: IntentExtrasHelper.OdkConfig?, extras: Map<String, Any>, context: android.content.Context? = null) {
        state = synchronized(this) {
            OdkIntentState(
                config = config,
                extras = extras,
                hasOdkCallFlag = extras["odk_call"]?.toString()?.toBooleanStrictOrNull() == true,
                hasOdkInstanceId = extras["odkCollectInstanceId"] != null,
                timestamp = System.currentTimeMillis() // Update timestamp when state changes
            )
        }

        if (context != null && config != null) {
            val cfg = context.config
            cfg.lastOdkBaseUrl = config.baseUrl
            cfg.lastOdkProjectId = config.projectId
            cfg.lastOdkDataset = config.datasetName
        }
    }

    fun current(): OdkIntentState = state

    fun clear() {
        synchronized(this) {
            state = OdkIntentState()
        }
    }

    fun isStateValid(): Boolean {
        return state.config != null || state.hasOdkCallFlag || state.hasOdkInstanceId
    }

    fun isStateValidAndFresh(thresholdMs: Long = 30000): Boolean {
        return isStateValid() && state.isFresh(thresholdMs)
    }

    fun getPrimaryOdkIndicator(): String? {
        return when {
            state.hasOdkInstanceId -> "instance_id"
            state.hasOdkCallFlag -> "call_flag"
            state.config != null -> "config"
            else -> null
        }
    }
}

class OdkIntentState(
    val config: IntentExtrasHelper.OdkConfig? = null,
    val extras: Map<String, Any> = emptyMap(),
    val hasOdkCallFlag: Boolean = false,
    val hasOdkInstanceId: Boolean = false,
    val timestamp: Long = System.currentTimeMillis() // Track when state was set
) {
    fun isFresh(thresholdMs: Long = 30000): Boolean { // 30 second freshness threshold
        return (System.currentTimeMillis() - timestamp) <= thresholdMs
    }

    override fun toString(): String {
        return "OdkIntentState(config=${config?.baseUrl}, hasOdkCallFlag=$hasOdkCallFlag, " +
               "hasOdkInstanceId=$hasOdkInstanceId, fresh=${isFresh()}, timestamp=$timestamp, extras=${extras.keys})"
    }
}
