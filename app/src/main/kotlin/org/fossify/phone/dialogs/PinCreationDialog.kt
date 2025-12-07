package org.fossify.phone.dialogs

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import org.fossify.phone.R
import org.fossify.phone.extensions.getAlertDialogBuilder
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.setupDialogButton
import org.fossify.phone.utils.SecurityUtils

class PinCreationDialog(
    private val activity: Activity,
    private val callback: (Boolean, String?) -> Unit
) {
    fun show() {
        try {
            android.util.Log.d("PinCreationDialog", "Starting pin creation dialog")
            showFirstPinDialog()
        } catch (e: Exception) {
            android.util.Log.e("PinCreationDialog", "Crash during pin creation dialog show", e)
            callback(false, null)
        }
    }

    private fun showFirstPinDialog() {
        val pinEditText = EditText(activity).apply {
            inputType = (InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)
            hint = activity.getString(R.string.create_pin)
            textSize = 18f
        }

        val dialog = activity.getAlertDialogBuilder()
            .setTitle(activity.getString(R.string.create_admin_pin))
            .setView(pinEditText)
            .setPositiveButton(R.string.next) { dialog, _ ->
                dialog.dismiss()
                showConfirmDialog(pinEditText.text.toString())
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                callback(false, null)
                dialog.dismiss()
            }
            .create()

        // Keyboard management
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // Request focus on the EditText
        pinEditText.requestFocus()

        dialog.show()
        dialog.apply {
            getButton(AlertDialog.BUTTON_POSITIVE)?.setupDialogButton(activity)
            getButton(AlertDialog.BUTTON_NEGATIVE)?.setupDialogButton(activity)
        }
    }

    private fun showConfirmDialog(initialPin: String) {
        try {
            android.util.Log.d("PinCreationDialog", "Starting pin confirmation dialog with length: ${initialPin.length}")
            showConfirmDialogInternal(initialPin)
        } catch (e: Exception) {
            android.util.Log.e("PinCreationDialog", "Crash during pin confirmation dialog", e)
            callback(false, null)
        }
    }

    private fun showConfirmDialogInternal(initialPin: String) {
        val confirmPinEditText = EditText(activity).apply {
            inputType = (InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)
            hint = activity.getString(R.string.confirm_pin)
            textSize = 18f
        }

        val dialog = activity.getAlertDialogBuilder()
            .setTitle(activity.getString(R.string.confirm_pin))
            .setView(confirmPinEditText)
            .setPositiveButton(R.string.create_pin) { dialog, _ ->
                try {
                    val confirmPin = confirmPinEditText.text.toString()
                    android.util.Log.d("PinCreationDialog", "Pin validation - initial length: ${initialPin.length}, confirm length: ${confirmPin.length}")

                    if (initialPin == confirmPin && initialPin.length >= 4) {
                        val pinHash = SecurityUtils.hashSecret(initialPin)
                        android.util.Log.d("PinCreationDialog", "Pin created successfully, hash length: ${pinHash.length}")
                        activity.config.adminPinHash = pinHash
                        activity.config.adminPinFailedAttempts = 0
                        activity.config.adminPinLockoutUntil = 0L
                        callback(true, initialPin)
                    } else if (initialPin != confirmPin) {
                        android.util.Log.w("PinCreationDialog", "Pin mismatch")
                        callback(false, null)
                    } else {
                        android.util.Log.w("PinCreationDialog", "Pin too short: ${initialPin.length}")
                        callback(false, null)
                    }
                    dialog.dismiss()
                } catch (e: Exception) {
                    android.util.Log.e("PinCreationDialog", "Crash during pin creation processing", e)
                    callback(false, null)
                    dialog.dismiss()
                }
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                callback(false, null)
                dialog.dismiss()
            }
            .create()

        // Keyboard management
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // Request focus on the EditText
        confirmPinEditText.requestFocus()

        dialog.show()
        dialog.apply {
            getButton(AlertDialog.BUTTON_POSITIVE)?.setupDialogButton(activity)
            getButton(AlertDialog.BUTTON_NEGATIVE)?.setupDialogButton(activity)
        }
    }
}
