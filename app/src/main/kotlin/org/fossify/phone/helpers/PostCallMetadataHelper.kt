package org.fossify.phone.helpers

import android.content.Context
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.phone.database.AppDatabase
import org.fossify.phone.helpers.CallSyncPayload.Companion.KEY_DATA
import org.fossify.phone.work.WorkManagerHelper
import org.fossify.phone.extensions.config
import org.fossify.phone.helpers.IntentExtrasHelper.OdkConfig
import org.fossify.phone.models.SyncLogStatus
import org.fossify.phone.models.PendingSync
import org.fossify.phone.helpers.SyncLogHelper
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the minimal post-call form data for non-ODK calls and refreshes any pending
 * sync payload so the additional fields are included on upload.
 */
class PostCallMetadataHelper private constructor(
    private val context: Context,
    private val db: AppDatabase?
) {
    private val callLogDao by lazy { db?.callLogDao() }
    private val pendingSyncDao by lazy { db?.pendingSyncDao() }
    private val syncLogHelper by lazy { SyncLogHelper(context) }
    private val syncLogDao by lazy { db?.syncLogDao() }

    data class FormState(
        val callLogId: UUID,
        val surveyCall: Boolean = true,
        val uniqueId: String? = null,
        val enumeratorId: String? = null,
        val note: String? = null
    )

    companion object {
        @Volatile
        private var instance: PostCallMetadataHelper? = null

        fun getInstance(context: Context): PostCallMetadataHelper {
            return instance ?: synchronized(this) {
                instance ?: try {
                    PostCallMetadataHelper(
                        context.applicationContext,
                        AppDatabase.getInstance(context)
                    ).also { instance = it }
                } catch (e: Exception) {
                    // Fall back to a helper that safely no-ops if the DB is unavailable
                    PostCallMetadataHelper(context.applicationContext, null).also { instance = it }
                }
            }
        }
    }

    suspend fun loadState(callLogId: UUID): FormState? = withContext(Dispatchers.IO) {
        try {
            val callLog = callLogDao?.getById(callLogId) ?: return@withContext null
            val extrasJson = runCatching { JSONObject(callLog.extrasJson) }.getOrElse { JSONObject() }
            val surveyCall = extrasJson.optString("survey_call", "yes").equals("yes", ignoreCase = true)

            FormState(
                callLogId = callLogId,
                surveyCall = surveyCall,
                uniqueId = callLog.surveyId,
                enumeratorId = callLog.enumeratorId,
                note = callLog.additionalNotes
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun save(callLogId: UUID, state: FormState): Boolean = withContext(Dispatchers.IO) {
        val database = db ?: return@withContext false
        val trimmedUniqueId = state.uniqueId?.trim()?.takeIf { it.isNotEmpty() }
        val trimmedEnumerator = state.enumeratorId?.trim()?.takeIf { it.isNotEmpty() }
        val trimmedNote = state.note?.trim()?.takeIf { it.isNotEmpty() }

        try {
            val callLog = database.callLogDao().getById(callLogId) ?: return@withContext false
            val extrasJson = runCatching { JSONObject(callLog.extrasJson) }.getOrElse { JSONObject() }
            extrasJson.put("survey_call", if (state.surveyCall) "yes" else "no")
            trimmedUniqueId?.let { extrasJson.put("unique_id", it) }

            callLogDao?.updatePostCallMetadata(
                id = callLogId,
                enumeratorId = trimmedEnumerator,
                surveyId = trimmedUniqueId,
                additionalNotes = trimmedNote,
                extrasJson = extrasJson.toString()
            )

            val updatedLog = database.callLogDao().getById(callLogId)
            val hasPending = refreshPendingSync(
                callLogId = callLogId,
                uniqueId = trimmedUniqueId,
                enumeratorId = trimmedEnumerator,
                note = trimmedNote,
                surveyCall = state.surveyCall
            )

            // Check variables for sync creation
            val syncAlreadyExists = pendingSyncDao?.getByCallLog(callLogId) != null
            val isFormCompleted = !trimmedUniqueId.isNullOrBlank() && !trimmedEnumerator.isNullOrBlank() && !trimmedNote.isNullOrBlank()

            // Only create new sync if no pending sync exists and we have an updated log
            // This prevents duplicate sync creation between CallService and PostCallMetadataHelper
            if (updatedLog != null) {
                android.util.Log.d("PostCallMetadataHelper", "Sync creation check - hasPending: $hasPending, syncAlreadyExists: $syncAlreadyExists, formCompleted: $isFormCompleted, callLogId: $callLogId")

                // Only create sync if form is completed and no sync already exists
                if (!syncAlreadyExists && !hasPending && isFormCompleted) {
                    android.util.Log.d("PostCallMetadataHelper", "Creating new pending sync for completed form: $callLogId")
                    createPendingSyncIfMissing(
                        callLog = updatedLog,
                        extrasJson = extrasJson.toString(),
                        surveyCall = state.surveyCall,
                        uniqueId = trimmedUniqueId,
                        enumeratorId = trimmedEnumerator,
                        note = trimmedNote
                    )
                } else {
                    android.util.Log.d("PostCallMetadataHelper", "Skipping sync creation${if (!isFormCompleted) " - form not completed" else if (syncAlreadyExists) " - sync already exists" else " - has pending"}")
                }
            }

            // Ensure sync runs as soon as metadata is complete
            if (isFormCompleted) {
                android.util.Log.d("PostCallMetadataHelper", "Enqueuing sync work after form completion (hasPending=$hasPending, syncAlreadyExists=$syncAlreadyExists): $callLogId")
                WorkManagerHelper.enqueueOdkSyncWork(context, initialDelayMs = 0L, forceNow = true)
            } else {
                android.util.Log.d("PostCallMetadataHelper", "Not enqueuing sync work - form not completed")
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun refreshPendingSync(
        callLogId: UUID,
        uniqueId: String?,
        enumeratorId: String?,
        note: String?,
        surveyCall: Boolean
    ): Boolean {
        val pending = pendingSyncDao?.getByCallLog(callLogId) ?: return false
        val payload = runCatching { JSONObject(pending.syncPayloadJson) }.getOrElse { JSONObject() }
        val dataObject = payload.optJSONObject(KEY_DATA) ?: JSONObject()

        payload.remove("await_metadata")
        dataObject.put("survey_call", if (surveyCall) "yes" else "no")
        uniqueId?.let {
            dataObject.put("survey_id", it)
            dataObject.put("unique_id", it)
        }
        enumeratorId?.let { dataObject.put("enumerator_id", it) }
        note?.let { dataObject.put("additional_notes", it) }

        payload.put(KEY_DATA, dataObject)
        pendingSyncDao?.insert(
            pending.reset().copy(syncPayloadJson = payload.toString())
        )
        return true
    }

    private fun resolvePersistedConfig(): OdkConfig? {
        val cfg = context.config
        val base = cfg.lastOdkBaseUrl
        val project = cfg.lastOdkProjectId
        val dataset = cfg.lastOdkDataset
        if (!base.isNullOrBlank() && !project.isNullOrBlank() && !dataset.isNullOrBlank()) {
            return OdkConfig(base, project, dataset)
        }

        val lastSuccess = runCatching { syncLogDao?.getLastSuccessfulConfig() }.getOrNull()
        return lastSuccess?.let {
            // Persist for future reuse
            context.config.lastOdkBaseUrl = it.baseUrl
            context.config.lastOdkProjectId = it.projectId
            context.config.lastOdkDataset = it.datasetName
            OdkConfig(it.baseUrl, it.projectId, it.datasetName)
        }
    }

    private fun parseExtrasMap(extrasJson: String?): MutableMap<String, Any> {
        val map = mutableMapOf<String, Any>()
        if (extrasJson.isNullOrBlank()) return map
        runCatching { JSONObject(extrasJson) }.getOrNull()?.let { json ->
            json.keys().forEach { key ->
                val value = json.get(key)
                when (value) {
                    is JSONObject -> map[key] = value.toString()
                    is JSONArray -> map[key] = value.toString()
                    else -> map[key] = value
                }
            }
        }
        return map
    }

    suspend fun backfillPendingWithConfig(config: OdkConfig) = withContext(Dispatchers.IO) {
        val callLogs = callLogDao?.getUnsynced() ?: return@withContext
        var queued = false
        callLogs.forEach { log ->
            val hasPending = pendingSyncDao?.getByCallLog(log.callLogId) != null
            if (hasPending) return@forEach

            val extras = parseExtrasMap(log.extrasJson)
            val payload = CallSyncPayloadBuilder.build(log, extras, config) ?: return@forEach
            val pending = PendingSync(
                callLogId = log.callLogId,
                syncPayloadJson = JSONObject(payload.toPersistenceMap()).toString()
            )
            pendingSyncDao?.insert(pending)
            syncLogHelper.logStatus(log, payload, SyncLogStatus.PENDING, "Queued with restored config")
            queued = true
        }
        if (queued) {
            WorkManagerHelper.enqueueOdkSyncWork(context, initialDelayMs = 0L, forceNow = true)
        }
    }

    private suspend fun createPendingSyncIfMissing(
        callLog: org.fossify.phone.models.CallLog,
        extrasJson: String,
        surveyCall: Boolean,
        uniqueId: String?,
        enumeratorId: String?,
        note: String?
    ) {
        val extras = parseExtrasMap(extrasJson).apply {
            put("survey_call", if (surveyCall) "yes" else "no")
            uniqueId?.let {
                put("survey_id", it)
                put("unique_id", it)
            }
            enumeratorId?.let { put("enumerator_id", it) }
            note?.let { put("additional_notes", it) }
        }

        val config = resolvePersistedConfig()
        val payload = config?.let { CallSyncPayloadBuilder.build(callLog, extras, it) }
        if (config == null || payload == null) {
            // Leave call unsynced and requeue when config becomes available
            pendingSyncDao?.deleteForCallLog(callLog.callLogId)
            val pending = PendingSync(
                callLogId = callLog.callLogId,
                syncPayloadJson = JSONObject(mapOf("await_config" to true, "call_log_id" to callLog.callLogId.toString())).toString()
            )
            pendingSyncDao?.insert(pending)
            syncLogHelper.logStatus(callLog, null, SyncLogStatus.PENDING, "Waiting for ODK config")
            return
        }

        pendingSyncDao?.deleteForCallLog(callLog.callLogId)
        val pending = PendingSync(
            callLogId = callLog.callLogId,
            syncPayloadJson = JSONObject(payload.toPersistenceMap()).toString()
        )
        pendingSyncDao?.insert(pending)
        syncLogHelper.logStatus(callLog, payload, SyncLogStatus.PENDING, "Queued for sync")
    }
}
