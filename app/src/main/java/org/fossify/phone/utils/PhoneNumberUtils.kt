package org.fossify.phone.utils

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber
import org.fossify.phone.models.CallLogItem
import org.fossify.phone.models.RecentCall
import android.telephony.PhoneNumberUtils as AndroidPhoneNumberUtils
import java.util.Locale

/**
 * Phone number utilities for call logging system
 * Uses libphonenumber for international validation and normalization
 */
object PhoneNumberUtils {

    private val phoneUtil = PhoneNumberUtil.getInstance()
    private val DEFAULT_REGION = Locale.getDefault().country

    /**
     * Validate phone number for call logging
     */
    fun isValidPhoneNumber(phoneNumber: String?, region: String = DEFAULT_REGION): Boolean {
        if (phoneNumber.isNullOrBlank()) return false

        return try {
            val number = phoneUtil.parse(phoneNumber, region)
            phoneUtil.isValidNumber(number)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Normalize phone number for storage and comparison
     */
    fun normalizePhoneNumber(phoneNumber: String?, region: String = DEFAULT_REGION): String? {
        if (phoneNumber.isNullOrBlank()) return null

        return try {
            val number = phoneUtil.parse(phoneNumber, region)
            phoneUtil.format(number, PhoneNumberUtil.PhoneNumberFormat.E164)
        } catch (e: Exception) {
            phoneNumber.trim()
        }
    }

    /**
     * Format phone number for display
     */
    fun formatPhoneNumber(phoneNumber: String?, region: String = DEFAULT_REGION): String? {
        if (phoneNumber.isNullOrBlank()) return null

        return try {
            val number = phoneUtil.parse(phoneNumber, region)
            phoneUtil.format(number, PhoneNumberUtil.PhoneNumberFormat.NATIONAL)
        } catch (e: Exception) {
            phoneNumber
        }
    }

    /**
     * Check if two phone numbers represent the same number
     */
    fun areSamePhoneNumber(number1: String?, number2: String?, region: String = DEFAULT_REGION): Boolean {
        if (number1.isNullOrBlank() || number2.isNullOrBlank()) return false

        return try {
            val parsed1 = phoneUtil.parse(number1, region)
            val parsed2 = phoneUtil.parse(number2, region)
            parsed1 == parsed2
        } catch (e: Exception) {
            AndroidPhoneNumberUtils.compare(number1, number2)
        }
    }

    /**
     * Extract phone number from CallLogItem
     */
    fun getPhoneNumberFromCallLog(callLog: CallLogItem): String? {
        return when (callLog) {
            is RecentCall -> callLog.phoneNumber
            else -> null
        }
    }

    /**
     * Get international format from national format
     */
    fun toInternational(phoneNumber: String?, region: String = DEFAULT_REGION): String? {
        if (phoneNumber.isNullOrBlank()) return null

        return try {
            val number = phoneUtil.parse(phoneNumber, region)
            phoneUtil.format(number, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
        } catch (e: Exception) {
            phoneNumber
        }
    }

    /**
     * Get region code from phone number
     */
    fun getRegionCode(phoneNumber: String?): String {
        return try {
            val number = phoneUtil.parse(phoneNumber ?: "", DEFAULT_REGION)
            phoneUtil.getRegionCodeForNumber(number)
        } catch (e: Exception) {
            DEFAULT_REGION
        }
    }

    /**
     * Is premium rate number
     */
    fun isPremiumRate(phoneNumber: String?, region: String = DEFAULT_REGION): Boolean {
        if (phoneNumber.isNullOrBlank()) return false

        return try {
            val number = phoneUtil.parse(phoneNumber, region)
            phoneUtil.getNumberType(number) == PhoneNumberUtil.PhoneNumberType.PREMIUM_RATE
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Extension: Safe phone number normalization
     */
    fun String?.normalizePhone(): String? = normalizePhoneNumber(this)

    /**
     * Extension: Safe phone number validation
     */
    fun String?.isValidPhone(): Boolean = isValidPhoneNumber(this)

    /**
     * Extension: Format for display
     */
    fun String?.formatForDisplay(): String? = formatPhoneNumber(this)
}
