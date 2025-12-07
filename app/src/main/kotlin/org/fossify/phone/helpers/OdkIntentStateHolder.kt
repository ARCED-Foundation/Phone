package org.fossify.phone.helpers

import org.fossify.phone.extensions.config

/**
 * Holds the latest ODK intent configuration and filtered extras so that callers outside the
 * dialpad activity can include the dynamic Central configuration when syncing call logs.
 */
object OdkIntentStateHolder {
    @Volatile
    private var state = OdkIntentState()

    fun update(config: IntentExtrasHelper.OdkConfig?, extras: Map<String, Any>, context: android.content.Context? = null) {
        state = OdkIntentState(
            config = config,
            extras = extras
        )
        if (context != null && config != null) {
            val cfg = context.config
            cfg.lastOdkBaseUrl = config.baseUrl
            cfg.lastOdkProjectId = config.projectId
            cfg.lastOdkDataset = config.datasetName
        }
    }

    fun current(): OdkIntentState = state

    fun clear() {
        state = OdkIntentState()
    }
}

data class OdkIntentState(
    val config: IntentExtrasHelper.OdkConfig? = null,
    val extras: Map<String, Any> = emptyMap()
)
