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
    private val onSave: (PostCallMetadataHelper.FormState) -> Unit
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

        activity.setupDialogStuff(binding.root, builder, R.string.post_call_form_title) { dialog ->
            alertDialog = dialog
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val uniqueId = binding.postCallUniqueId.value.trim()
                val enumeratorId = binding.postCallEnumerator.value.trim()
                val note = binding.postCallNote.value.trim()

                var hasError = false
                if (uniqueId.isBlank()) {
                    binding.postCallUniqueIdHint.error = activity.getString(R.string.post_call_unique_id_required)
                    hasError = true
                } else {
                    binding.postCallUniqueIdHint.error = null
                }

                if (enumeratorId.isBlank()) {
                    binding.postCallEnumeratorHint.error = activity.getString(R.string.post_call_enumerator_required)
                    hasError = true
                } else {
                    binding.postCallEnumeratorHint.error = null
                }

                if (note.isBlank()) {
                    binding.postCallNoteHint.error = activity.getString(R.string.post_call_note_required)
                    hasError = true
                } else {
                    binding.postCallNoteHint.error = null
                }

                if (hasError) {
                    return@setOnClickListener
                }

                val updated = state.copy(
                    surveyCall = binding.postCallSurveySwitch.isChecked,
                    uniqueId = uniqueId,
                    enumeratorId = enumeratorId,
                    note = note
                )
                onSave(updated)
                dialog.dismiss()
            }
        }
    }
}
