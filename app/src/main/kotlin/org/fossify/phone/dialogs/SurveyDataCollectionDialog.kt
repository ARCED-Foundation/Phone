package org.fossify.phone.dialogs

import androidx.appcompat.app.AlertDialog
import androidx.annotation.VisibleForTesting
import androidx.core.widget.doAfterTextChanged
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.fossify.commons.extensions.*
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.databinding.DialogSurveyDataCollectionBinding
import org.fossify.phone.helpers.SurveyDataCompletionHelper

/**
 * Dialog prompting the user to enter survey details after an incoming call.
 *
 * Auto-saves drafts when fields change or when the dialog is dismissed so that
 * in-progress data survives process deaths or navigation away from the screen.
 */
class SurveyDataCollectionDialog(
    private val activity: SimpleActivity,
    private val draft: SurveyDataCompletionHelper.SurveyDraft,
    private val autoSaveScope: CoroutineScope,
    private val onSaveDraft: (surveyId: String?, notes: String?) -> Unit,
    private val onComplete: (surveyId: String, notes: String?) -> Unit,
    private val onSkip: () -> Unit
) {

    private var saveJob: Job? = null
    private val completed = AtomicBoolean(false)

    @VisibleForTesting
    internal var alertDialog: AlertDialog? = null

    init {
        val binding = DialogSurveyDataCollectionBinding.inflate(activity.layoutInflater).apply {
            surveyDataId.setText(draft.surveyId ?: "")
            surveyDataNotes.setText(draft.additionalNotes ?: "")
        }

        binding.surveyDataId.doAfterTextChanged {
            binding.surveyDataIdHint.error = null
            scheduleAutoSave(binding)
        }
        binding.surveyDataNotes.doAfterTextChanged {
            scheduleAutoSave(binding)
        }

        val builder = activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.survey_skip, null)
            .setNeutralButton(R.string.survey_save_draft, null)

        activity.setupDialogStuff(binding.root, builder, R.string.survey_data_title) { dialog ->
            alertDialog = dialog
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val surveyId = binding.surveyDataId.value
                if (surveyId.isBlank()) {
                    binding.surveyDataIdHint.error = activity.getString(R.string.survey_id_required)
                    return@setOnClickListener
                }
                completed.set(true)
                onComplete(surveyId.trim(), binding.surveyDataNotes.value.ifBlank { null })
                dialog.dismiss()
            }

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                saveDraft(binding)
                dialog.dismiss()
            }

            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                saveDraft(binding)
                onSkip()
                dialog.dismiss()
            }

            dialog.setOnDismissListener {
                if (!completed.get()) {
                    saveDraft(binding)
                }
                saveJob?.cancel()
            }
        }
    }

    private fun scheduleAutoSave(binding: DialogSurveyDataCollectionBinding) {
        saveJob?.cancel()
        saveJob = autoSaveScope.launch {
            delay(350)
            saveDraft(binding)
        }
    }

    private fun saveDraft(binding: DialogSurveyDataCollectionBinding) {
        onSaveDraft(binding.surveyDataId.value, binding.surveyDataNotes.value)
    }
}
