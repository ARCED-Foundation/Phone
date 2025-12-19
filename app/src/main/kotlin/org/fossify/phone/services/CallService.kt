package org.fossify.phone.services

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import org.fossify.commons.extensions.canUseFullScreenIntent
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.helpers.PERMISSION_POST_NOTIFICATIONS
import org.fossify.phone.activities.CallActivity
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.isOutgoing
import org.fossify.phone.extensions.keyguardManager
import org.fossify.phone.extensions.powerManager
import org.fossify.phone.helpers.CallManager
import org.fossify.phone.helpers.CallNotificationManager
import org.fossify.phone.helpers.NoCall
import org.fossify.phone.helpers.OdkIntentStateHolder
import org.fossify.phone.models.Events
import org.fossify.phone.models.CallOutcome
import org.fossify.phone.utils.Logger
import org.fossify.phone.utils.TimestampUtils
import org.greenrobot.eventbus.EventBus
import org.fossify.phone.helpers.CallManagerListener
import org.fossify.phone.models.AudioRoute
import org.fossify.phone.helpers.CallLogger
import org.fossify.phone.activities.SurveyDataCollectionActivity
import org.fossify.phone.activities.PostCallMetadataActivity
import java.util.UUID
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

class CallService : InCallService(), CallManagerListener {
    private val callNotificationManager by lazy { CallNotificationManager(this) }
    companion object {
        // Prevent duplicate metadata prompts for the same call log
        private val promptedCallLogs: MutableSet<UUID> =
            Collections.newSetFromMap(ConcurrentHashMap())
    }

    /**
     * Performs atomic cleanup of ODK session and intent state to prevent race conditions
     */
    private fun performAtomicOdkCleanup() {
        Logger.callDetection("Performing atomic ODK cleanup")
        // Clear intent state first
        OdkIntentStateHolder.clear()
        // Then reset session
        CallManager.resetOdkSession()
    }

    private val callListener = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)

            Logger.callDetection("Call state changed: ${getCallStateName(state)} for call ${call.hashCode()}")

            // Detect call start events
            if (state == Call.STATE_RINGING) {
                handleCallStart(call)
            } else if (state == Call.STATE_ACTIVE) {
                handleCallAnswered(call)
            } else if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
                handleCallEnd(call)
            }

            // Original notification management
            if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
                callNotificationManager.cancelNotification()
            } else {
                callNotificationManager.setupNotification()
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.onCallAdded(call)
        CallManager.addListener(this)
        CallManager.inCallService = this
        call.registerCallback(callListener)

        // Incoming/Outgoing (locked): high priority (FSI)
        // Incoming (unlocked): if user opted in, low priority ➜ manual activity start, otherwise high priority (FSI)
        // Outgoing (unlocked): low priority ➜ manual activity start
        val isIncoming = !call.isOutgoing()
        val isDeviceLocked = !powerManager.isInteractive || keyguardManager.isDeviceLocked
        val lowPriority = when {
            isIncoming && isDeviceLocked -> false
            !isIncoming && isDeviceLocked -> false
            isIncoming && !isDeviceLocked -> config.alwaysShowFullscreen
            else -> true
        }

        callNotificationManager.setupNotification(lowPriority)
        if (isIncoming || lowPriority || !hasPermission(PERMISSION_POST_NOTIFICATIONS) || !canUseFullScreenIntent()) {
            try {
                startActivity(CallActivity.getStartIntent(this))
            } catch (_: Exception) {
                // seems like startActivity can throw AndroidRuntimeException and
                // ActivityNotFoundException, not yet sure when and why, lets show a notification
                callNotificationManager.setupNotification()
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callListener)
        val wasPrimaryCall = call == CallManager.getPrimaryCall()
        CallManager.onCallRemoved(call)
        if (CallManager.getPhoneState() == NoCall) {
            CallManager.inCallService = null
            callNotificationManager.cancelNotification()
        } else {
            callNotificationManager.setupNotification()
            if (wasPrimaryCall) {
                startActivity(CallActivity.getStartIntent(this))
            }
        }

        EventBus.getDefault().post(Events.RefreshCallLog)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        if (audioState != null) {
            CallManager.onAudioStateChanged(audioState)
        }
    }

    override fun onDestroy() {
        CallManager.removeListener(this)
        super.onDestroy()
        callNotificationManager.cancelNotification()
    }

    /**
     * Handle call start event (ringing)
     */
    private fun handleCallStart(call: Call) {
        val isIncoming = !call.isOutgoing()
        val phoneNumber = getPhoneNumber(call)
        val callStartTimestamp = TimestampUtils.now()

        Logger.callDetection("Call started - Direction: ${if (isIncoming) "incoming" else "outgoing"}, Number: $phoneNumber, Timestamp: $callStartTimestamp")

        // CallManager integration - this will be enhanced in T021 for direction detection
        // For now, just log the event for future CallLog creation
    }

    /**
     * Handle call answered event
     */
    private fun handleCallAnswered(call: Call) {
        val phoneNumber = getPhoneNumber(call)
        val answerTimestamp = TimestampUtils.now()

        Logger.callDetection("Call answered - Number: $phoneNumber, Timestamp: $answerTimestamp")

    }

    /**
     * Handle call end event
     */
    private fun handleCallEnd(call: Call) {
        val phoneNumber = getPhoneNumber(call)
        val callEndTimestamp = TimestampUtils.now()

        Logger.callDetection("Call ended - Number: $phoneNumber, Timestamp: $callEndTimestamp")

    }

    /**
     * Get phone number from call
     */
    private fun getPhoneNumber(call: Call): String {
        return call.details.handle.schemeSpecificPart
    }

    /**
     * Get human-readable call state name
     */
    private fun getCallStateName(state: Int): String {
        return when (state) {
            Call.STATE_NEW -> "NEW"
            Call.STATE_RINGING -> "RINGING"
            Call.STATE_DIALING -> "DIALING"
            Call.STATE_ACTIVE -> "ACTIVE"
            Call.STATE_HOLDING -> "HOLDING"
            Call.STATE_DISCONNECTING -> "DISCONNECTING"
            Call.STATE_DISCONNECTED -> "DISCONNECTED"
            Call.STATE_SELECT_PHONE_ACCOUNT -> "SELECT_PHONE_ACCOUNT"
            else -> "UNKNOWN($state)"
        }
    }

    override fun onStateChanged() {
        // Handled by CallManager
    }

    override fun onAudioStateChanged(audioState: AudioRoute) {
        // Audio handled by CallManager
    }

    override fun onPrimaryCallChanged(call: Call) {
        // Primary call handled by CallManager
    }

    override fun onCallStarted(number: String, isOutgoing: Boolean) {
        Logger.callDetection("Call started: $number, outgoing: $isOutgoing")
    }

    override fun onCallActive(number: String, isOutgoing: Boolean) {
        Logger.callDetection("Call active: $number, outgoing: $isOutgoing")
    }

    override fun onCallEnded() {
        Logger.callDetection("All calls ended")
    }

    override fun onCallStarted(call: Call, number: String, isOutgoing: Boolean) {
        Logger.callDetection("Enhanced call started: $number, outgoing: $isOutgoing")
    }

    override fun onCallActive(call: Call, number: String, isOutgoing: Boolean) {
        Logger.callDetection("Enhanced call active: $number, outgoing: $isOutgoing")
    }

    override fun onCallOutcomeDetected(
        call: Call,
        outcome: CallOutcome,
        outcomeDetail: String?,
        durationSeconds: Double,
        startTimeMs: Long,
        endTimeMs: Long
    ) {
        val number = getPhoneNumber(call)
        Logger.callDetection("OUTCOME: $outcome | Number: $number | Duration: ${String.format("%.2f", durationSeconds)}s | Detail: ${outcomeDetail ?: "none"}")
        val isOutgoing = call.isOutgoing()
        val endTime = endTimeMs
        val startTime = startTimeMs

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val callLogger = CallLogger(this@CallService)
                val intentState = OdkIntentStateHolder.current()
                val callExtras = call.details.extras

                // Treat the call as ODK-driven if we still have a live ODK session/config
                // or if any of the known flags are present on the call extras.
                val hasOdkSession = CallManager.isOdkSessionActive()
                val isValidSession = CallManager.isValidOdkSession()
                val isSessionReady = CallManager.isOdkSessionReady()
                val odkSessionState = CallManager.getOdkSessionState()
                val hasOdkConfig = intentState.config != null
                val stateFlag = intentState.extras["odk_call"]?.toString()?.toBooleanStrictOrNull() == true
                val stateInstanceFlag = intentState.extras["odkCollectInstanceId"] != null
                val extrasFlag = callExtras?.getString("odk_call")?.toBooleanStrictOrNull() == true ||
                    callExtras?.getBoolean("odk_call", false) == true
                val extrasInstanceFlag = callExtras?.getString("odkCollectInstanceId") != null

                // Enhanced ODK detection with logging for debugging
                Logger.callDetection("ODK Detection Debug - Duration: ${String.format("%.3f", durationSeconds)}s")
                Logger.callDetection("  - hasOdkSession: $hasOdkSession")
                Logger.callDetection("  - isValidSession: $isValidSession")
                Logger.callDetection("  - isSessionReady: $isSessionReady")
                Logger.callDetection("  - odkSessionState: $odkSessionState")
                Logger.callDetection("  - hasOdkConfig: ${hasOdkConfig != null}")
                Logger.callDetection("  - stateFlag: $stateFlag")
                Logger.callDetection("  - stateInstanceFlag: $stateInstanceFlag")
                Logger.callDetection("  - extrasFlag: $extrasFlag")
                Logger.callDetection("  - extrasInstanceFlag: $extrasInstanceFlag")
                Logger.callDetection("  - intentState extras: ${intentState.extras}")
                Logger.callDetection("  - callExtras: ${callExtras?.keySet()}")
                Logger.callDetection("  - intentState isFresh: ${intentState.isFresh()}")
                Logger.callDetection("  - OdkIntentStateHolder isStateValid: ${OdkIntentStateHolder.isStateValid()}")

                // Enhanced weighted ODK detection - prioritize certain sources
                val isOdkCall = when {
                    // Highest priority: Active ODK session
                    hasOdkSession -> {
                        Logger.callDetection("ODK call detected via ACTIVE SESSION")
                        true
                    }
                    // High priority: Enhanced intent state validation with timestamp check
                    hasOdkConfig || OdkIntentStateHolder.isStateValidAndFresh() -> {
                        val primaryIndicator = OdkIntentStateHolder.getPrimaryOdkIndicator()
                        Logger.callDetection("ODK call detected via INTENT STATE (primary indicator: $primaryIndicator, fresh: ${OdkIntentStateHolder.current().isFresh()})")
                        true
                    }
                    // Medium priority: Instance ID (strong ODK indicator)
                    stateInstanceFlag || extrasInstanceFlag -> {
                        Logger.callDetection("ODK call detected via INSTANCE ID")
                        true
                    }
                    // Low priority: odk_call flag with no duration restriction - always process ODK intent values
                    (stateFlag || extrasFlag) -> { // No minimum duration requirement
                        Logger.callDetection("ODK call detected via ODK_CALL FLAG (duration: ${String.format("%.3f", durationSeconds)}s)")
                        true
                    }
                    else -> {
                        Logger.callDetection("Call classified as NON-ODK (duration: ${String.format("%.3f", durationSeconds)}s)")
                        false
                    }
                }

                Logger.callDetection("Final ODK classification: $isOdkCall (Duration: ${String.format("%.3f", durationSeconds)}s)")

                // Only clear intent state if definitely not an ODK call
                if (!isOdkCall) {
                    Logger.callDetection("Clearing ODK intent state - call is not ODK")
                    OdkIntentStateHolder.clear()
                } else {
                    Logger.callDetection("Preserving ODK intent state - call is ODK")
                }
                val createdLog = callLogger.createCallLog(
                    phoneNumber = number,
                    isOutgoing = isOutgoing,
                    startTime = startTime,
                    endTime = endTime,
                    durationSeconds = durationSeconds,
                    outcome = outcome,
                    outcomeDetail = outcomeDetail,
                    skipDuplicateCheck = isOdkCall,
                    isOdkCall = isOdkCall,
                    deferSync = false,  // Don't defer sync - create immediately for both ODK and non-ODK calls
                    intentState = intentState
                )
            if (createdLog == null) {
                Logger.callDetection("Call log creation skipped (duplicate or error)")
                // Clean up ODK session even if log creation failed to prevent orphaned sessions
                if (isOdkCall) {
                    Logger.callDetection("Cleaning up ODK session due to log creation failure")
                    CallManager.resetOdkSession()
                    OdkIntentStateHolder.clear()
                }
            } else {
                Logger.callDetection("Call log processed successfully: ${createdLog.callLogId}")
                Logger.callDetection("ODK call form suppression: ${if (isOdkCall) "SUPPRESSED" else "TRIGGERED"}")

                // Handle sync creation based on call type
                if (createdLog != null) {
                    if (isOdkCall) {
                        // For ODK calls, track the call for deferred sync
                        Logger.callDetection("Tracking ODK call for deferred sync: ${createdLog.callLogId}")
                        CallManager.trackPendingOdkCall(createdLog.callLogId.toString())
                    } else {
                        // For non-ODK calls, do NOT create sync immediately - wait for form completion
                        Logger.callDetection("Deferring sync for non-ODK call until form completed: ${createdLog.callLogId}")

                        // Only show form for non-ODK calls
                        if (promptedCallLogs.add(createdLog.callLogId)) {
                            Logger.callDetection("Launching PostCallMetadataActivity for non-ODK call")
                            withContext(Dispatchers.Main) {
                                PostCallMetadataActivity.launch(
                                    this@CallService,
                                    createdLog.callLogId
                                )
                            }
                        }
                    }
                }

                // Perform immediate atomic ODK session cleanup to prevent race conditions
                if (isOdkCall) {
                    Logger.callDetection("Performing immediate atomic ODK session cleanup")
                    performAtomicOdkCleanup()
                }
            }
        } catch (e: Exception) {
            Logger.callDetection("Call outcome processing failed: ${e.message}")
        }
    }
    }
}
