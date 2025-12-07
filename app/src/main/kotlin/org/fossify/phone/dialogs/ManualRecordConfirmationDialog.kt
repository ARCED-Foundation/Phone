package org.fossify.phone.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import org.fossify.phone.R

class ManualRecordConfirmationDialog : DialogFragment() {
    private var onConfirmListener: (() -> Unit)? = null

    fun setOnConfirmListener(listener: () -> Unit): ManualRecordConfirmationDialog {
        onConfirmListener = listener
        return this
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.manual_record_dialog_title)
            .setMessage(R.string.manual_record_dialog_message)
            .setNegativeButton(R.string.manual_record_dialog_cancel, null)
            .setPositiveButton(R.string.manual_record_dialog_confirm) { _, _ ->
                onConfirmListener?.invoke()
            }
            .create()
    }

    override fun onDestroyView() {
        onConfirmListener = null
        super.onDestroyView()
    }

    companion object {
        private const val TAG = "ManualRecordConfirmationDialog"

        fun show(
            fragmentManager: FragmentManager,
            block: ManualRecordConfirmationDialog.() -> Unit = {}
        ) {
            val existing = fragmentManager.findFragmentByTag(TAG) as? ManualRecordConfirmationDialog
            if (existing != null && existing.isAdded) {
                return
            }
            ManualRecordConfirmationDialog().apply(block).show(fragmentManager, TAG)
        }
    }
}
