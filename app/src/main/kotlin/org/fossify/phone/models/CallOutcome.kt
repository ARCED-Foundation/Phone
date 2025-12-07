package org.fossify.phone.models

/**
 * Call outcome values persisted with each call log.
 */
enum class CallOutcome(val value: String) {
    ANSWERED("answered"),
    NO_ANSWER("no_answer"),
    BUSY("busy"),
    FAILED("failed"),
    REJECTED("rejected");

    companion object {
        fun fromValue(value: String): CallOutcome =
            entries.firstOrNull { it.value == value } ?: NO_ANSWER
    }
}
