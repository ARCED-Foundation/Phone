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
import org.json.JSONObject
import org.greenrobot.eventbus.EventBus
import org.fossify.phone.helpers.CallManagerListener
import org.fossify.phone.models.AudioRoute
import org.fossify.phone.helpers.CallLogger
import org.fossify.phone.activities.SurveyDataCollectionActivity
import org.fossify.phone.activities.PostCallMetadataActivity
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CallService : InCallService(), CallManagerListener {
    private val callNotificationManager by lazy { CallNotificationManager(this) }

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
        if (
            lowPriority
            || !hasPermission(PERMISSION_POST_NOTIFICATIONS)
            || !canUseFullScreenIntent()
        ) {
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
                val extrasJson = runCatching { JSONObject(call.details.extras?.toString() ?: "") }.getOrNull()
                val extrasFlag = extrasJson
                    ?.optString("odk_call", "false")
                    ?.equals("true", ignoreCase = true) == true
                val stateFlag = intentState.extras["odk_call"]?.toString()?.toBooleanStrictOrNull() == true
                val isOdkCall = CallManager.isOdkSessionActive() ||
                    stateFlag ||
                    extrasFlag ||
                    call.details.extras?.getString("odk_call")?.toBooleanStrictOrNull() == true ||
                    call.details.extras?.getBoolean("odk_call", false) == true ||
                    call.details.extras?.getString("odkCollectInstanceId") != null
                if (!isOdkCall) {
                    OdkIntentStateHolder.clear()
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
                    isOdkCall = isOdkCall
            )
            if (createdLog == null) {
                Logger.callDetection("Call log creation skipped (duplicate or error)")
            } else {
                Logger.callDetection("Call log processed successfully: ${createdLog.callLogId}")
                if (isOdkCall) {
                    CallManager.resetOdkSession()
                    OdkIntentStateHolder.clear()
                }
                if (!isOdkCall) {
                    withContext(Dispatchers.Main) {
                        PostCallMetadataActivity.launch(
                            this@CallService,
                            createdLog.callLogId
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Logger.callDetection("Call outcome processing failed: ${e.message}")
        }
    }
    }
}
