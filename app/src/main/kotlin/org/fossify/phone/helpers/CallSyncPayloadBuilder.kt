package org.fossify.phone.helpers

import org.fossify.phone.models.CallLog

/**
 * Data required to send an entity creation request to ODK Central and to persist the payload
 * for retry attempts.
 */
data class CallSyncPayload(
    val label: String,
    val data: Map<String, String>,
    val baseUrl: String,
    val projectId: String,
    val datasetName: String
) {
    fun toEntityRequestBody(): Map<String, Any> = mapOf(
        KEY_LABEL to label,
        KEY_DATA to data
    )

    fun toPersistenceMap(): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        result.putAll(toEntityRequestBody())
        result[KEY_BASE_URL] = baseUrl
        result[KEY_PROJECT_ID] = projectId
        result[KEY_DATASET] = datasetName
        return result
    }

    companion object {
        const val KEY_LABEL = "label"
        const val KEY_DATA = "data"
        const val KEY_BASE_URL = "central_base_url"
        const val KEY_PROJECT_ID = "project_id"
        const val KEY_DATASET = "dataset"

        fun fromPersistenceMap(map: Map<String, Any>): CallSyncPayload? {
            val baseUrl = map[KEY_BASE_URL]?.toString()?.takeIf { it.isNotBlank() } ?: return null
            val projectId = map[KEY_PROJECT_ID]?.toString()?.takeIf { it.isNotBlank() } ?: return null
            val datasetName = map[KEY_DATASET]?.toString()?.takeIf { it.isNotBlank() } ?: return null
            val label = map[KEY_LABEL]?.toString()?.takeIf { it.isNotBlank() } ?: return null

            val rawData = map[KEY_DATA]
            val data = when (rawData) {
                is Map<*, *> -> rawData.mapNotNull { (key, value) ->
                    val name = key as? String ?: return@mapNotNull null
                    name to (value?.toString() ?: "")
                }.toMap()
                else -> emptyMap()
            }

            return CallSyncPayload(label, data, baseUrl, projectId, datasetName)
        }
    }
}

/**
 * Builds the payload that will be sent to ODK Central or stored for later sync.
 */
object CallSyncPayloadBuilder {
    fun build(
        callLog: CallLog,
        extras: Map<String, Any>,
        odkConfig: IntentExtrasHelper.OdkConfig?
    ): CallSyncPayload? {
        val config = odkConfig ?: return null

        val data = mutableMapOf<String, String>()
        fun MutableMap<String, String>.putString(key: String, value: Any?) {
            val cleaned = sanitizeValue(value)
            if (cleaned != null) this[key] = cleaned
        }

        data.putString("call_log_id", callLog.callLogId)
        data.putString("direction", callLog.direction)
        data.putString("call_start_utc", callLog.callStartUtc)
        data.putString("call_end_utc", callLog.callEndUtc)
        data.putString("duration_seconds", callLog.durationSeconds)
        data.putString("outcome", callLog.outcome)
        data.putString("phoneNumber", callLog.phoneNumber ?: "")
        callLog.attemptNumber?.let { data["attempt"] = it.toString() }
        callLog.outcomeDetail?.let { data["outcome_detail"] = it }
        callLog.instanceId?.let { data["instance_id"] = it }
        callLog.enumeratorId?.let { data["enumerator_id"] = it }
        callLog.surveyId?.let { data["survey_id"] = it }
        callLog.additionalNotes?.let { data["additional_notes"] = it }

        extras.forEach { (key, value) ->
            normalizeExtraKey(key)?.let { normalized ->
                if (!data.containsKey(normalized)) {
                    sanitizeValue(value)?.let { cleaned ->
                        data[normalized] = cleaned
                    }
                }
            }
        }

        val label = createLabel(callLog)
        return CallSyncPayload(
            label = label,
            data = data,
            baseUrl = config.baseUrl,
            projectId = config.projectId,
            datasetName = config.datasetName
        )
    }

    private fun createLabel(callLog: CallLog): String {
        val phone = callLog.phoneNumber?.takeIf { it.isNotBlank() }
        val parts = mutableListOf<String>()
        phone?.let { parts.add(it) }
        parts.add(callLog.direction)
        parts.add(callLog.callStartUtc.toString())
        return parts.joinToString(" | ").takeIf { it.isNotBlank() } ?: callLog.callLogId.toString()
    }

        private fun normalizeExtraKey(key: String): String? {
            return when (key) {
                "odk_call" -> null
                "field_id" -> "fieldId"
                "total_duration",
                "successful_calls",
                "form_valid",
                "records",
            "centralBaseUrl",
            "central_base_url",
            "centralProjectId",
            "central_project_id",
            "centralDatasetName",
            "central_dataset_name" -> null
            else -> key
        }
    }

    private fun sanitizeValue(value: Any?): String? {
        if (value == null) return null
        return when (value) {
            is Number -> {
                val doubleVal = value.toDouble()
                if (doubleVal % 1 == 0.0) {
                    doubleVal.toLong().toString()
                } else {
                    value.toString()
                }
            }
            else -> value.toString()
        }
    }
}
