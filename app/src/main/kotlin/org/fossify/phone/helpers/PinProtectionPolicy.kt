package org.fossify.phone.helpers

import org.fossify.phone.utils.SecurityUtils

data class PinValidationResult(
    val isValid: Boolean,
    val lockoutTriggered: Boolean
)

class PinProtectionPolicy(
    private val config: Config,
    private val clock: () -> Long = System::currentTimeMillis
) {
    internal val maxAttempts = 5
    internal val lockoutMinutes = 5L

    fun isLockedOut(): Boolean = config.adminPinLockoutUntil > clock()

    fun remainingLockSeconds(): Long =
        ((config.adminPinLockoutUntil - clock()) / 1000).coerceAtLeast(0)

    fun validatePin(enteredPin: String): PinValidationResult {
        val storedPinHash = config.adminPinHash ?: return PinValidationResult(false, false)

        val isValid = SecurityUtils.verifySecret(enteredPin, storedPinHash)
        if (isValid) {
            config.adminPinFailedAttempts = 0
            config.adminPinLockoutUntil = 0L
            return PinValidationResult(true, false)
        }

        val attempts = (config.adminPinFailedAttempts + 1).coerceAtMost(maxAttempts)
        config.adminPinFailedAttempts = attempts

        if (attempts >= maxAttempts) {
            config.adminPinLockoutUntil = clock() + lockoutMinutes * 60_000
            return PinValidationResult(false, true)
        }

        return PinValidationResult(false, false)
    }
}
