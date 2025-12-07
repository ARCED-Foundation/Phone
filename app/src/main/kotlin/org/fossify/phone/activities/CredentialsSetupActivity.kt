package org.fossify.phone.activities

import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.core.widget.doOnTextChanged
import org.fossify.commons.extensions.viewBinding
import org.fossify.phone.R
import org.fossify.phone.databinding.ActivityCredentialsSetupBinding
import org.fossify.phone.extensions.config
import org.fossify.phone.helpers.AdminSettingsHelper
import org.fossify.commons.dialogs.ConfirmationDialog

class CredentialsSetupActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityCredentialsSetupBinding::inflate)
    private lateinit var adminSettingsHelper: AdminSettingsHelper
    private var credentialsTestVerified = false
    private var lastTestedUrl: String? = null
    private var lastTestedUsername: String? = null
    private var lastTestedPassword: String? = null
    private var initialCentralUrl: String = ""
    private var initialUsername: String = ""
    private var initialRememberBaseUrl: Boolean = false
    private var hasLoadedInitialState = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setSupportActionBar(binding.credentialsToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.credentialsToolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        adminSettingsHelper = AdminSettingsHelper(this)

        setupClickListeners()
        loadCurrentCredentials()
    }

    private fun setupClickListeners() {
        binding.btnSaveCredentials.setOnClickListener {
            saveCredentials()
        }

        binding.btnTestConnection.setOnClickListener {
            testConnection()
        }
    }

    private fun loadCurrentCredentials() {
        lifecycleScope.launch(Dispatchers.IO) {
            val credentials = adminSettingsHelper.getCredentials()
            runOnUiThread {
                val startingUrl = credentials?.centralUrl ?: config.centralBaseUrlOverride ?: ""
                val startingUsername = credentials?.username ?: ""
                binding.editTextCentralUrl.setText(startingUrl)
                binding.editTextUsername.setText(startingUsername)
                binding.editTextPassword.setText("")
                binding.switchRememberBaseUrl.isChecked = config.centralBaseUrlOverride != null
                initialCentralUrl = startingUrl
                initialUsername = startingUsername
                initialRememberBaseUrl = binding.switchRememberBaseUrl.isChecked
                hasLoadedInitialState = true
            }
        }

        binding.editTextCentralUrl.doOnTextChanged { _, _, _, _ -> invalidateTestState() }
        binding.editTextUsername.doOnTextChanged { _, _, _, _ -> invalidateTestState() }
        binding.editTextPassword.doOnTextChanged { _, _, _, _ -> invalidateTestState() }
    }

    private fun saveCredentials() {
        val username = binding.editTextUsername.text.toString().trim()
        val password = binding.editTextPassword.text.toString().trim()
        val baseUrlInput = binding.editTextCentralUrl.text.toString().trim()
        val centralUrl = baseUrlInput.takeIf { it.isNotBlank() } ?: config.centralBaseUrlOverride

        if (centralUrl.isNullOrBlank()) {
            Toast.makeText(this, R.string.central_url_required, Toast.LENGTH_SHORT).show()
            return
        }

        if (username.isBlank() || password.isBlank()) {
            Toast.makeText(this, R.string.all_fields_required, Toast.LENGTH_SHORT).show()
            return
        }

        if (!isTestStillValid(centralUrl, username, password)) {
            Toast.makeText(this, R.string.test_required_before_save, Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val success = adminSettingsHelper.saveCredentials(centralUrl, username, password)
            runOnUiThread {
                if (success) {
                    Toast.makeText(this@CredentialsSetupActivity, R.string.credentials_saved_successfully, Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@CredentialsSetupActivity, R.string.credentials_save_failed, Toast.LENGTH_SHORT).show()
                }
            }
            if (success && binding.switchRememberBaseUrl.isChecked && baseUrlInput.isNotBlank()) {
                config.centralBaseUrlOverride = baseUrlInput
            }
        }
    }

    private fun testConnection() {
        val username = binding.editTextUsername.text.toString().trim()
        val password = binding.editTextPassword.text.toString().trim()
        val baseUrlInput = binding.editTextCentralUrl.text.toString().trim()
        val centralUrl = baseUrlInput.takeIf { it.isNotBlank() } ?: config.centralBaseUrlOverride

        if (centralUrl.isNullOrBlank()) {
            Toast.makeText(this, R.string.central_url_required, Toast.LENGTH_SHORT).show()
            return
        }

        if (username.isBlank() || password.isBlank()) {
            Toast.makeText(this, R.string.all_fields_required, Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val success = adminSettingsHelper.validateCredentialsBeforeStorage(centralUrl, username, password)
            runOnUiThread {
                if (success) {
                    Snackbar.make(binding.root, R.string.connection_successful, Snackbar.LENGTH_SHORT).show()
                    credentialsTestVerified = true
                    lastTestedUrl = centralUrl
                    lastTestedUsername = username
                    lastTestedPassword = password
                } else {
                    Snackbar.make(binding.root, R.string.connection_failed, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun invalidateTestState() {
        credentialsTestVerified = false
    }

    private fun isTestStillValid(url: String, username: String, password: String): Boolean {
        return credentialsTestVerified &&
            url == lastTestedUrl &&
            username == lastTestedUsername &&
            password == lastTestedPassword
    }

    private fun hasUnsavedChanges(): Boolean {
        if (!hasLoadedInitialState) return false
        val currentUrl = binding.editTextCentralUrl.text?.toString()?.trim().orEmpty()
        val currentUsername = binding.editTextUsername.text?.toString()?.trim().orEmpty()
        val rememberBaseUrl = binding.switchRememberBaseUrl.isChecked
        val passwordChanged = binding.editTextPassword.text?.isNotBlank() == true

        return currentUrl != initialCentralUrl ||
            currentUsername != initialUsername ||
            rememberBaseUrl != initialRememberBaseUrl ||
            passwordChanged
    }

    private fun promptUnsavedChanges(): Boolean {
        if (!hasUnsavedChanges()) return false
        ConfirmationDialog(this, getString(R.string.unsaved_changes_message)) {
            finish()
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                if (!promptUnsavedChanges()) {
                    finish()
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        if (promptUnsavedChanges()) {
            return
        }
        super.onBackPressed()
    }
}
