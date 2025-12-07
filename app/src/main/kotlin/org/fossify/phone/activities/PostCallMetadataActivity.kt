package org.fossify.phone.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.toast
import org.fossify.phone.R
import org.fossify.phone.dialogs.PostCallMetadataDialog
import org.fossify.phone.helpers.PostCallMetadataHelper

/**
 * Hosts the minimal post-call metadata dialog for non-ODK calls.
 */
class PostCallMetadataActivity : SimpleActivity() {

    private val helper by lazy { PostCallMetadataHelper.getInstance(this) }
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
            val draft = helper.loadState(id) ?: PostCallMetadataHelper.FormState(id)
            PostCallMetadataDialog(
                activity = this@PostCallMetadataActivity,
                state = draft,
                onSave = { formState ->
                    lifecycleScope.launch {
                        val success = withContext(Dispatchers.IO) {
                            helper.save(id, formState)
                        }
                        if (!success) {
                            toast(R.string.survey_data_missing_call_log)
                        }
                        finish()
                    }
                }
            )
        }
    }

    companion object {
        private const val EXTRA_CALL_LOG_ID = "extra_post_call_log_id"
        @Volatile
        private var promptActive = false

        fun launch(context: Context, callLogId: UUID) {
            if (promptActive) return
            promptActive = true
            val intent = Intent(context, PostCallMetadataActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_CALL_LOG_ID, callLogId)
            }
            context.startActivity(intent)
        }

        private fun clearPromptFlag() {
            promptActive = false
        }
    }
}
