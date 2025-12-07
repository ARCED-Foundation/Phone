package org.fossify.phone.dialogs

import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.value
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.databinding.DialogPostCallMetadataBinding
import org.fossify.phone.extensions.getAlertDialogBuilder
import org.fossify.phone.helpers.PostCallMetadataHelper

class PostCallMetadataDialog(
    private val activity: SimpleActivity,
    private val state: PostCallMetadataHelper.FormState,
    private val onSave: (PostCallMetadataHelper.FormState) -> Unit,
    private val onSkip: () -> Unit
) {
    internal var alertDialog: AlertDialog? = null

    init {
        val binding = DialogPostCallMetadataBinding.inflate(activity.layoutInflater).apply {
            postCallSurveySwitch.isChecked = state.surveyCall
            postCallUniqueId.setText(state.uniqueId ?: "")
            postCallEnumerator.setText(state.enumeratorId ?: "")
            postCallNote.setText(state.note ?: "")
        }

        val builder = activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.survey_skip, null)

        activity.setupDialogStuff(binding.root, builder, R.string.post_call_form_title) { dialog ->
            alertDialog = dialog
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val uniqueId = binding.postCallUniqueId.value.trim()
                if (uniqueId.isBlank()) {
                    binding.postCallUniqueIdHint.error = activity.getString(R.string.post_call_unique_id_required)
                    return@setOnClickListener
                }

                val updated = state.copy(
                    surveyCall = binding.postCallSurveySwitch.isChecked,
                    uniqueId = uniqueId,
                    enumeratorId = binding.postCallEnumerator.value.trim().ifEmpty { null },
                    note = binding.postCallNote.value.trim().ifEmpty { null }
                )
                onSave(updated)
                dialog.dismiss()
            }

            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                onSkip()
                dialog.dismiss()
            }
        }
    }
}
