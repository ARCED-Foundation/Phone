package org.fossify.phone.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import org.fossify.phone.extensions.getStateCompat
import org.fossify.phone.extensions.hasCapability
import org.fossify.phone.extensions.isConference
import org.fossify.phone.extensions.formatCurrentTimeIso8601
import org.fossify.phone.extensions.CALL_DATA_SEPARATOR
import org.fossify.phone.extensions.CALL_DIRECTION_OUTGOING
import org.fossify.phone.extensions.CALL_DIRECTION_INCOMING
import org.fossify.phone.models.AudioRoute
import org.fossify.phone.models.OdkCallTrackingInfo
import org.fossify.phone.models.OdkCallRecord
import org.fossify.phone.models.ODKSession
import org.fossify.phone.models.CallDirection
import org.fossify.phone.models.SessionStatus
import org.fossify.phone.extensions.saveOdkSession
import org.fossify.phone.extensions.clearOdkSessionState
import org.fossify.phone.extensions.ODK_SESSION_TIMEOUT_MS
import org.fossify.phone.extensions.concatenateCallValues
import android.util.Base64
import kotlinx.serialization.json.Json
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet

// inspired by https://github.com/Chooloo/call_manage
class CallManager {
    companion object {
        @SuppressLint("StaticFieldLeak")
        var inCallService: InCallService? = null
        private var call: Call? = null
        private val calls = mutableListOf<Call>()
        private val listeners = CopyOnWriteArraySet<CallManagerListener>()

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
            var isInStateUpdate: Boolean = false  // Prevent concurrent notifications
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

        // Handle individual call state changes with proper lifecycle tracking
        private fun handleCallStateChange(call: Call, newState: Int) {
            val stateHistory = callStateHistory[call]
            if (stateHistory == null) {
                // Initialize state history if missing
                callStateHistory[call] = CallStateHistory(previousState = newState)
                return
            }

            val oldState = stateHistory.previousState

            // Only process meaningful transitions
            if (oldState != newState) {
                android.util.Log.d("CallManager", "Call state transition: ${callStateToString(oldState)} -> ${callStateToString(newState)}")

                // Handle call start transitions (IDLE -> DIALING/CONNECTING/ACTIVE)
                if (isStartTransition(oldState, newState)) {
                    if (!stateHistory.hasNotifiedStart) {
                        notifyCallStartedEvents(call)
                        stateHistory.hasNotifiedStart = true
                    }
                }

                // Handle call active transition (DIALING/CONNECTING -> ACTIVE)
                if (isCallActiveTransition(oldState, newState)) {
                    notifyCallActiveEvents(call)
                }

                // Handle call end transitions (ACTIVE/DIALING/CONNECTING -> DISCONNECTED/DISCONNECTING)
                if (isEndTransition(oldState, newState)) {
                    if (!stateHistory.hasNotifiedEnd) {
                        notifyCallEndedEvents()
                        stateHistory.hasNotifiedEnd = true
                    }
                }
            }

            // Always update the previous state
            stateHistory.previousState = newState
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
                    oldState == Call.STATE_CONNECTING) &&
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
            val isOutgoing = isCallOutgoing(call)
            android.util.Log.d("CallManager", "Call became active: $number, isOutgoing: $isOutgoing, state: ${callStateToString(call.getStateCompat())}")

            // Update ODK call tracking with connection time
            if (isOdkSessionActive()) {
                try {
                    // Find the active ODK call for this number and update connection time
                    activeOdkCallTracking.values.find { it.number == number }?.let { trackingInfo ->
                        val callId = findCallIdByNumber(number)
                        if (callId != null) {
                            updateOdkCallConnection(callId, System.currentTimeMillis())
                            android.util.Log.d("CallManager", "ODK call connected for $number at ${System.currentTimeMillis()}")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("CallManager", "Failed to update ODK call connection: ${e.message}")
                }
            }

            for (listener in listeners) {
                // Use only enhanced method for reliable tracking
                listener.onCallActive(call, number, isOutgoing)
            }
        }

        private fun notifyCallEndedEvents() {
            android.util.Log.d("CallManager", "Call ended event triggered for all calls")

            // Complete ODK call tracking for all active calls
            if (isOdkSessionActive()) {
                try {
                    val currentTime = System.currentTimeMillis()
                    val completedRecords = mutableListOf<OdkCallRecord>()

                    // Complete all active ODK calls
                    activeOdkCallTracking.entries.forEach { (callId, trackingInfo) ->
                        val duration = if (trackingInfo.connectTime != null) {
                            (currentTime - trackingInfo.connectTime) / 1000.0
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
                } catch (e: Exception) {
                    android.util.Log.e("CallManager", "Failed to complete ODK calls: ${e.message}")
                }
            }

            for (listener in listeners) {
                listener.onCallEnded()
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
        fun initializeOdkSession(context: Context, phoneNumber: String?, existingValue: String?, variant: String?) {
    // Clear any existing session data to prevent overwrite
    odkSession?.callRecords?.let { _ -> odkSession = odkSession?.copy(callRecords = emptyList()) }
    activeOdkCallTracking.clear()

            odkSessionContext = context.applicationContext
            odkSession = ODKSession(
                phoneNumber = phoneNumber,
                existingValue = existingValue,
                sessionStartTime = System.currentTimeMillis(),
                sessionVariant = variant,
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
            return completedSession
        }

        fun getActiveOdkCalls(): Map<String, OdkCallTrackingInfo> = activeOdkCallTracking

        fun addActiveOdkCall(callId: String, call: Call, phoneNumber: String, direction: CallDirection): OdkCallTrackingInfo {
            val trackingInfo = OdkCallTrackingInfo(
                call = call,
                startTime = System.currentTimeMillis(),
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

        fun getOdkCallDataForConcatenation(): String {
            val callRecords = odkSession?.callRecords ?: emptyList()
            val callData = callRecords.joinToString(CALL_DATA_SEPARATOR) { record ->
                formatCallDataForConcatenation(
                    phoneNumber = record.phoneNumber,
                    direction = if (record.direction == CallDirection.OUTGOING) CALL_DIRECTION_OUTGOING else CALL_DIRECTION_INCOMING,
                    duration = record.duration,
                    timestamp = record.startTime
                )
            }
            return callData
        }

        fun getConcatenatedOdkValue(): String {
            val session = odkSession ?: return ""
            if (session.callRecords.isEmpty()) {
                return session.existingValue ?: ""
            }
            val existingValue = session.existingValue ?: ""
            val newCallData = getOdkCallDataForConcatenation()
            return concatenateCallValues(existingValue, newCallData)
        }

        fun resetOdkSession() {
            odkSession = null
            activeOdkCallTracking.clear()
            odkSessionContext?.clearOdkSessionState()
            odkSessionContext = null
        }

        fun updateOdkSessionState(isActive: Boolean = false) {
            odkSession = odkSession?.copy(isActive = isActive)
            odkSessionContext?.let { context ->
                context.saveOdkSession(odkSession!!)
            }
        }

        private fun formatCallDataForConcatenation(
            phoneNumber: String,
            direction: String,
            duration: Double,
            timestamp: Long
        ): String {
            val formattedTimestamp = formatCurrentTimeIso8601().replace("Z", ".000Z") // Ensure ISO format
            return when (direction) {
                CALL_DIRECTION_INCOMING -> String.format(
                    Locale.US,
                    "In: %s; Duration: %.2fs; Started: %s",
                    phoneNumber,
                    duration,
                    formattedTimestamp
                )
                else -> String.format(
                    Locale.US,
                    "Out: %s; Duration: %.2fs; Started: %s",
                    phoneNumber,
                    duration,
                    formattedTimestamp
                )
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
