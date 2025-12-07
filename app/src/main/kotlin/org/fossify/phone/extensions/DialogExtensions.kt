package org.fossify.phone.extensions

import android.app.Activity
import android.widget.Button
import androidx.appcompat.app.AlertDialog

fun Activity.getAlertDialogBuilder(): AlertDialog.Builder = AlertDialog.Builder(this)

fun Button.setupDialogButton(activity: Activity) {
    // Keep dialog buttons consistent with the app theme; no-op placeholder for now
    isAllCaps = false
}
