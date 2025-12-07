package org.fossify.phone.dialogs

import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.google.android.material.chip.Chip
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.toast
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.databinding.DialogReservedKeysConfigBinding
import org.fossify.phone.helpers.AdminSettingsHelper

class ReservedKeysConfigDialog(
    private val activity: SimpleActivity,
    private val adminSettingsHelper: AdminSettingsHelper,
    private val onSaved: (Set<String>) -> Unit = {}
) {

    private val savedCustomKeys = adminSettingsHelper.getReservedKeys().toMutableSet()
    private val defaultKeys = AdminSettingsHelper.DEFAULT_RESERVED_KEYS

    init {
        val binding = DialogReservedKeysConfigBinding.inflate(LayoutInflater.from(activity))

        populateDefaultKeyChips(binding)
        refreshCustomKeyChips(binding)

        binding.btnAddReservedKey.setOnClickListener {
            addCustomKey(binding)
        }

        val builder = activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(android.R.string.cancel, null)

        activity.setupDialogStuff(binding.root, builder, R.string.reserved_keys_dialog_title) { dialog ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (adminSettingsHelper.updateReservedKeys(savedCustomKeys)) {
                    activity.toast(R.string.reserved_keys_saved)
                    onSaved(savedCustomKeys.toSet())
                    dialog.dismiss()
                } else {
                    binding.reservedKeyInputHint.error = activity.getString(R.string.reserved_key_invalid)
                }
            }
        }
    }

    private fun populateDefaultKeyChips(binding: DialogReservedKeysConfigBinding) {
        binding.chipDefaultReservedKeys.removeAllViews()
        defaultKeys.sorted().forEach { key ->
            binding.chipDefaultReservedKeys.addView(createChip(key))
        }
    }

    private fun refreshCustomKeyChips(binding: DialogReservedKeysConfigBinding) {
        binding.chipCustomReservedKeys.removeAllViews()

        if (savedCustomKeys.isEmpty()) {
            binding.textCustomReservedKeysEmpty.visibility = View.VISIBLE
            return
        }

        binding.textCustomReservedKeysEmpty.visibility = View.GONE
        savedCustomKeys.sorted().forEach { key ->
            binding.chipCustomReservedKeys.addView(createChip(key, true) {
                savedCustomKeys.remove(key)
                refreshCustomKeyChips(binding)
            })
        }
    }

    private fun addCustomKey(binding: DialogReservedKeysConfigBinding) {
        val rawKey = binding.reservedKeyInput.text?.toString()?.trim().orEmpty()
        binding.reservedKeyInputHint.error = null

        when {
            rawKey.isBlank() -> binding.reservedKeyInputHint.error = activity.getString(R.string.reserved_key_required)
            !adminSettingsHelper.isReservedKeyValid(rawKey) -> binding.reservedKeyInputHint.error = activity.getString(R.string.reserved_key_invalid)
            rawKey in defaultKeys || rawKey in savedCustomKeys -> binding.reservedKeyInputHint.error = activity.getString(R.string.reserved_key_duplicate)
            else -> {
                savedCustomKeys.add(rawKey)
                binding.reservedKeyInput.text?.clear()
                refreshCustomKeyChips(binding)
            }
        }
    }

    private fun createChip(text: String, closeable: Boolean = false, onClose: (() -> Unit)? = null): Chip {
        return Chip(activity).apply {
            this.text = text
            isCloseIconVisible = closeable
            onClose?.let { listener ->
                setOnCloseIconClickListener { listener() }
            }
        }
    }
}
