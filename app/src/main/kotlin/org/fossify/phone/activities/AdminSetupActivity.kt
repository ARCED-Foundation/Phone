package org.fossify.phone.activities

import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.MenuItem
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.fossify.phone.R
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.phone.databinding.ActivityAdminSetupBinding
import org.fossify.commons.extensions.viewBinding
import org.fossify.phone.extensions.config
import org.fossify.phone.dialogs.PinCreationDialog
import org.fossify.phone.dialogs.PinProtectionDialog
import org.fossify.phone.dialogs.ReservedKeysConfigDialog
import org.fossify.phone.helpers.AdminSettingsHelper
import android.widget.Toast

class AdminSetupActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityAdminSetupBinding::inflate)
    private lateinit var adminSettingsHelper: AdminSettingsHelper
    private var pinValidated = false
    private var pinReminderShown = false
    private var pinCreationDialogVisible = false
    private var initialRetentionDays: Int = 30
    private var retentionDirty = false
    private var hasStoredCredentials = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setSupportActionBar(binding.adminSetupToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.adminSetupToolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        adminSettingsHelper = AdminSettingsHelper(this)

        setupFooterHtml()
        setupClickListeners()
        checkIfPinExists()
        loadLogRetention()
    }

    private fun setupFooterHtml() {
        val footerText = binding.root.findViewById<android.widget.TextView>(R.id.sotlab_footer)
        val htmlText = Html.fromHtml(getString(R.string.sotlab_footer), Html.FROM_HTML_MODE_LEGACY)
        footerText.text = htmlText
        footerText.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun setupClickListeners() {
        binding.btnAddPin.setOnClickListener {
            try {
                android.util.Log.d("AdminSetupActivity", "Add pin button clicked")
                showPinCreationDialog()
            } catch (e: Exception) {
                android.util.Log.e("AdminSetupActivity", "Crash in add pin button click handler", e)
            }
        }

        binding.btnRemovePin.setOnClickListener {
            showPinRemovalConfirmation()
        }

        binding.btnConfigureCredentials.setOnClickListener {
            ensurePinAccess {
                startActivity(Intent(this, CredentialsSetupActivity::class.java))
            }
        }

        binding.btnConfigureReservedKeys.setOnClickListener {
            ensurePinAccess {
                showReservedKeysDialog()
            }
        }

        binding.btnSaveLogRetention.setOnClickListener {
            ensurePinAccess {
                saveLogRetention()
            }
        }

    }

    private fun checkIfPinExists() {
        val pinHash = config.adminPinHash
        binding.btnAddPin.isEnabled = pinHash == null
        binding.btnRemovePin.isEnabled = pinHash != null
    }

    private fun loadLogRetention() {
        initialRetentionDays = adminSettingsHelper.getLogRetentionDays()
        binding.editLogRetentionDays.setText(initialRetentionDays.toString())
        retentionDirty = false
        binding.editLogRetentionDays.doOnTextChanged { text, _, _, _ ->
            val current = text?.toString()?.trim().orEmpty()
            retentionDirty = current != initialRetentionDays.toString()
        }
    }

    private fun saveLogRetention() {
        val value = binding.editLogRetentionDays.text?.toString()?.trim()?.toIntOrNull()
        if (value == null || value !in 1..365) {
            Toast.makeText(this, R.string.invalid_retention_days, Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val success = adminSettingsHelper.updateLogRetentionDays(value)
            runOnUiThread {
                if (success) {
                    Snackbar.make(binding.root, R.string.log_retention_saved, Snackbar.LENGTH_SHORT).show()
                    initialRetentionDays = value
                    retentionDirty = false
                } else {
                    Toast.makeText(this@AdminSetupActivity, R.string.invalid_retention_days, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showPinCreationDialog(onSuccess: (() -> Unit)? = null) {
        if (pinCreationDialogVisible) {
            return
        }
        pinCreationDialogVisible = true
        PinCreationDialog(this) { success, _ ->
            pinCreationDialogVisible = false
            if (success) {
                Snackbar.make(binding.root, R.string.pin_created_successfully, Snackbar.LENGTH_SHORT).show()
                checkIfPinExists()
                pinReminderShown = false
                pinValidated = true
                onSuccess?.invoke()
            }
        }.show()
    }

    private fun showPinRemovalConfirmation() {
        PinProtectionDialog(this) { isValid ->
            if (isValid) {
                config.adminPinHash = null
                config.adminPinFailedAttempts = 0
                config.adminPinLockoutUntil = 0L
                pinValidated = false
                lifecycleScope.launch(Dispatchers.IO) {
                    val hasCredentials = adminSettingsHelper.hasCredentials()
                    runOnUiThread {
                        hasStoredCredentials = hasCredentials
                        Snackbar.make(binding.root, R.string.pin_removed_successfully, Snackbar.LENGTH_SHORT).show()
                        checkIfPinExists()
                        if (hasCredentials && !pinCreationDialogVisible) {
                            promptPinCreationForMissingProtection()
                        }
                    }
                }
            }
        }.show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                if (!handleExitAttempt()) {
                    finish()
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        handlePinProtectionOnResume()
        checkIfPinExists()
        promptPinReminderIfNeeded()
    }

    override fun onBackPressed() {
        if (handleExitAttempt()) {
            return
        }
        super.onBackPressed()
    }

    private fun handlePinProtectionOnResume() {
        if (config.adminPinHash != null) {
            ensurePinAccess {}
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val hasCredentials = adminSettingsHelper.hasCredentials()
            hasStoredCredentials = hasCredentials
            if (!hasCredentials) {
                return@launch
            }
            runOnUiThread {
                if (config.adminPinHash == null && !pinCreationDialogVisible) {
                    showPinCreationDialog()
                }
            }
        }
    }

    private fun ensurePinAccess(action: () -> Unit) {
        if (config.adminPinHash == null) {
            lifecycleScope.launch(Dispatchers.IO) {
                val hasAnyCredentials = adminSettingsHelper.hasCredentials()
                hasStoredCredentials = hasAnyCredentials
                runOnUiThread {
                    if (!hasAnyCredentials) {
                        action()
                    } else {
                        showPinCreationDialog(action)
                    }
                }
            }
            return
        }
        if (pinValidated) {
            action()
            return
        }

        PinProtectionDialog(this) { isValid ->
            pinValidated = isValid
            if (isValid) {
                action()
            }
        }.show()
    }

    private fun promptPinReminderIfNeeded() {
        if (config.adminPinHash != null || pinReminderShown) {
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val hasCredentials = adminSettingsHelper.hasCredentials()
            hasStoredCredentials = hasCredentials
            if (!hasCredentials) {
                return@launch
            }
            runOnUiThread {
                if (config.adminPinHash == null && !pinReminderShown) {
                    pinReminderShown = true
                    Snackbar.make(
                        binding.root,
                        R.string.pin_required_after_credentials,
                        Snackbar.LENGTH_LONG
                    ).setAction(R.string.create_pin) {
                        showPinCreationDialog()
                    }.show()
                }
            }
        }
    }

    private fun showReservedKeysDialog() {
        ReservedKeysConfigDialog(this, adminSettingsHelper)
    }

    private fun handleExitAttempt(): Boolean {
        if (shouldBlockExitForMissingPin()) {
            promptPinCreationForMissingProtection()
            return true
        }
        if (handleUnsavedChanges()) return true
        return false
    }

    private fun handleUnsavedChanges(): Boolean {
        if (!retentionDirty) return false
        ConfirmationDialog(this, getString(R.string.unsaved_changes_message)) {
            if (shouldBlockExitForMissingPin()) {
                promptPinCreationForMissingProtection()
                return@ConfirmationDialog
            }
            retentionDirty = false
            finish()
        }
        return true
    }

    private fun shouldBlockExitForMissingPin(): Boolean {
        return hasStoredCredentials && config.adminPinHash == null
    }

    private fun promptPinCreationForMissingProtection() {
        Snackbar.make(
            binding.root,
            R.string.pin_required_after_credentials,
            Snackbar.LENGTH_LONG
        ).setAction(R.string.create_pin) {
            showPinCreationDialog()
        }.show()
    }
}
