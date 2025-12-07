package org.fossify.phone.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.fossify.phone.R
import org.fossify.phone.databinding.ItemSyncLogBinding
import org.fossify.phone.models.SyncLogEntry
import org.fossify.phone.models.SyncLogStatus
import org.fossify.phone.utils.TimestampUtils

class SyncLogAdapter(
    private val onRetry: (SyncLogEntry) -> Unit
) : ListAdapter<SyncLogEntry, SyncLogAdapter.SyncLogViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SyncLogViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemSyncLogBinding.inflate(inflater, parent, false)
        return SyncLogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SyncLogViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SyncLogViewHolder(private val binding: ItemSyncLogBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: SyncLogEntry) {
            val context = binding.root.context
            binding.syncLogTime.text = TimestampUtils.formatDisplayTimestamp(entry.createdAt)

            val status = runCatching { SyncLogStatus.valueOf(entry.status) }.getOrDefault(SyncLogStatus.PENDING)
            val (statusLabel, colorRes) = when (status) {
                SyncLogStatus.SUCCESS -> R.string.sync_log_status_success to R.color.color_outgoing_call
                SyncLogStatus.PENDING -> R.string.sync_log_status_pending to R.color.color_incoming_call
                SyncLogStatus.FAILED -> R.string.sync_log_status_failed to R.color.color_missed_call
            }
            val statusColor = ContextCompat.getColor(context, colorRes)
            binding.syncLogStatus.text = context.getString(statusLabel)
            binding.syncLogStatus.backgroundTintList = ColorStateList.valueOf(statusColor)
            binding.syncLogStatus.setTextColor(ContextCompat.getColor(context, android.R.color.white))
            binding.syncLogRetry.isVisible = status != SyncLogStatus.SUCCESS
            binding.syncLogRetry.setOnClickListener { onRetry(entry) }

            val phoneLabel = entry.phoneNumber?.takeIf { it.isNotBlank() } ?: context.getString(R.string.unknown_caller)
            val direction = entry.direction?.takeIf { it.isNotBlank() } ?: "-"
            binding.syncLogPrimary.text = context.getString(R.string.sync_log_row_primary, phoneLabel, direction)

            val dataset = entry.datasetName?.takeIf { it.isNotBlank() } ?: "-"
            val project = entry.projectId?.takeIf { it.isNotBlank() } ?: "-"
            binding.syncLogSecondary.text = context.getString(R.string.sync_log_row_secondary, dataset, project)

            binding.syncLogMessage.isVisible = !entry.message.isNullOrBlank()
            binding.syncLogMessage.text = entry.message

            val details = buildString {
                append(context.getString(R.string.sync_log_row_secondary, entry.datasetName.orEmpty(), entry.projectId.orEmpty()))
                if (!entry.outcome.isNullOrBlank()) {
                    append("\n")
                    append(context.getString(R.string.sync_log_outcome_row, entry.outcome.orEmpty(), entry.durationSeconds?.toString().orEmpty()))
                }
                if (entry.callStartUtc != null || entry.callEndUtc != null) {
                    append("\n")
                    append(context.getString(R.string.sync_log_time_row, entry.callStartUtc?.toString().orEmpty(), entry.callEndUtc?.toString().orEmpty()))
                }
            }
            binding.syncLogSecondary.text = details.trim()
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<SyncLogEntry>() {
        override fun areItemsTheSame(oldItem: SyncLogEntry, newItem: SyncLogEntry): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: SyncLogEntry, newItem: SyncLogEntry): Boolean = oldItem == newItem
    }
}
