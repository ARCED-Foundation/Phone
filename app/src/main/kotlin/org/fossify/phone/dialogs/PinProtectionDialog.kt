package org.fossify.phone.dialogs

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import org.fossify.phone.R
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.getAlertDialogBuilder
import org.fossify.phone.extensions.setupDialogButton
import org.fossify.phone.helpers.PinProtectionPolicy

class PinProtectionDialog(
    private val activity: Activity,
    private val callback: (Boolean) -> Unit
) {
    private val policy = PinProtectionPolicy(activity.config)

    fun show() {
        if (policy.isLockedOut()) {
            val remainingSeconds = policy.remainingLockSeconds().coerceAtLeast(1)
            Toast.makeText(
                activity,
                activity.getString(R.string.pin_locked_out, remainingSeconds),
                Toast.LENGTH_LONG
            ).show()
            callback(false)
            return
        }

        val view = EditText(activity).apply {
            inputType = (InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)
            hint = activity.getString(R.string.enter_pin)
            textSize = 18f
        }

        val dialog = activity.getAlertDialogBuilder()
            .setTitle(activity.getString(R.string.pin_protection))
            .setView(view)
            .setPositiveButton(R.string.validate) { dialog, _ ->
                val enteredPin = view.text.toString()
                val result = policy.validatePin(enteredPin)
                callback(result.isValid)
                if (result.lockoutTriggered) {
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.pin_too_many_attempts, policy.lockoutMinutes.toInt()),
                        Toast.LENGTH_LONG
                    ).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        // Keyboard management
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // Request focus on the EditText
        view.requestFocus()

        dialog.show()
        dialog.apply {
            getButton(AlertDialog.BUTTON_POSITIVE)?.setupDialogButton(activity)
            getButton(AlertDialog.BUTTON_NEGATIVE)?.setupDialogButton(activity)
        }
    }
}
