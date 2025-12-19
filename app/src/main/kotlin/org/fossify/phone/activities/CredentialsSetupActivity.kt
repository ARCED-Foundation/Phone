package org.fossify.phone.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
        setupEdgeToEdge()
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

    private fun setupEdgeToEdge() {
        window.decorView.apply {
            systemUiVisibility = systemUiVisibility or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
    }

    private fun setupKeyboardVisibilityListener() {
        // Removed automatic password field focus that was interfering with user input
        // The edge-to-edge setup and windowSoftInputMode in manifest handle keyboard visibility
    }

    private fun setupClickListeners() {
        binding.btnSaveCredentials.setOnClickListener {
            saveCredentials()
        }

        binding.btnTestConnection.setOnClickListener {
            testConnection()
        }

        binding.switchRememberBaseUrl.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                // Clear any persisted override so ODK intent-provided URLs are used.
                config.centralBaseUrlOverride = null
            }
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
            } else if (!binding.switchRememberBaseUrl.isChecked) {
                config.centralBaseUrlOverride = null
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

        // Show loading indicator
        binding.btnTestConnection.isEnabled = false
        binding.btnTestConnection.text = getString(R.string.testing_connection)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Get diagnostic info before testing
                val diagnosticInfo = adminSettingsHelper.getDiagnosticInfo()

                // Test the connection
                val success = adminSettingsHelper.validateCredentialsBeforeStorage(centralUrl, username, password)

                runOnUiThread {
                    binding.btnTestConnection.isEnabled = true
                    binding.btnTestConnection.text = getString(R.string.test_connection)

                    if (success) {
                        Snackbar.make(binding.root, R.string.connection_successful, Snackbar.LENGTH_SHORT).show()
                        credentialsTestVerified = true
                        lastTestedUrl = centralUrl
                        lastTestedUsername = username
                        lastTestedPassword = password
                    } else {
                        // Enhanced error reporting with detailed message
                        val errorMessage = buildDetailedErrorMessage(centralUrl, diagnosticInfo)
                        showDetailedConnectionError(errorMessage)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.btnTestConnection.isEnabled = true
                    binding.btnTestConnection.text = getString(R.string.test_connection)

                    val errorMessage = "Connection test failed: ${e.message}\n\nPlease check:\n• Internet connection\n• Server URL is correct\n• ODK Central server is running"
                    showDetailedConnectionError(errorMessage)
                }
            }
        }
    }

    private fun buildDetailedErrorMessage(baseUrl: String, diagnosticInfo: Map<String, String>): String {
        return """
            Connection Failed

            Server: $baseUrl
            Version: ${diagnosticInfo["client_version"]}
            Time: ${java.text.SimpleDateFormat.getDateTimeInstance().format(java.util.Date())}

            Troubleshooting:
            1. Check if the URL is correct and accessible
            2. Verify username and password are correct
            3. Ensure ODK Central server is running
            4. Check if the server supports the API endpoints:
               • /v1/users/current
               • /v1/projects
            5. Verify network connectivity
            6. Check server logs for more details

            Supported endpoints tested with 15-second timeout.
        """.trimIndent()
    }

    private fun showDetailedConnectionError(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Connection Failed")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .setNegativeButton("Copy Details") { dialog, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Connection Error", message)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Details copied to clipboard", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
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