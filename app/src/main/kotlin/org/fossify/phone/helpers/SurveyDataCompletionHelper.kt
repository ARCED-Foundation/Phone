package org.fossify.phone.helpers

import android.content.Context
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.annotation.VisibleForTesting
import org.fossify.phone.database.AppDatabase
import org.fossify.phone.helpers.CallSyncPayload
import org.fossify.phone.models.CallLog
import org.fossify.phone.models.PartialSurveyData
import org.fossify.phone.work.WorkManagerHelper
import org.json.JSONObject

/**
 * Coordinates persistence of survey metadata for incoming calls.
 *
 * - Stores partial survey data drafts to recover after interruptions
 * - Writes completed survey data back to CallLog for syncing
 * - Refreshes pending sync payloads to include survey fields
 */
class SurveyDataCompletionHelper private constructor(
    private val context: Context,
    private val db: AppDatabase?
    ) {

    private val callLogDao by lazy { db?.callLogDao() }
    private val partialDao by lazy { db?.partialSurveyDataDao() }
    private val pendingSyncDao by lazy { db?.pendingSyncDao() }

    data class SurveyDraft(
        val callLogId: UUID,
        val surveyId: String? = null,
        val additionalNotes: String? = null
    )

    companion object {
        @Volatile
        private var instance: SurveyDataCompletionHelper? = null

        fun getInstance(context: Context): SurveyDataCompletionHelper {
            return instance ?: synchronized(this) {
                instance ?: try {
                    SurveyDataCompletionHelper(
                        context.applicationContext,
                        AppDatabase.getInstance(context)
                    ).also { instance = it }
                } catch (e: Exception) {
                    android.util.Log.e("SurveyDataCompletionHelper", "Failed to create database instance", e)
                    // Create a safe fallback that prevents crashes
                    SurveyDataCompletionHelper.createFallbackInstance(context.applicationContext)
                        .also { instance = it }
                }
            }
        }

        private fun createFallbackInstance(context: Context): SurveyDataCompletionHelper {
            android.util.Log.w("SurveyDataCompletionHelper", "Creating fallback instance without database")
            // Create a helper with null database that safely handles all operations
            return SurveyDataCompletionHelper(context, null)
        }

        @VisibleForTesting
        internal fun createForTest(context: Context, database: AppDatabase): SurveyDataCompletionHelper {
            return SurveyDataCompletionHelper(context.applicationContext, database)
        }
    }

    /**
     * Load any existing draft data for a call log. Falls back to saved survey
     * data on the CallLog if it already exists.
     */
    suspend fun getDraft(callLogId: UUID): SurveyDraft? = withContext(Dispatchers.IO) {
        try {
            val existing = partialDao?.getByCallLog(callLogId)
            if (existing != null) {
                return@withContext SurveyDraft(
                    callLogId = callLogId,
                    surveyId = existing.surveyId,
                    additionalNotes = existing.additionalNotes
                )
        }

            val callLog = callLogDao?.getById(callLogId) ?: return@withContext null
            SurveyDraft(callLogId, callLog.surveyId, callLog.additionalNotes)
        } catch (e: Exception) {
            android.util.Log.e("SurveyDataCompletionHelper", "Failed to get draft for callLogId: $callLogId", e)
            null
        }
    }

    /**
     * Retrieve the oldest incomplete survey draft (used to re-prompt after interruption).
     */
    suspend fun getOldestIncomplete(): SurveyDraft? = withContext(Dispatchers.IO) {
        try {
            partialDao?.getOldestIncomplete()?.let {
                SurveyDraft(it.callLogId, it.surveyId, it.additionalNotes)
            }
        } catch (e: Exception) {
            android.util.Log.e("SurveyDataCompletionHelper", "Failed to get oldest incomplete survey", e)
            null
        }
    }

    /**
     * Save a draft record to preserve user input if the dialog is dismissed or
     * the app is interrupted.
     */
    suspend fun saveDraft(callLogId: UUID, surveyId: String?, notes: String?) = withContext(Dispatchers.IO) {
        val trimmedSurveyId = surveyId?.takeIf { it.isNotBlank() }
        val trimmedNotes = notes?.takeIf { it.isNotBlank() }
        if (trimmedSurveyId == null && trimmedNotes == null) {
            // Nothing meaningful to store, clear any previous drafts
            partialDao?.deleteForCallLog(callLogId)
            return@withContext
        }

        val draft = PartialSurveyData(
            callLogId = callLogId,
            surveyId = trimmedSurveyId,
            additionalNotes = trimmedNotes,
            isComplete = false
        )
        partialDao?.insert(draft)
    }

    /**
     * Persist completed survey data to the CallLog and refresh pending sync
     * payload so the data is included on the next upload attempt.
     */
    suspend fun completeSurvey(callLogId: UUID, surveyId: String, notes: String?): Boolean = withContext(Dispatchers.IO) {
        val callLog = callLogDao?.getById(callLogId) ?: return@withContext false
        val trimmedNotes = notes?.takeIf { it.isNotBlank() }

        callLogDao?.updateSurveyData(callLogId, surveyId, trimmedNotes)
        partialDao?.deleteForCallLog(callLogId)
        refreshPendingSync(callLog.copy(surveyId = surveyId, additionalNotes = trimmedNotes))

        // Re-enqueue sync to push updated data
        WorkManagerHelper.enqueueOdkSyncWork(context, initialDelayMs = 0)
        true
    }

    private suspend fun refreshPendingSync(callLog: CallLog) {
        val pending = pendingSyncDao?.getByCallLog(callLog.callLogId) ?: return
        val updatedPayload = runCatching { JSONObject(pending.syncPayloadJson) }
            .getOrElse { JSONObject() }

        val dataObject = (updatedPayload.optJSONObject(CallSyncPayload.KEY_DATA) ?: JSONObject())

        callLog.surveyId?.let { dataObject.put("survey_id", it) }
        callLog.additionalNotes?.let { dataObject.put("additional_notes", it) }

        updatedPayload.put(CallSyncPayload.KEY_DATA, dataObject)

        pendingSyncDao?.insert(
            pending.reset().copy(
                syncPayloadJson = updatedPayload.toString()
            )
        )
    }
}
