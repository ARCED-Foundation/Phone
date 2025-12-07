package org.fossify.phone.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.toast
import org.fossify.phone.R
import org.fossify.phone.dialogs.SurveyDataCollectionDialog
import org.fossify.phone.helpers.SurveyDataCompletionHelper

/**
 * Thin activity host that shows the survey data collection dialog.
 * Launched after incoming calls to gather survey metadata and ensures drafts
 * are restored if the user was interrupted previously.
 */
class SurveyDataCollectionActivity : SimpleActivity() {

    private val helper by lazy { SurveyDataCompletionHelper.getInstance(this) }
    private var callLogId: UUID? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        callLogId = savedInstanceState?.getSerializable(EXTRA_CALL_LOG_ID) as? UUID
            ?: intent.getSerializableExtra(EXTRA_CALL_LOG_ID) as? UUID

        if (callLogId == null) {
            finish()
            clearPromptFlag()
            return
        }

        launchDialog(callLogId!!)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        callLogId?.let { outState.putSerializable(EXTRA_CALL_LOG_ID, it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        clearPromptFlag()
    }

    private fun launchDialog(id: UUID) {
        lifecycleScope.launch {
            val draft = helper.getDraft(id) ?: SurveyDataCompletionHelper.SurveyDraft(id)
            SurveyDataCollectionDialog(
                activity = this@SurveyDataCollectionActivity,
                draft = draft,
                autoSaveScope = lifecycleScope,
                onSaveDraft = { surveyId, notes ->
                    lifecycleScope.launch(Dispatchers.IO) { helper.saveDraft(id, surveyId, notes) }
                },
                onComplete = { surveyId, notes ->
                    lifecycleScope.launch {
                        val success = helper.completeSurvey(id, surveyId, notes)
                        if (!success) {
                            toast(R.string.survey_data_missing_call_log)
                        }
                        finish()
                    }
                },
                onSkip = { finish() }
            )
        }
    }

    companion object {
        private const val EXTRA_CALL_LOG_ID = "extra_call_log_id"
        @Volatile
        private var promptActive = false

        fun launch(context: Context, callLogId: UUID) {
            if (promptActive) return
            promptActive = true
            val intent = Intent(context, SurveyDataCollectionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_CALL_LOG_ID, callLogId)
            }
            context.startActivity(intent)
        }

        fun launchPendingIfAny(context: Context) {
            if (promptActive) return
            val appContext = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val helper = SurveyDataCompletionHelper.getInstance(appContext)
                    val pending = helper.getOldestIncomplete() ?: return@launch
                    withContext(Dispatchers.Main) {
                        launch(appContext, pending.callLogId)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SurveyDataCollectionActivity", "Failed to launch pending survey data collection", e)
                    // Don't crash the app if survey data collection fails
                }
            }
        }

        private fun clearPromptFlag() {
            promptActive = false
        }
    }
}
