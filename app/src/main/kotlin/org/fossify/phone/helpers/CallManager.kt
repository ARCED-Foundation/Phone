package org.fossify.phone.helpers

import android.annotation.SuppressLint
import android.os.Handler
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import org.fossify.phone.extensions.getStateCompat
import org.fossify.phone.extensions.hasCapability
import org.fossify.phone.extensions.isConference
import org.fossify.phone.models.AudioRoute
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

        data class CallStateHistory(
            var previousState: Int = Call.STATE_DISCONNECTED,
            var hasNotifiedStart: Boolean = false,
            var hasNotifiedEnd: Boolean = false
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
            android.util.Log.d("CallManager", "Call started: $number, isOutgoing: $isOutgoing, state: ${callStateToString(call.getStateCompat())}")
            for (listener in listeners) {
                listener.onCallStarted(number, isOutgoing)
                // Also pass the Call object for more reliable tracking
                listener.onCallStarted(call, number, isOutgoing)
            }
        }

        private fun notifyCallActiveEvents(call: Call) {
            val number = getPhoneNumber(call)
            val isOutgoing = isCallOutgoing(call)
            android.util.Log.d("CallManager", "Call became active: $number, isOutgoing: $isOutgoing, state: ${callStateToString(call.getStateCompat())}")
            for (listener in listeners) {
                listener.onCallActive(number, isOutgoing)
                // Also pass the Call object for more reliable tracking
                listener.onCallActive(call, number, isOutgoing)
            }
        }

        private fun notifyCallEndedEvents() {
            android.util.Log.d("CallManager", "Call ended event triggered for all calls")
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

sealed class PhoneState
object NoCall : PhoneState()
class SingleCall(val call: Call) : PhoneState()
class TwoCalls(val active: Call, val onHold: Call) : PhoneState()
