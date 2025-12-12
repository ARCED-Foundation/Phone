package org.fossify.phone.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import org.fossify.phone.extensions.getStateCompat
import org.fossify.phone.extensions.hasCapability
import org.fossify.phone.extensions.isConference
import org.fossify.phone.extensions.isOutgoing
import org.fossify.phone.models.AudioRoute
import org.fossify.phone.models.OdkCallTrackingInfo
import org.fossify.phone.models.OdkCallRecord
import org.fossify.phone.models.ODKSession
import org.fossify.phone.models.CallDirection
import org.fossify.phone.models.SessionStatus
import org.fossify.phone.models.CallOutcome
import org.fossify.phone.extensions.saveOdkSession
import org.fossify.phone.extensions.clearOdkSessionState
import org.fossify.phone.extensions.ODK_SESSION_TIMEOUT_MS
import kotlinx.serialization.json.Json
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet
import org.fossify.phone.utils.PerformanceMonitor

// inspired by https://github.com/Chooloo/call_manage
class CallManager {
    companion object {
        @SuppressLint("StaticFieldLeak")
        var inCallService: InCallService? = null
        private var call: Call? = null
        private val calls = mutableListOf<Call>()
        internal val listeners = CopyOnWriteArraySet<CallManagerListener>()
        private val mainHandler = Handler(Looper.getMainLooper())

        // Track per-call state history for proper lifecycle management
        private val callStateHistory = mutableMapOf<Call, CallStateHistory>()

        // ODK Session Management
        private var odkSession: ODKSession? = null
        val activeOdkCallTracking = mutableMapOf<String, OdkCallTrackingInfo>()
        private var odkSessionContext: Context? = null

        // DTMF tone duration
        private const val DIALPAD_TONE_LENGTH_MS = 120L

        data class CallStateHistory(
            var previousState: Int = Call.STATE_DISCONNECTED,
            var hasNotifiedStart: Boolean = false,
            var hasNotifiedEnd: Boolean = false,
            var isInStateUpdate: Boolean = false,  // Prevent concurrent notifications
            var startTimeMillis: Long? = null,
            var connectTimeMillis: Long? = null
        )

        data class CallTiming(
            val durationSeconds: Double,
            val startTimeMs: Long,
            val connectTimeMs: Long?
        )

        fun onCallAdded(call: Call) {
            this.call = call
            calls.add(call)

            // Initialize state history for new call
            callStateHistory[call] = CallStateHistory()

            for (listener in listeners) {
                listener.onPrimaryCallChanged(call)
            }
            call.registerCallback(object : Call.Callback() {
                override fun onStateChanged(call: Call, state: Int) {
                    handleCallStateChange(call, state)
                }

                override fun onDetailsChanged(call: Call, details: Call.Details) {
                    updateState()
                }

                override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: MutableList<Call>) {
                    updateState()
                }
            })
        }

        fun onCallRemoved(call: Call) {
            calls.remove(call)
            callStateHistory.remove(call)
            updateState()
        }

        fun onAudioStateChanged(audioState: CallAudioState) {
            val route = AudioRoute.fromRoute(audioState.route) ?: return
            for (listener in listeners) {
                listener.onAudioStateChanged(route)
            }
        }

        fun getPhoneState(): PhoneState {
            return when (calls.size) {
                0 -> NoCall
                1 -> SingleCall(calls.first())
                2 -> {
                    val active = calls.find { it.getStateCompat() == Call.STATE_ACTIVE }
                    val newCall = calls.find { it.getStateCompat() == Call.STATE_CONNECTING || it.getStateCompat() == Call.STATE_DIALING }
                    val onHold = calls.find { it.getStateCompat() == Call.STATE_HOLDING }
                    if (active != null && newCall != null) {
                        TwoCalls(newCall, active)
                    } else if (newCall != null && onHold != null) {
                        TwoCalls(newCall, onHold)
                    } else if (active != null && onHold != null) {
                        TwoCalls(active, onHold)
                    } else {
                        TwoCalls(calls[0], calls[1])
                    }
                }

                else -> {
                    val conference = calls.find { it.isConference() } ?: return NoCall
                    val secondCall = if (conference.children.size + 1 != calls.size) {
                        calls.filter { !it.isConference() }
                            .subtract(conference.children.toSet())
                            .firstOrNull()
                    } else {
                        null
                    }
                    if (secondCall == null) {
                        SingleCall(conference)
                    } else {
                        val newCallState = secondCall.getStateCompat()
                        if (newCallState == Call.STATE_ACTIVE || newCallState == Call.STATE_CONNECTING || newCallState == Call.STATE_DIALING) {
                            TwoCalls(secondCall, conference)
                        } else {
                            TwoCalls(conference, secondCall)
                        }
                    }
                }
            }
        }

        private fun getCallAudioState() = inCallService?.callAudioState

        fun getSupportedAudioRoutes(): Array<AudioRoute> {
            return AudioRoute.values().filter {
                val supportedRouteMask = getCallAudioState()?.supportedRouteMask
                if (supportedRouteMask != null) {
                    supportedRouteMask and it.route == it.route
                } else {
                    false
                }
            }.toTypedArray()
        }

        fun getCallAudioRoute() = AudioRoute.fromRoute(getCallAudioState()?.route)

        fun setAudioRoute(newRoute: Int) {
            inCallService?.setAudioRoute(newRoute)
        }

        private fun updateState() {
            val primaryCall = when (val phoneState = getPhoneState()) {
                is NoCall -> null
                is SingleCall -> phoneState.call
                is TwoCalls -> phoneState.active
            }

            var notify = true
            var previousState = call?.getStateCompat()

            if (primaryCall == null) {
                call = null
                // Notify call ended for all calls
                notifyCallEndedEvents()
            } else if (primaryCall != call) {
                // Call started event
                if (call == null && previousState == null) {
                    notifyCallStartedEvents(primaryCall)
                }
                call = primaryCall
                for (listener in listeners) {
                    listener.onPrimaryCallChanged(primaryCall)
                }
                notify = false
            }

            // remove all disconnected calls manually in case they are still here
            calls.removeAll { it.getStateCompat() == Call.STATE_DISCONNECTED }
        }

        private fun notifyStateChangedListeners() {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                for (listener in listeners) {
                    listener.onStateChanged()
                }
            } else {
                mainHandler.post {
                    for (listener in listeners) {
                        listener.onStateChanged()
                    }
                }
            }
        }

        // Handle individual call state changes with proper lifecycle tracking
        private fun handleCallStateChange(call: Call, newState: Int) {
            val detectionStart = PerformanceMonitor.startCallDetection()
            try {
                val stateHistory = callStateHistory[call]
                if (stateHistory == null) {
                    // Initialize state history if missing
                    callStateHistory[call] = CallStateHistory(previousState = newState)
                    return
                }

                val oldState = stateHistory.previousState
                val callDetails = call.details
                val detailCreationTime = callDetails.creationTimeMillis.takeIf { it > 0 }
                val detailConnectTime = callDetails.connectTimeMillis.takeIf { it > 0 }

                if (detailCreationTime != null) {
                    stateHistory.startTimeMillis = stateHistory.startTimeMillis?.let { minOf(it, detailCreationTime) } ?: detailCreationTime
                }
                if (detailConnectTime != null) {
                    stateHistory.connectTimeMillis = stateHistory.connectTimeMillis?.let { minOf(it, detailConnectTime) } ?: detailConnectTime
                }

                // Only process meaningful transitions
                val stateChanged = oldState != newState
                if (stateChanged) {
                    android.util.Log.d("CallManager", "Call state transition: ${callStateToString(oldState)} -> ${callStateToString(newState)}")

                    // Handle call start transitions (IDLE -> DIALING/CONNECTING/ACTIVE)
                    if (isStartTransition(oldState, newState)) {
                        if (stateHistory.startTimeMillis == null) {
                            stateHistory.startTimeMillis = detailCreationTime ?: System.currentTimeMillis()
                        }
                        if (!stateHistory.hasNotifiedStart) {
                            notifyCallStartedEvents(call)
                            stateHistory.hasNotifiedStart = true
                        }
                    }

                    // Handle call active transition (DIALING/CONNECTING -> ACTIVE)
                    if (isCallActiveTransition(oldState, newState)) {
                        val resolvedConnectTime = detailConnectTime ?: System.currentTimeMillis()
                        if (stateHistory.connectTimeMillis == null || (detailConnectTime != null && detailConnectTime < stateHistory.connectTimeMillis!!)) {
                            stateHistory.connectTimeMillis = resolvedConnectTime
                        }
                        notifyCallActiveEvents(call)
                    }

                    // Handle call end transitions and detect call outcome
                    if (isEndTransition(oldState, newState)) {
                        if (!stateHistory.hasNotifiedEnd) {
                            val outcome = detectCallOutcome(call, oldState, newState)
                            val endTimeMs = System.currentTimeMillis()
                            val timing = calculateCallTiming(call, stateHistory, endTimeMs)
                            val outcomeDetail = buildOutcomeDetail(
                                call = call,
                                outcome = outcome,
                                durationSeconds = timing.durationSeconds,
                                startTimeMs = timing.startTimeMs,
                                endTimeMs = endTimeMs,
                                oldState = oldState,
                                newState = newState,
                                connectTimeMs = timing.connectTimeMs
                            )
                            notifyCallEndedEvents()
                            notifyCallOutcomeEvents(call, outcome, outcomeDetail, timing.durationSeconds, timing.startTimeMs, endTimeMs)
                            stateHistory.hasNotifiedEnd = true
                        }
                    }
                }

                // Always update the previous state
                stateHistory.previousState = newState
                if (stateChanged) {
                    notifyStateChangedListeners()
                }
            } finally {
                PerformanceMonitor.endCallDetection(detectionStart)
            }
        }

        // Check if this is a call start transition
        private fun isStartTransition(oldState: Int, newState: Int): Boolean {
            return oldState == Call.STATE_DISCONNECTED &&
                   (newState == Call.STATE_DIALING ||
                    newState == Call.STATE_CONNECTING ||
                    newState == Call.STATE_ACTIVE)
        }

        // Check if this is a call active transition (when call becomes connected)
        private fun isCallActiveTransition(oldState: Int, newState: Int): Boolean {
            return (oldState == Call.STATE_DIALING ||
                    oldState == Call.STATE_CONNECTING ||
                    oldState == Call.STATE_RINGING) &&
                   newState == Call.STATE_ACTIVE
        }

        // Check if this is a call end transition
        private fun isEndTransition(oldState: Int, newState: Int): Boolean {
            return (oldState == Call.STATE_DIALING ||
                    oldState == Call.STATE_CONNECTING ||
                    oldState == Call.STATE_ACTIVE ||
                    oldState == Call.STATE_HOLDING) &&
                   (newState == Call.STATE_DISCONNECTED ||
                    newState == Call.STATE_DISCONNECTING)
        }

        // Convert call state to string for logging
        private fun callStateToString(state: Int): String {
            return when (state) {
                Call.STATE_ACTIVE -> "ACTIVE"
                Call.STATE_DISCONNECTED -> "DISCONNECTED"
                Call.STATE_DISCONNECTING -> "DISCONNECTING"
                Call.STATE_DIALING -> "DIALING"
                Call.STATE_CONNECTING -> "CONNECTING"
                Call.STATE_HOLDING -> "HOLDING"
                Call.STATE_RINGING -> "RINGING"
                else -> "UNKNOWN($state)"
            }
        }

        private fun notifyCallStartedEvents(call: Call) {
            val number = getPhoneNumber(call)
            val isOutgoing = isCallOutgoing(call)
            // Use Call object hash for more stable ID generation
            val callId = "call_${System.currentTimeMillis()}_${call.hashCode()}_${Thread.currentThread().id}"

            android.util.Log.d("CallManager", "Call started: $number, isOutgoing: $isOutgoing, state: ${callStateToString(call.getStateCompat())}")

            // Check if ODK session is active and track this call
            if (isOdkSessionActive() && odkSessionContext != null) {
                try {
                    val direction = if (isOutgoing) CallDirection.OUTGOING else CallDirection.INCOMING
                    addActiveOdkCall(callId, call, number, direction)
                    android.util.Log.d("CallManager", "ODK call tracking started for $number with ID: $callId")
                } catch (e: Exception) {
                    android.util.Log.e("CallManager", "Failed to track ODK call: ${e.message}")
                }
            }

            for (listener in listeners) {
                // Use only enhanced method for reliable tracking
                listener.onCallStarted(call, number, isOutgoing)
            }
        }

        private fun notifyCallActiveEvents(call: Call) {
            val number = getPhoneNumber(call)
            val trackingInfo = activeOdkCallTracking.values.find { it.number == number }
            val isOutgoing = trackingInfo?.direction == CallDirection.OUTGOING ?: isCallOutgoing(call)
            android.util.Log.d("CallManager", "Call became active: $number, isOutgoing: $isOutgoing (tracking: ${trackingInfo?.direction}), state: ${callStateToString(call.getStateCompat())}")

            // Update ODK call tracking with connection time
            if (isOdkSessionActive()) {
                try {
                    val callId = findCallIdByNumber(number)
                    if (callId != null) {
                        val connectTimestamp = call.details.connectTimeMillis.takeIf { it > 0 } ?: System.currentTimeMillis()
                        updateOdkCallConnection(callId, connectTimestamp)
                        android.util.Log.d("CallManager", "ODK call connected for $number at $connectTimestamp")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("CallManager", "Failed to update ODK call connection: ${e.message}")
                }
            }

            for (listener in listeners) {
                listener.onCallActive(call, number, isOutgoing)
            }
        }

        private fun notifyCallEndedEvents() {
            android.util.Log.d("CallManager", "Call ended event triggered for all calls")

            // Complete ODK call tracking for all active calls
            val session = odkSession
            if (isOdkSessionActive() && session != null) {
                try {
                    val currentTime = System.currentTimeMillis()
                    val completedRecords = mutableListOf<OdkCallRecord>()

                    // Complete all active ODK calls
                    activeOdkCallTracking.entries.forEach { (callId, trackingInfo) ->
                        val duration = if (trackingInfo.connectTime != null) {
                            (currentTime - trackingInfo.connectTime!!) / 1000.0
                        } else {
                            0.0 // Failed call
                        }

                        val record = completeOdkCall(callId, duration, trackingInfo.connectTime != null)
                        if (record != null) {
                            completedRecords.add(record)
                            android.util.Log.d("CallManager", "ODK call completed: ${record.phoneNumber}, duration: ${record.duration}s")
                        }
                    }

                    // Add completed records to session
                    completedRecords.forEach { record ->
                        addOdkCallRecord(record)
                    }

                    android.util.Log.d("CallManager", "Completed ${completedRecords.size} ODK calls")

                    // T009: Auto-return full data if configured
                    if (session.autoReturnDisconnect && activeOdkCallTracking.isEmpty()) {
                        sendOdkReturnBroadcast(session)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("CallManager", "Failed to complete ODK calls: ${e.message}", e)
                }
            }

            for (listener in listeners) {
                listener.onCallEnded()
            }
        }

        private fun sendOdkReturnBroadcast(session: ODKSession) {
            val payloadIntent = Intent("org.fossify.phone.ODK_RETURN_FULL").apply {
                putExtra("value", getConcatenatedOdkValue())
                putExtra("total_duration", session.totalDuration)
                putExtra("successful_calls", session.successfulCallCount)
                putExtra("field_id", session.fieldId)
                putExtra("form_valid", true)

                val recordsJson = kotlinx.serialization.json.Json.encodeToString(session.callRecords)
                putExtra("records", recordsJson)
            }

            odkSessionContext?.sendBroadcast(payloadIntent)
            android.util.Log.d("CallManager", "ODK return broadcast broadcasted for local handlers")

            session.callingPackage?.let { callingPackage ->
                android.util.Log.d("CallManager", "Sending ODK return broadcast to $callingPackage")
                val externalIntent = Intent(payloadIntent).apply {
                    setPackage(callingPackage)
                }
                odkSessionContext?.sendBroadcast(externalIntent)
            }
        }

        fun getPrimaryCall(): Call? {
            return call
        }

        fun getConferenceCalls(): List<Call> {
            return calls.find { it.isConference() }?.children ?: emptyList()
        }

        fun accept() {
            call?.answer(VideoProfile.STATE_AUDIO_ONLY)
        }

        fun reject() {
            if (call != null) {
                val state = getState()
                if (state == Call.STATE_RINGING) {
                    call!!.reject(false, null)
                } else if (state != Call.STATE_DISCONNECTED && state != Call.STATE_DISCONNECTING) {
                    call!!.disconnect()
                }
            }
        }

        fun toggleHold(): Boolean {
            val isOnHold = getState() == Call.STATE_HOLDING
            if (isOnHold) {
                call?.unhold()
            } else {
                call?.hold()
            }
            return !isOnHold
        }

        fun swap() {
            if (calls.size > 1) {
                calls.find { it.getStateCompat() == Call.STATE_HOLDING }?.unhold()
            }
        }

        fun merge() {
            val conferenceableCalls = call!!.conferenceableCalls
            if (conferenceableCalls.isNotEmpty()) {
                call!!.conference(conferenceableCalls.first())
            } else {
                if (call!!.hasCapability(Call.Details.CAPABILITY_MERGE_CONFERENCE)) {
                    call!!.mergeConference()
                }
            }
        }

        fun addListener(listener: CallManagerListener) {
            listeners.add(listener)
        }

        fun removeListener(listener: CallManagerListener) {
            listeners.remove(listener)
        }

        fun getState() = getPrimaryCall()?.getStateCompat()

        fun keypad(char: Char) {
            call?.playDtmfTone(char)
            Handler().postDelayed({
                call?.stopDtmfTone()
            }, DIALPAD_TONE_LENGTH_MS)
        }

        // ODK Session Management Methods
        fun initializeOdkSession(context: Context, phoneNumber: String?, existingValue: String?, variant: String?, fieldId: String? = null, callingPackage: String? = null, autoReturnDisconnect: Boolean = true) {
    // Clear any existing session data to prevent overwrite
    odkSession?.callRecords?.let { _ -> odkSession = odkSession?.copy(callRecords = emptyList()) }
    activeOdkCallTracking.clear()

    odkSessionContext = context.applicationContext
    odkSession = ODKSession(
        phoneNumber = phoneNumber,
        existingValue = existingValue,
        sessionStartTime = System.currentTimeMillis(),
        sessionVariant = variant,
        fieldId = fieldId,
        callingPackage = callingPackage,
        autoReturnDisconnect = autoReturnDisconnect,
        isActive = true,
        callRecords = emptyList<OdkCallRecord>(),
        activeCalls = emptyMap<String, OdkCallTrackingInfo>()
    )
    // Save session to SharedPreferences
    odkSessionContext?.let { context ->
        context.saveOdkSession(odkSession!!)
    }
}

        fun getOdkSession(): ODKSession? = odkSession

        fun isOdkSessionActive(): Boolean = odkSession?.isActive == true

        fun endOdkSession(): ODKSession? {
            val completedSession = odkSession?.endSession()
            odkSession = completedSession
            odkSessionContext?.let { context ->
                context.saveOdkSession(odkSession!!)
            }
            odkSession = null
            activeOdkCallTracking.clear()
            odkSessionContext = null
            OdkIntentStateHolder.clear()
            return completedSession
        }

        fun getActiveOdkCalls(): Map<String, OdkCallTrackingInfo> = activeOdkCallTracking

        fun addActiveOdkCall(callId: String, call: Call, phoneNumber: String, direction: CallDirection): OdkCallTrackingInfo {
            val startTimestamp = call.details.creationTimeMillis.takeIf { it > 0 } ?: System.currentTimeMillis()
            val trackingInfo = OdkCallTrackingInfo(
                call = call,
                startTime = startTimestamp,
                connectTime = null,
                number = phoneNumber,
                direction = direction,
                isActive = true
            )

            activeOdkCallTracking[callId] = trackingInfo
            odkSession = odkSession?.addActiveCall(callId, trackingInfo)
            odkSessionContext?.let { context ->
                context.saveOdkSession(odkSession!!)
            }

            return trackingInfo
        }

        fun updateOdkCallConnection(callId: String, connectTime: Long): Boolean {
            return activeOdkCallTracking[callId]?.let { trackingInfo ->
                val updatedTrackingInfo = trackingInfo.copy(
                    connectTime = connectTime,
                    isActive = true
                )
                activeOdkCallTracking[callId] = updatedTrackingInfo
                odkSession = odkSession?.updateActiveCall(callId, updatedTrackingInfo)
                odkSessionContext?.let { context ->
                    context.saveOdkSession(odkSession!!)
                }
                true
            } ?: false
        }

        fun removeActiveOdkCall(callId: String): OdkCallTrackingInfo? {
            val trackingInfo = activeOdkCallTracking.remove(callId)
            trackingInfo?.let { info ->
                odkSession = odkSession?.removeActiveCall(callId)
                odkSessionContext?.let { context ->
                    context.saveOdkSession(odkSession!!)
                }
            }
            return trackingInfo
        }

        fun addOdkCallRecord(record: OdkCallRecord): ODKSession? {
            odkSession = odkSession?.addCallRecord(record)
            odkSessionContext?.let { context ->
                context.saveOdkSession(odkSession!!)
            }
            return odkSession
        }

        fun completeOdkCall(
            callId: String,
            duration: Double,
            wasSuccessful: Boolean,
            failureReason: String? = null
        ): OdkCallRecord? {
            val trackingInfo = activeOdkCallTracking[callId] ?: return null

            val record = OdkCallRecord(
                callId = callId,
                direction = trackingInfo.direction,
                phoneNumber = trackingInfo.number,
                duration = duration,
                startTime = trackingInfo.startTime,
                connectTime = trackingInfo.connectTime,
                endTime = System.currentTimeMillis(),
                wasSuccessful = wasSuccessful,
                failureReason = failureReason
            )

            removeActiveOdkCall(callId)
            return record
        }

        fun getConcatenatedOdkValue(): String {
            val session = odkSession ?: return ""
            val latestNumber = session.callRecords.lastOrNull()?.phoneNumber ?: session.phoneNumber
            val formattedNumber = formatOdkPhoneNumber(latestNumber)
            return when {
                formattedNumber != null -> "Outgoing call to $formattedNumber"
                !session.existingValue.isNullOrBlank() -> session.existingValue
                else -> ""
            }
        }

        private fun formatOdkPhoneNumber(rawNumber: String?): String? {
            val cleaned = rawNumber?.takeIf { it.isNotBlank() }?.trim() ?: return null
            if (cleaned.startsWith("+")) {
                return cleaned
            }
            val formatted = formatNumberToE164(cleaned)
            if (!formatted.isNullOrBlank()) {
                return formatted
            }
            return if (cleaned.all { it.isDigit() }) {
                "+$cleaned"
            } else {
                null
            }
        }

        private fun formatNumberToE164(number: String): String? {
            val region = odkSessionContext?.let { context ->
                val telephonyManager = context.getSystemService(TelephonyManager::class.java)
                telephonyManager?.simCountryIso?.takeIf { it.isNotBlank() }
                    ?: telephonyManager?.networkCountryIso?.takeIf { it.isNotBlank() }
            } ?: Locale.getDefault().country
            val normalizedRegion = region.uppercase(Locale.US).takeIf { it.isNotBlank() } ?: "US"
            return PhoneNumberUtils.formatNumberToE164(number, normalizedRegion)
        }

        fun resetOdkSession() {
            odkSession = null
            activeOdkCallTracking.clear()
            odkSessionContext?.clearOdkSessionState()
            odkSessionContext = null
            OdkIntentStateHolder.clear()
        }

        fun updateOdkSessionState(isActive: Boolean = false) {
            odkSession = odkSession?.copy(isActive = isActive)
            odkSessionContext?.let { context ->
                context.saveOdkSession(odkSession!!)
            }
        }

        fun checkOdkSessionTimeout(): Boolean {
            val session = odkSession ?: return false
            val currentTime = System.currentTimeMillis()
            val timeSinceStart = currentTime - session.sessionStartTime

            if (timeSinceStart > ODK_SESSION_TIMEOUT_MS) {
                odkSession = session.timeoutSession()
                odkSessionContext?.let { context ->
                    context.saveOdkSession(odkSession!!)
                }
                return true
            } else {
                return false
            }
        }

        // Detect call outcome based on state transition
        private fun detectCallOutcome(call: Call, oldState: Int, newState: Int): CallOutcome {
            return when (newState) {
                Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> {
                    when {
                        // Call was active when disconnected - answered
                        oldState == Call.STATE_ACTIVE || oldState == Call.STATE_HOLDING -> CallOutcome.ANSWERED
                        // Call was ringing when disconnected - no answer
                        oldState == Call.STATE_RINGING -> CallOutcome.NO_ANSWER
                        // Call was in dialing/connecting when disconnected - failed
                        oldState == Call.STATE_DIALING || oldState == Call.STATE_CONNECTING -> CallOutcome.FAILED
                        // Call was held when disconnected - still considered answered
                        oldState == Call.STATE_HOLDING -> CallOutcome.ANSWERED
                        else -> CallOutcome.FAILED
                    }
                }
                Call.STATE_ACTIVE -> {
                    // Call became active - answered
                    CallOutcome.ANSWERED
                }
                Call.STATE_HOLDING -> {
                    // Call was put on hold - answered (since it was connected)
                    CallOutcome.ANSWERED
                }
                else -> CallOutcome.FAILED
            }
        }

        // Calculate call timing using the most precise timestamps available
        private fun calculateCallTiming(
            call: Call,
            stateHistory: CallStateHistory,
            endTimeMs: Long
        ): CallTiming {
            val tracked = activeOdkCallTracking.values.find { it.call == call }
            val details = call.details

            val detailConnectTime = details.connectTimeMillis.takeIf { it > 0 }
            val detailCreationTime = details.creationTimeMillis.takeIf { it > 0 }

            val connectTime = detailConnectTime
                ?: stateHistory.connectTimeMillis
                ?: tracked?.connectTime

            val startTime = connectTime
                ?: stateHistory.startTimeMillis
                ?: tracked?.startTime
                ?: detailCreationTime
                ?: endTimeMs

            val durationMs = (endTimeMs - startTime).coerceAtLeast(0)
            val durationSeconds = durationMs / 1000.0

            return CallTiming(durationSeconds, startTime, connectTime)
        }

        // Get failure reason for failed calls
        private fun getCallFailureReason(oldState: Int, newState: Int): String? {
            return when (newState) {
                Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> when (oldState) {
                    Call.STATE_RINGING -> "Call not answered"
                    Call.STATE_DIALING, Call.STATE_CONNECTING -> "Call cancelled before connecting"
                    Call.STATE_ACTIVE, Call.STATE_HOLDING -> "Call ended while active"
                    else -> "Call ended from state ${callStateToString(oldState)}"
                }
                else -> "Call ended from state ${callStateToString(oldState)}"
            }
        }

        private fun describeDisconnectCause(call: Call): String? {
            val cause = call.details.disconnectCause ?: return null
            val explicit = cause.description?.takeIf { it.isNotBlank() }?.toString()
                ?: cause.label?.takeIf { it.isNotBlank() }?.toString()
            if (!explicit.isNullOrBlank()) return explicit

            return when (cause.code) {
                DisconnectCause.LOCAL -> "Local hangup"
                DisconnectCause.REMOTE -> "Remote hangup"
                DisconnectCause.BUSY -> "Line busy"
                DisconnectCause.MISSED -> "Call missed"
                DisconnectCause.REJECTED -> "Call rejected"
                DisconnectCause.ERROR -> "Call error"
                DisconnectCause.CANCELED -> "Call cancelled"
                else -> cause.reason?.takeIf { it.isNotBlank() }
            }
        }

        private fun formatSeconds(seconds: Double): String {
            return String.format(Locale.US, "%.1fs", seconds)
        }

        private fun buildOutcomeDetail(
            call: Call,
            outcome: CallOutcome,
            durationSeconds: Double,
            startTimeMs: Long,
            endTimeMs: Long,
            oldState: Int,
            newState: Int,
            connectTimeMs: Long?
        ): String {
            val segments = mutableListOf<String>()
            val direction = if (call.isOutgoing()) "outgoing call" else "incoming call"
            segments.add(direction)

            when (outcome) {
                CallOutcome.ANSWERED -> {
                    segments.add("ended normally")
                    if (durationSeconds > 0) {
                        segments.add("duration ${formatSeconds(durationSeconds)}")
                    }
                }
                CallOutcome.NO_ANSWER -> {
                    segments.add("no answer")
                    val ringSeconds = ((connectTimeMs ?: endTimeMs) - startTimeMs).coerceAtLeast(0) / 1000.0
                    if (ringSeconds > 0) {
                        segments.add("rang ${formatSeconds(ringSeconds)}")
                    }
                }
                CallOutcome.BUSY -> {
                    segments.add("line busy")
                }
                CallOutcome.FAILED -> {
                    getCallFailureReason(oldState, newState)?.let { segments.add(it) }
                }
                CallOutcome.REJECTED -> {
                    segments.add("call rejected")
                }
            }

            describeDisconnectCause(call)?.let { segments.add("disconnect: $it") }
            segments.add("states ${callStateToString(oldState)} -> ${callStateToString(newState)}")

            return segments.joinToString(" | ")
        }

        // Notify listeners about call outcome
        private fun notifyCallOutcomeEvents(
            call: Call,
            outcome: CallOutcome,
            outcomeDetail: String?,
            durationSeconds: Double,
            startTimeMs: Long,
            endTimeMs: Long
        ) {
            val number = getPhoneNumber(call)

            android.util.Log.d("CallManager", "Call outcome detected: $outcome, number: $number, duration: ${durationSeconds}s")

            for (listener in CallManager.listeners) {
                listener.onCallOutcomeDetected(call, outcome, outcomeDetail, durationSeconds, startTimeMs, endTimeMs)
            }
        }
    }
}

interface CallManagerListener {
    fun onStateChanged()
    fun onAudioStateChanged(audioState: AudioRoute)
    fun onPrimaryCallChanged(call: Call)

    // ODK integration methods
    fun onCallStarted(number: String, isOutgoing: Boolean)
    fun onCallActive(number: String, isOutgoing: Boolean)  // New method for when call becomes active/connected
    fun onCallEnded()

    // Enhanced methods that pass Call objects for reliable tracking
    fun onCallStarted(call: Call, number: String, isOutgoing: Boolean)
    fun onCallActive(call: Call, number: String, isOutgoing: Boolean)

    // Call logging methods
    fun onCallOutcomeDetected(
        call: Call,
        outcome: CallOutcome,
        outcomeDetail: String?,
        durationSeconds: Double,
        startTimeMs: Long,
        endTimeMs: Long
    )
}

// Helper function to extract phone number from call details
private fun getPhoneNumber(call: Call): String {
    return cleanPhoneNumber(call.details.handle?.toString() ?: "Unknown")
}

// Helper function to clean phone numbers by removing tel: prefix
private fun cleanPhoneNumber(number: String): String {
    return number.replace("tel:", "")
}

// Helper function to determine if a call is outgoing
private fun isCallOutgoing(call: Call): Boolean {
    val callState = call.getStateCompat()
    // More reliable: check handle direction and call state
    val handle = call.details.handle?.toString() ?: ""

    // Outgoing calls typically start in DIALING/CONNECTING state
    val isDialingOrConnecting = callState == Call.STATE_DIALING || callState == Call.STATE_CONNECTING

    // Use the reliable method first (state-based)
    return isDialingOrConnecting
}

// Helper function to find call ID by phone number
private fun findCallIdByNumber(phoneNumber: String): String? {
    return CallManager.activeOdkCallTracking.entries.find { it.value.number == phoneNumber }?.key
}

sealed class PhoneState
object NoCall : PhoneState()
class SingleCall(val call: Call) : PhoneState()
class TwoCalls(val active: Call, val onHold: Call) : PhoneState()
