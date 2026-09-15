package com.ashudialer.app.telecom

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import com.ashudialer.app.AshuDialerApp
import com.ashudialer.app.data.db.CallDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class PixelInCallService : InCallService() {

    companion object {
        private const val TAG = "PixelInCallService"

        private val _allCalls = mutableListOf<Call>()
        val allCalls: List<Call> get() = _allCalls.toList()


        var instance: PixelInCallService? = null
            private set

        /**
         * The real, Telecom-confirmed current audio route - not an optimistic
         * guess read immediately after requesting a route change. Telecom's
         * setAudioRoute() is a request, not an instant switch (it can take a
         * noticeable moment, more on some OEMs and with Bluetooth); reading
         * AudioRouteController.currentRoute() right after calling it was
         * capturing the stale pre-switch value, which is why the speaker
         * button used to sometimes show the wrong state even though the
         * actual audio route had genuinely changed. onCallAudioStateChanged
         * below is Telecom's own authoritative callback for exactly this -
         * it fires once the platform has actually completed the switch, so
         * this StateFlow always reflects reality rather than a guess.
         */
        private val _currentAudioRoute = kotlinx.coroutines.flow.MutableStateFlow(AudioRoute.EARPIECE)
        val currentAudioRouteFlow: kotlinx.coroutines.flow.StateFlow<AudioRoute> = _currentAudioRoute

        private val _availableAudioRoutes = kotlinx.coroutines.flow.MutableStateFlow<List<AudioRoute>>(listOf(AudioRoute.EARPIECE, AudioRoute.SPEAKER))
        val availableAudioRoutesFlow: kotlinx.coroutines.flow.StateFlow<List<AudioRoute>> = _availableAudioRoutes

        /**
         * Same fix as _currentAudioRoute, applied to mute. Mute used to be
         * driven by directly toggling AudioManager.isMicrophoneMute and
         * immediately reading it back - a completely separate, synchronous
         * path from the audio route's async Telecom-callback path. That
         * mismatch in timing is what made the two buttons visibly fall out
         * of sync with each other (mute would flip instantly while speaker
         * was still mid-transition, or vice versa on slower OEM Telecom
         * stacks). CallAudioState - the same object onCallAudioStateChanged
         * already receives for route - also carries isMuted, so both
         * buttons now update from the exact same callback, at the exact
         * same time, every time.
         */
        private val _isMuted = kotlinx.coroutines.flow.MutableStateFlow(false)
        val isMutedFlow: kotlinx.coroutines.flow.StateFlow<Boolean> = _isMuted


        /**
         * Pick the call that should be represented by the foreground in-call UI.
         *
         * The old order was RINGING -> ACTIVE -> first call. That is wrong for
         * an outgoing second call: the original call remains ACTIVE while the
         * new call is DIALING/CONNECTING, so the UI kept showing the old caller
         * and never showed the new call's Calling/Dialing state.
         *
         * Keep an actually ringing incoming call highest priority, then an
         * outgoing DIALING/CONNECTING call, then ACTIVE, then HOLDING. This
         * mirrors what a normal dialer presents while keeping the other call
         * available as secondary/held context.
         */
        val currentCall: Call?
            get() = _allCalls.firstOrNull { it.state == Call.STATE_RINGING }
                ?: _allCalls.firstOrNull { it.state == Call.STATE_DIALING }
                ?: _allCalls.firstOrNull { it.state == Call.STATE_CONNECTING }
                ?: _allCalls.firstOrNull { it.state == Call.STATE_ACTIVE }
                ?: _allCalls.firstOrNull { it.state == Call.STATE_HOLDING }
                ?: _allCalls.firstOrNull()

        val secondaryCall: Call?
            get() = _allCalls.firstOrNull { it != currentCall && it.state != Call.STATE_DISCONNECTED }

        val hasMultipleCalls: Boolean
            get() = _allCalls.count { it.state != Call.STATE_DISCONNECTED } > 1

        /** True only when Telecom currently exposes the pair as conferenceable. */
        fun canMergeCalls(): Boolean {
            val primary = currentCall ?: return false
            val secondary = secondaryCall ?: return false
            return try {
                primary.state == Call.STATE_ACTIVE &&
                    (secondary.state == Call.STATE_HOLDING || secondary.state == Call.STATE_ACTIVE) &&
                    (primary.conferenceableCalls.contains(secondary) ||
                        secondary.conferenceableCalls.contains(primary))
            } catch (_: Throwable) {
                false
            }
        }

        /** True for the standard ACTIVE + HOLDING two-call swap situation. */
        fun canSwapCalls(): Boolean {
            val primary = currentCall ?: return false
            val secondary = secondaryCall ?: return false
            return (primary.state == Call.STATE_ACTIVE && secondary.state == Call.STATE_HOLDING) ||
                (primary.state == Call.STATE_HOLDING && secondary.state == Call.STATE_ACTIVE)
        }

        private val listeners = mutableListOf<() -> Unit>()

        fun addCallListener(listener: () -> Unit) {
            listeners.add(listener)
            listener()
        }

        fun removeCallListener(listener: () -> Unit) {
            listeners.remove(listener)
        }

        private fun notifyListeners() {
            listeners.forEach { it() }
        }


        private val loggedAsMissed = mutableSetOf<String>()
        private val loggedAsAnswered = mutableSetOf<String>()
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO)


    private var latestSettings: com.ashudialer.app.data.AppSettings = com.ashudialer.app.data.AppSettings()

    override fun onCreate() {
        super.onCreate()
        instance = this
        val app = applicationContext as? AshuDialerApp
        if (app != null) {
            serviceScope.launch {
                app.appSettingsRepository.settingsFlow.collect { latestSettings = it }
            }
        }
    }

    override fun onDestroy() {
        // Do not let Call instances from a previous InCallService lifetime
        // survive a Telecom/OEM service restart. A stale Call object can make
        // currentCall point at a disconnected call and the next UI launch can
        // immediately close or show the wrong caller.
        _allCalls.toList().forEach { call ->
            runCatching { call.unregisterCallback(callCallback) }
        }
        _allCalls.clear()
        _currentAudioRoute.value = AudioRoute.EARPIECE
        _availableAudioRoutes.value = listOf(AudioRoute.EARPIECE, AudioRoute.SPEAKER)
        _isMuted.value = false
        resolvedContactNames.clear()
        loggedAsMissed.clear()
        loggedAsAnswered.clear()
        notifyListeners()
        super.onDestroy()
        if (instance === this) instance = null
    }

    /**
     * Telecom's authoritative callback - fires once the platform has
     * genuinely finished switching the audio route (or updated which
     * routes are available, e.g. a Bluetooth headset connecting/
     * disconnecting mid-call). Publishing this into the StateFlows above
     * is what makes the in-call UI's speaker/audio icon always match
     * reality, instead of the previous optimistic-read-right-after-request
     * approach that could show a stale icon.
     */
    override fun onCallAudioStateChanged(audioState: android.telecom.CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        try {
            _currentAudioRoute.value = when (audioState.route) {
                android.telecom.CallAudioState.ROUTE_SPEAKER -> AudioRoute.SPEAKER
                android.telecom.CallAudioState.ROUTE_BLUETOOTH -> AudioRoute.BLUETOOTH
                android.telecom.CallAudioState.ROUTE_WIRED_HEADSET -> AudioRoute.WIRED_HEADSET
                else -> AudioRoute.EARPIECE
            }
            val supported = mutableListOf<AudioRoute>()
            val mask = audioState.supportedRouteMask
            if (mask and android.telecom.CallAudioState.ROUTE_EARPIECE != 0) supported.add(AudioRoute.EARPIECE)
            if (mask and android.telecom.CallAudioState.ROUTE_SPEAKER != 0) supported.add(AudioRoute.SPEAKER)
            if (mask and android.telecom.CallAudioState.ROUTE_BLUETOOTH != 0) supported.add(AudioRoute.BLUETOOTH)
            if (mask and android.telecom.CallAudioState.ROUTE_WIRED_HEADSET != 0) supported.add(AudioRoute.WIRED_HEADSET)
            if (supported.isNotEmpty()) _availableAudioRoutes.value = supported
            _isMuted.value = audioState.isMuted
        } catch (e: Exception) {
            Log.w(TAG, "Failed to process audio state change", e)
        }
    }

    /**
     * Requests mute through Telecom's own setMuted(), same as setAudioRoute()
     * requests a route - a request, not an instant local write. The real
     * state always comes back through onCallAudioStateChanged above, kept
     * in lockstep with the route so both buttons settle together.
     */
    fun requestMuted(muted: Boolean) {
        try {
            setMuted(muted)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request mute state", e)
        }
    }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            Log.d(TAG, "Call state changed: $state")
            notifyListeners()

            try {
                handleStateForNotification(call, state)
            } catch (e: Exception) {
                Log.e(TAG, "Notification handling failed on state change", e)
            }


            if (state == Call.STATE_ACTIVE) {
                callKey(call)?.let { loggedAsAnswered.add(it) }
            }

            if (state == Call.STATE_DISCONNECTED) {
                handleMissedCallLogging(call)
                call.unregisterCallback(this)
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "Call added: ${call.details.handle}, state=${call.state}, totalCalls=${_allCalls.size + 1}")
        _allCalls.add(call)
        call.registerCallback(callCallback)
        notifyListeners()

        try {
            handleStateForNotification(call, call.state)
        } catch (e: Exception) {
            Log.e(TAG, "Notification handling failed for new call", e)
        }

        val isIncoming = call.details?.callDirection == Call.Details.DIRECTION_INCOMING
        val number = call.details?.handle?.schemeSpecificPart ?: "Unknown"

        if (isIncoming) {
            vibrateForIncomingCall(number)
            try {
                val carrierName = call.details?.callerDisplayName?.takeIf { it.isNotBlank() }
                val displayName = resolvedContactNames[number] ?: carrierName ?: number
                CallNotificationHelper.showIncomingCallNotification(
                    applicationContext, displayName, number, latestSettings.ledFlashForAlerts
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to prepare incoming-call fullscreen notification", e)
            }
        }
        launchInCallUi(call)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d(TAG, "Call removed, remaining=${_allCalls.size - 1}")
        call.unregisterCallback(callCallback)
        _allCalls.remove(call)
        notifyListeners()
        if (_allCalls.isEmpty()) {
            CallNotificationHelper.clear(applicationContext)
        }
        callKey(call)?.let {
            loggedAsMissed.remove(it)
            loggedAsAnswered.remove(it)
        }
    }

    private val resolvedContactNames = mutableMapOf<String, String?>()

    private fun handleStateForNotification(call: Call, state: Int) {
        val number = call.details?.handle?.schemeSpecificPart ?: "Unknown"
        val carrierName = call.details?.callerDisplayName?.takeIf { it.isNotBlank() }
        val savedName = resolvedContactNames[number]
        val name = savedName ?: carrierName ?: number
        val isIncoming = call.details?.callDirection == Call.Details.DIRECTION_INCOMING

        if (number != "Unknown" && !resolvedContactNames.containsKey(number)) {
            resolvedContactNames[number] = null // mark in-flight so we don't re-query while waiting
            val app = applicationContext as? AshuDialerApp
            if (app != null) {
                serviceScope.launch {
                    val match = app.contactsRepository.lookupNameForNumber(number)?.displayName
                    resolvedContactNames[number] = match
                    if (match != null) {
                        handleStateForNotification(call, call.state)
                    }
                }
            }
        }

        when (state) {
            Call.STATE_RINGING -> {
                CallNotificationHelper.showIncomingCallNotification(applicationContext, name, number, latestSettings.ledFlashForAlerts)
            }
            Call.STATE_DIALING, Call.STATE_CONNECTING -> {


                if (!isIncoming) {
                    CallNotificationHelper.showOngoingCallNotification(applicationContext, name)
                }
            }
            Call.STATE_ACTIVE, Call.STATE_HOLDING -> {
                CallNotificationHelper.showOngoingCallNotification(applicationContext, name)
            }
            Call.STATE_DISCONNECTED -> {
                if (_allCalls.size <= 1) {
                    if (latestSettings.keepCallsInNotifications) {
                        CallNotificationHelper.showCallEndedNotification(applicationContext, name)
                    } else {
                        CallNotificationHelper.clear(applicationContext)
                    }
                }
            }
        }
    }


    private fun handleMissedCallLogging(call: Call) {
        val key = callKey(call) ?: return
        if (key in loggedAsAnswered) return
        if (key in loggedAsMissed) return

        val isIncoming = call.details?.callDirection == Call.Details.DIRECTION_INCOMING
        if (!isIncoming) return

        loggedAsMissed.add(key)

        val number = call.details?.handle?.schemeSpecificPart ?: "Unknown"
        val carrierName = call.details?.callerDisplayName?.takeIf { it.isNotBlank() }

        val app = applicationContext as? AshuDialerApp ?: return
        serviceScope.launch {
            try {
                // Use the exact same identity resolution as the live incoming
                // screen. This prevents a missed-call notification from
                // showing a stale/carrier label while the call screen showed
                // the saved contact name.
                val contact = if (number != "Unknown") {
                    app.contactsRepository.lookupNameForNumber(number)
                } else null
                val displayName = contact?.displayName ?: carrierName ?: number

                app.callLogRepository.logCall(
                    number = number,
                    name = displayName,
                    direction = CallDirection.MISSED
                )

                CallNotificationHelper.showMissedCallNotification(
                    applicationContext,
                    callerName = displayName,
                    callerNumber = number
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log missed call", e)
                // Even if the local contact provider is unavailable, still
                // deliver a useful missed-call notification with the network
                // supplied caller label/number.
                CallNotificationHelper.showMissedCallNotification(
                    applicationContext,
                    callerName = carrierName ?: number,
                    callerNumber = number
                )
            }
        }
    }

    private fun callKey(call: Call): String? {
        val number = call.details?.handle?.schemeSpecificPart ?: return null


        return "$number:${call.details?.creationTimeMillis ?: 0}"
    }

    private fun vibrateForIncomingCall(number: String?) {
        serviceScope.launch {
            val patternId = number?.let {
                try {
                    (application as AshuDialerApp).database.vibrationRuleDao().getPatternId(it)
                } catch (e: Exception) {
                    Log.w(TAG, "Vibration pattern lookup failed", e)
                    null
                }
            }
            val pattern = VibrationPattern.fromId(patternId)
            playVibrationPattern(pattern)
        }
    }

    private fun playVibrationPattern(pattern: VibrationPattern) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern.timings, -1))
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed", e)
        }
    }

    private fun launchInCallUi(call: Call? = currentCall) {
        val number = call?.details?.handle?.schemeSpecificPart ?: "Unknown"
        val carrierName = call?.details?.callerDisplayName?.takeIf { it.isNotBlank() }
        val name = resolvedContactNames[number] ?: carrierName ?: number
        val isIncoming = call?.details?.callDirection == Call.Details.DIRECTION_INCOMING

        if (isIncoming) {
            // InCallService does not expose a public bringToForeground()
            // method. The incoming-call notification/full-screen intent and
            // Activity fallback below are the supported ways to surface UI.
            val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
            val screenInteractive = powerManager?.isInteractive != false
            val deviceLocked = keyguardManager?.isKeyguardLocked == true

            // The notification remains the required lock-screen fallback.
            // If Android 14+ full-screen access or notifications are disabled,
            // fall back to the actual call Activity; showWhenLocked/turnScreenOn
            // on that Activity makes it visible without trying to dismiss a
            // secure PIN/pattern/password lock.
            val notificationsEnabled = androidx.core.app.NotificationManagerCompat
                .from(applicationContext).areNotificationsEnabled()
            val fullScreenAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                runCatching {
                    getSystemService(android.app.NotificationManager::class.java)
                        ?.canUseFullScreenIntent() == true
                }.getOrDefault(false)
            } else {
                true
            }

            if (!notificationsEnabled || !fullScreenAllowed || !screenInteractive || deviceLocked) {
                val intent = Intent(this, InCallActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_USER_ACTION
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.w(TAG, "Incoming call Activity fallback failed", e)
                }
            }
            return
        }

        val intent = Intent(this, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_NO_USER_ACTION
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Direct startActivity failed, falling back to call notification", e)
            try {
                CallNotificationHelper.showOngoingCallNotification(applicationContext, name)
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Call notification fallback also failed", fallbackError)
            }
        }
    }


    fun answer() {
        runCatching { currentCall?.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY) }
            .onFailure { Log.w(TAG, "Answer request failed", it) }
    }

    fun reject() {
        runCatching { currentCall?.reject(false, null) }
            .onFailure { Log.w(TAG, "Reject request failed", it) }
    }

    fun hangup() {
        runCatching { currentCall?.disconnect() }
            .onFailure { Log.w(TAG, "Hangup request failed", it) }
    }

    fun toggleHold() {
        currentCall?.let {
            try {
                val capabilities = it.details?.callCapabilities ?: 0
                val canHold = capabilities and Call.Details.CAPABILITY_HOLD != 0
                if (!canHold) return
                when (it.state) {
                    Call.STATE_HOLDING -> it.unhold()
                    Call.STATE_ACTIVE -> it.hold()
                    else -> Unit
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Unable to change hold state", e)
            }
        }
    }


    fun swapCalls() {
        val primary = currentCall ?: return
        val secondary = secondaryCall ?: return
        try {
            when {
                primary.state == Call.STATE_ACTIVE && secondary.state == Call.STATE_HOLDING -> {
                    primary.hold()
                    secondary.unhold()
                }
                primary.state == Call.STATE_HOLDING && secondary.state == Call.STATE_ACTIVE -> {
                    primary.unhold()
                    secondary.hold()
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Unable to swap calls", e)
        }
    }


    fun mergeCalls() {
        val primary = currentCall ?: return
        val secondary = secondaryCall ?: return
        if (!canMergeCalls()) return
        try {
            primary.conference(secondary)
        } catch (e: Throwable) {
            // Some carrier/OEM phone accounts advertise two calls but reject
            // conference() until both calls have settled into ACTIVE. The UI
            // already gates the action with conferenceableCalls; this catch
            // prevents a carrier-specific TelecomException from crashing the
            // in-call screen.
            Log.w(TAG, "Conference request rejected by Telecom", e)
        }
    }
}
