package org.fossify.phone.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.phone.R
import org.fossify.phone.adapters.SyncLogAdapter
import org.fossify.phone.database.AppDatabase
import org.fossify.phone.databinding.ActivitySyncLogsBinding
import org.fossify.phone.extensions.config
import org.fossify.phone.helpers.CallSyncPayloadBuilder
import org.fossify.phone.helpers.IntentExtrasHelper
import org.fossify.phone.helpers.SyncLogHelper
import org.fossify.phone.models.PendingSync
import org.fossify.phone.models.SyncLogEntry
import org.fossify.phone.models.SyncLogStatus
import org.fossify.phone.work.WorkManagerHelper
import java.io.File

class SyncLogActivity : SimpleActivity() {

    private val binding by viewBinding(ActivitySyncLogsBinding::inflate)
    private val adapter by lazy { SyncLogAdapter(::retrySync) }
    private val syncLogHelper by lazy { SyncLogHelper(this) }
    private val db by lazy { AppDatabase.getInstance(this) }
    private val callLogDao by lazy { db.callLogDao() }
    private val pendingSyncDao by lazy { db.pendingSyncDao() }
    private var logsJob: Job? = null
    private var latestLogs: List<SyncLogEntry> = emptyList()
    private var pendingCsvContent: String? = null

    private val createCsvDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            val csvContent = pendingCsvContent
            pendingCsvContent = null
            if (uri == null || csvContent == null) {
                return@registerForActivityResult
            }
            lifecycleScope.launch {
                val success = saveCsvToUri(uri, csvContent)
                toast(if (success) R.string.export_csv_saved else R.string.export_csv_failed)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setSupportActionBar(binding.syncLogToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setupList()
        updateRetentionSummary()
        binding.syncLogToolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    override fun onStart() {
        super.onStart()
        observeLogs()
    }

    override fun onStop() {
        super.onStop()
        logsJob?.cancel()
        logsJob = null
    }

    private fun setupList() {
        binding.syncLogList.layoutManager = LinearLayoutManager(this)
        binding.syncLogList.adapter = adapter
        binding.syncLogList.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )
    }

    private fun observeLogs() {
        logsJob?.cancel()
        logsJob = lifecycleScope.launch {
            syncLogHelper.observeRecentLogs().collectLatest { logs ->
                latestLogs = logs
                adapter.submitList(logs)
                binding.syncLogEmpty.isVisible = logs.isEmpty()
            }
        }
    }

    private fun updateRetentionSummary() {
        val days = config.syncLogRetentionDays
        binding.syncLogSubtitle.text = getString(R.string.sync_logs_retention, days)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_export_csv -> {
                exportCsv()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_sync_logs, menu)
        return true
    }

    private fun exportCsv() {
        if (latestLogs.isEmpty()) {
            toast(R.string.sync_logs_empty)
            return
        }
        val csvContent = buildCsv(latestLogs)
        pendingCsvContent = csvContent
        AlertDialog.Builder(this)
            .setTitle(R.string.export_csv)
            .setItems(
                arrayOf(
                    getString(R.string.export_csv_save_device),
                    getString(R.string.export_csv_share)
                )
            ) { _, which ->
                when (which) {
                    0 -> createCsvDocument.launch("sync_logs_${System.currentTimeMillis()}.csv")
                    1 -> shareCsv(csvContent)
                }
            }
            .show()
    }

    private fun shareCsv(csvContent: String) {
        val csvFile = File(cacheDir, "sync_logs.csv")
        csvFile.writeText(csvContent)
        val uri: Uri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            csvFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.export_csv)))
    }

    private suspend fun saveCsvToUri(uri: Uri, csvContent: String): Boolean {
        return withContext(Dispatchers.IO) {
            runCatching {
                contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(csvContent.toByteArray())
                    stream.flush()
                } ?: throw IllegalStateException("No output stream")
            }.isSuccess
        }
    }

    private fun retrySync(entry: SyncLogEntry) {
        val callLogId = entry.callLogId ?: run {
            toast(R.string.sync_log_retry_missing_id)
            return
        }
        lifecycleScope.launch {
            val callLog = withContext(Dispatchers.IO) { callLogDao.getById(callLogId) }
            if (callLog == null) {
                toast(R.string.sync_log_retry_missing_id)
                return@launch
            }
            if (callLog.synced) {
                withContext(Dispatchers.IO) {
                    pendingSyncDao.deleteForCallLog(callLogId)
                    syncLogHelper.logStatus(callLog, null, SyncLogStatus.SUCCESS, "Already synced")
                }
                toast(R.string.sync_log_retry_enqueued)
                return@launch
            }

            // Check if this is a non-ODK call with incomplete metadata
            if (!callLog.isOdkCall && !callLog.formCompleted && !callLog.synced) {
                AlertDialog.Builder(this@SyncLogActivity)
                    .setTitle("Complete Metadata")
                    .setMessage("This call requires metadata completion before syncing. Complete the form now?")
                    .setPositiveButton("Complete Metadata") { _, _ ->
                        // Launch PostCallMetadataActivity for this call
                        PostCallMetadataActivity.launch(this@SyncLogActivity, callLogId)
                    }
                    .setNegativeButton("Skip") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
                return@launch
            }
            val config = resolvePersistedConfig()
            if (config == null) {
                toast(R.string.sync_log_retry_missing_config)
                return@launch
            }
            val extras = IntentExtrasHelper.parseExtrasJson(callLog.extrasJson)
            val payload = CallSyncPayloadBuilder.build(callLog, extras, config)
            if (payload == null) {
                toast(R.string.sync_log_retry_missing_payload)
                return@launch
            }
            withContext(Dispatchers.IO) {
                pendingSyncDao.deleteForCallLog(callLogId)
                val pending = PendingSync(
                    callLogId = callLogId,
                    syncPayloadJson = JSONObject(payload.toPersistenceMap()).toString()
                )
                pendingSyncDao.insert(pending)
            }
            WorkManagerHelper.enqueueOdkSyncWork(this@SyncLogActivity, initialDelayMs = 0L, forceNow = true)
            toast(R.string.sync_log_retry_enqueued)
        }
    }

    private fun resolvePersistedConfig(): IntentExtrasHelper.OdkConfig? {
        val cfg = config
        val base = cfg.lastOdkBaseUrl
        val project = cfg.lastOdkProjectId
        val dataset = cfg.lastOdkDataset
        return if (!base.isNullOrBlank() && !project.isNullOrBlank() && !dataset.isNullOrBlank()) {
            IntentExtrasHelper.OdkConfig(base, project, dataset)
        } else {
            null
        }
    }

    private fun buildCsv(logs: List<SyncLogEntry>): String {
        val headers = listOf(
            "created_at",
            "status",
            "message",
            "call_log_id",
            "phone_number",
            "direction",
            "dataset",
            "project_id",
            "base_url",
            "call_start_utc",
            "call_end_utc",
            "duration_seconds",
            "outcome",
            "outcome_detail",
            "instance_id",
            "attempt_number",
            "enumerator_id",
            "survey_id",
            "additional_notes",
            "extras_json",
            "synced_flag",
            "sync_attempts",
            "last_sync_error"
        )
        val rows = logs.map { entry ->
            listOf(
                entry.createdAt.toString(),
                entry.status,
                entry.message.orEmpty(),
                entry.callLogId?.toString().orEmpty(),
                entry.phoneNumber.orEmpty(),
                entry.direction.orEmpty(),
                entry.datasetName.orEmpty(),
                entry.projectId.orEmpty(),
                entry.baseUrl.orEmpty(),
                entry.callStartUtc?.toString().orEmpty(),
                entry.callEndUtc?.toString().orEmpty(),
                entry.durationSeconds?.toString().orEmpty(),
                entry.outcome.orEmpty(),
                entry.outcomeDetail.orEmpty(),
                entry.instanceId.orEmpty(),
                entry.attemptNumber?.toString().orEmpty(),
                entry.enumeratorId.orEmpty(),
                entry.surveyId.orEmpty(),
                entry.additionalNotes.orEmpty(),
                entry.extrasJson.orEmpty(),
                entry.syncedFlag?.toString().orEmpty(),
                entry.syncAttempts?.toString().orEmpty(),
                entry.lastSyncError.orEmpty()
            ).joinToString(",") { escapeCsv(it) }
        }
        return buildString {
            append(headers.joinToString(","))
            append("\n")
            rows.forEachIndexed { index, row ->
                append(row)
                if (index != rows.lastIndex) append("\n")
            }
        }
    }

    private fun escapeCsv(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            val escaped = value.replace("\"", "\"\"")
            return "\"$escaped\""
        }
        return value
    }
}
