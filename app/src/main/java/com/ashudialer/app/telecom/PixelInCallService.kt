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

        /** Latest Telecom-confirmed audio state for notification/actions. */
        @Volatile
        var callAudioState: android.telecom.CallAudioState? = null

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
         * True once this call has genuinely become a bidirectional native
         * (carrier ViLTE/VoLTE) video call - i.e. Telecom's own
         * VideoCall.Callback has told this app, one way or another, that
         * video is actually flowing, not merely that a request was sent.
         * Two separate call sites set this true, matching the two ways a
         * call can end up in bidirectional video (see onVideoCallChanged's
         * own doc comment below for the same split): onSessionModifyRequestReceived
         * when the OTHER party requested the upgrade and this app just
         * auto-accepted it, and onSessionModifyResponseReceived when THIS
         * device's own outgoing request (sent from InCallActivity's
         * onUpgradeToNativeVideo) comes back approved. InCallActivity
         * collects this the same way it already collects isMutedFlow/
         * currentAudioRouteFlow above, and uses it - together with the
         * Call.videoCall reference InCallActivity already holds directly -
         * to decide when to swap CallScreen's normal audio UI for
         * NativeVideoCallScreen's live TextureView surfaces. This flag
         * only ever reflects "is native video active right now"; it is not
         * a request/pending state, so a rejected or not-yet-answered
         * upgrade request correctly leaves it false rather than needing a
         * separate branch to unset a "pending" value.
         */
        private val _nativeVideoUpgradeActive = kotlinx.coroutines.flow.MutableStateFlow(false)
        val nativeVideoUpgradeActiveFlow: kotlinx.coroutines.flow.StateFlow<Boolean> = _nativeVideoUpgradeActive


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
        PixelInCallService.callAudioState = null
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
        _nativeVideoUpgradeActive.value = false
        PixelInCallService.callAudioState = null
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
            PixelInCallService.callAudioState = audioState
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
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (currentCall != null) {
                    runCatching { CallNotificationHelper.refreshOngoing(applicationContext) }
                }
            }
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

        // DEEP FIX (native/VoLTE video calling - the incoming-upgrade
        // half of the feature; the outgoing half is the
        // sendSessionModifyRequest wiring in InCallActivity's
        // onUpgradeToNativeVideo): Call.videoCall only actually becomes
        // non-null once Telecom/the carrier attaches a VideoProvider to
        // this call - which can happen either because this device itself
        // requested video (answered as STATE_BIDIRECTIONAL, or sent its
        // own upgrade request) or because the OTHER party requested an
        // upgrade mid-call. Registering a VideoCall.Callback here, the
        // moment videoCall appears, is what lets this app actually
        // respond to that second case - a request arriving from the other
        // side - rather than silently ignoring it, which would leave the
        // other person's upgrade attempt hanging with no response at all
        // until it times out on their end.
        override fun onVideoCallChanged(call: Call, videoCall: android.telecom.InCallService.VideoCall?) {
            super.onVideoCallChanged(call, videoCall)
            videoCall?.registerCallback(object : android.telecom.InCallService.VideoCall.Callback() {
                override fun onSessionModifyRequestReceived(requestProfile: android.telecom.VideoProfile) {
                    // Auto-accepts a video upgrade the other party
                    // requested, the same way this app already answers a
                    // call that arrives already requesting video (see
                    // answerVideoStateFor's doc comment in InCallActivity)
                    // - both are "the network/other party is offering
                    // video, let it through" rather than a decision this
                    // app second-guesses. Responding at all (rather than
                    // never calling sendSessionModifyResponse) is required
                    // by the platform - the requester's side is left
                    // waiting until this is called, one way or another.
                    val accepted = runCatching {
                        videoCall.sendSessionModifyResponse(
                            android.telecom.VideoProfile(android.telecom.VideoProfile.STATE_BIDIRECTIONAL)
                        )
                    }.isSuccess
                    // Flips nativeVideoUpgradeActiveFlow the moment this
                    // app agrees to the upgrade, not on some later,
                    // separate confirmation - there is no further
                    // "did the other side see my response" callback to
                    // wait for on this incoming-request path (compare
                    // onSessionModifyResponseReceived below, which DOES
                    // wait for a real response, because there this device
                    // is the one that asked and genuinely doesn't know
                    // the answer yet).
                    if (accepted) {
                        _nativeVideoUpgradeActive.value = true
                    }
                }

                // DEEP FIX (native/VoLTE video calling, the missing half
                // called out in InCallActivity's onUpgradeToNativeVideo
                // doc comment: "rendering the two live video surfaces...
                // is a separate, larger UI surface of its own - not yet
                // built here"). This callback is Telecom's answer to
                // *this device's own* sendSessionModifyRequest call - it
                // fires once (accepted, rejected, or timed out/failed),
                // and is the only reliable signal that the upgrade this
                // app asked for actually went through, as opposed to the
                // request merely having been sent. Before this, the
                // callback body was empty, so tapping "Switch to video
                // call" would successfully ask the carrier to upgrade and
                // then never do anything with the answer - the call
                // would genuinely become bidirectional video at the
                // network level with no UI ever reflecting it.
                //
                // status is one of Connection.VideoProvider's SESSION_MODIFY_REQUEST_*
                // constants; only SESSION_MODIFY_REQUEST_SUCCESS means the
                // request was actually granted. Checking responseProfile's own videoState as
                // well (not just status) matches what the AOSP Dialer's
                // own VideoCallPresenter does - a carrier can in principle
                // report SUCCESS while still only granting a narrower
                // video state (e.g. one-way) than what was requested, and
                // isBidirectional on the actual granted profile is the
                // correct thing to gate a bidirectional-video UI on, not
                // the mere presence of a success status.
                override fun onSessionModifyResponseReceived(
                    status: Int,
                    requestedProfile: android.telecom.VideoProfile?,
                    responseProfile: android.telecom.VideoProfile?
                ) {
                    val granted = status == android.telecom.Connection.VideoProvider.SESSION_MODIFY_REQUEST_SUCCESS &&
                        responseProfile != null &&
                        android.telecom.VideoProfile.isBidirectional(responseProfile.videoState)
                    _nativeVideoUpgradeActive.value = granted
                }

                override fun onCallSessionEvent(event: Int) {}
                override fun onPeerDimensionsChanged(width: Int, height: Int) {}
                override fun onVideoQualityChanged(videoQuality: Int) {}
                override fun onCallDataUsageChanged(dataUsage: Long) {}
                override fun onCameraCapabilitiesChanged(cameraCapabilities: android.telecom.VideoProfile.CameraCapabilities?) {}
            })
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "Call added: ${call.details.handle}, state=${call.state}, totalCalls=${_allCalls.size + 1}")
        _allCalls.removeAll { existing -> existing === call || existing.state == Call.STATE_DISCONNECTED }
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
        if (_allCalls.none { it.state != Call.STATE_DISCONNECTED }) {
            PixelInCallService.callAudioState = null
            _currentAudioRoute.value = AudioRoute.EARPIECE
            _availableAudioRoutes.value = listOf(AudioRoute.EARPIECE, AudioRoute.SPEAKER)
            _isMuted.value = false
            _nativeVideoUpgradeActive.value = false
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


    // DEEP FIX for "native VoLTE video calling isn't supported" (part 1 of
    // 2 - the matching InCallActivity call sites cover the other two
    // paths): every answer() call in this app used to hardcode
    // VideoProfile.STATE_AUDIO_ONLY unconditionally, regardless of what
    // kind of call was actually incoming. Telecom/the carrier's own
    // ConnectionService reports whether an incoming call itself requested
    // video via Call.Details.getVideoState() - if a call genuinely arrives
    // already carrying STATE_BIDIRECTIONAL (a real VoLTE video call being
    // placed to this device), forcing STATE_AUDIO_ONLY on it here was
    // actively downgrading a call that was never audio-only to begin with,
    // not "supporting audio calls" - the carrier network handles that
    // downgrade decision on its own for calls that never requested video
    // in the first place, so this only ever changes behavior for a call
    // that already came in requesting video.
    //
    // VideoProfile.isAudioOnly(int) (not a plain == comparison -
    // STATE_AUDIO_ONLY is 0, so == misses any state with the paused bit
    // set) is the platform-documented correct way to check this.
    fun answer() {
        val videoState = currentCall?.details?.videoState ?: android.telecom.VideoProfile.STATE_AUDIO_ONLY
        val answerAsVideo = if (android.telecom.VideoProfile.isAudioOnly(videoState)) {
            android.telecom.VideoProfile.STATE_AUDIO_ONLY
        } else {
            android.telecom.VideoProfile.STATE_BIDIRECTIONAL
        }
        runCatching { currentCall?.answer(answerAsVideo) }
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
