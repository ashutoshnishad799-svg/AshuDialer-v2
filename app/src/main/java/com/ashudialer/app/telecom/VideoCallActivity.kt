package com.ashudialer.app.telecom

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ashudialer.app.AshuDialerApp
import com.ashudialer.app.data.SignalingSession
import com.ashudialer.app.ui.screens.VideoCallPhase
import com.ashudialer.app.ui.screens.VideoCallScreen
import com.ashudialer.app.ui.theme.AshuDialerTheme
import com.ashudialer.app.util.isWhatsAppInstalled
import com.ashudialer.app.util.openWhatsAppChat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.webrtc.PeerConnection
import java.util.UUID


class VideoCallActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ROLE = "role"
        const val EXTRA_CALLEE_NUMBER = "callee_number"
        const val EXTRA_CALLEE_NAME = "callee_name"
        const val EXTRA_CALL_ID = "call_id"
        const val ROLE_CALLER = "caller"
        const val ROLE_CALLEE = "callee"

        fun callerIntent(context: Context, calleeNumber: String, calleeName: String): Intent =
            Intent(context, VideoCallActivity::class.java).apply {
                putExtra(EXTRA_ROLE, ROLE_CALLER)
                putExtra(EXTRA_CALLEE_NUMBER, calleeNumber)
                putExtra(EXTRA_CALLEE_NAME, calleeName)
                putExtra(EXTRA_CALL_ID, UUID.randomUUID().toString())
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

        fun calleeIntent(context: Context, callId: String, callerName: String): Intent =
            Intent(context, VideoCallActivity::class.java).apply {
                putExtra(EXTRA_ROLE, ROLE_CALLEE)
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_CALLEE_NAME, callerName)
                // Matches the same fix applied to every InCallActivity
                // launch site (see CallNotificationHelper/MainActivity) -
                // this Activity is also launched from a full-screen
                // notification PendingIntent (VideoCallListenerService.
                // showIncomingVideoCallNotification) that can fire while
                // the screen is off/locked, so it needs the same two
                // flags for the same reason: SINGLE_TOP so a re-tap
                // resumes this task instead of triggering a fresh
                // re-resolve that can re-consult an OEM keyguard, and
                // NO_USER_ACTION so the launch itself isn't treated as a
                // "fresh deliberate app open" that should be gated by
                // unlock - an incoming video call should never require
                // unlocking the phone, exactly like a regular incoming
                // call.
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
            }
    }


    private var cameraPermissionResult = mutableStateOf<Boolean?>(null)
    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> cameraPermissionResult.value = granted }

    private val isInPipMode = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        // Pressing back on an active call enters picture-in-picture instead
        // of finishing the Activity outright - the same gesture WhatsApp/
        // Meet use, so a call keeps running in a small floating window
        // rather than being torn down (see WebRtcCallManager.release() in
        // the DisposableEffect below for what finish() would otherwise
        // trigger). This now lives inside setContent as a Compose
        // BackHandler (below, keyed on phase) rather than here, since
        // RINGING_INCOMING needs different back behavior - PiP-ing an
        // unanswered incoming call, with no video to show yet, would be
        // wrong the way no stock dialer ever shrinks a still-ringing
        // incoming call into a floating window - and phase is Compose
        // state that isn't reachable from this plain onCreate scope.

        val app = application as AshuDialerApp
        val role = intent.getStringExtra(EXTRA_ROLE) ?: ROLE_CALLER
        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: UUID.randomUUID().toString()
        val displayName = intent.getStringExtra(EXTRA_CALLEE_NAME) ?: "Unknown"
        val calleeNumber = intent.getStringExtra(EXTRA_CALLEE_NUMBER) ?: ""


        val localUid = app.authRepository.currentUserUidOrNull()

        setContent {
            val themeId by app.themePreference.themeIdFlow.collectAsState(initial = "ocean")
            val settings by app.appSettingsRepository.settingsFlow.collectAsState(initial = com.ashudialer.app.data.AppSettings())

            // DEEP FIX for "an incoming video call request should show an
            // accept screen, not connect on its own": ROLE_CALLEE now
            // starts in RINGING_INCOMING and stays there - camera never
            // requested, WebRtcCallManager never created, no offer ever
            // read from the signaling session - until hasAcceptedIncoming
            // becomes true, which only ever happens from the actual Accept
            // tap on IncomingVideoCallDecision (see onAcceptIncoming
            // below). ROLE_CALLER has nothing to accept (they're the one
            // who placed the call), so it keeps starting at CONNECTING
            // exactly as before.
            var hasAcceptedIncoming by remember { mutableStateOf(role != ROLE_CALLEE) }
            var phase by remember {
                mutableStateOf(if (role == ROLE_CALLEE) VideoCallPhase.RINGING_INCOMING else VideoCallPhase.CONNECTING)
            }
            var remoteVideoTrack by remember { mutableStateOf<org.webrtc.VideoTrack?>(null) }
            var remoteAudioTrack by remember { mutableStateOf<org.webrtc.AudioTrack?>(null) }
            var isMicEnabled by remember { mutableStateOf(true) }
            var isCameraEnabled by remember { mutableStateOf(true) }
            var remoteUidResolved by remember { mutableStateOf<String?>(null) }
            var callManager by remember { mutableStateOf<WebRtcCallManager?>(null) }
            var notSignedInMessage by remember { mutableStateOf<String?>(null) }
            // True only for the one specific FAILED reason where offering
            // a WhatsApp fallback actually makes sense - see
            // onOpenWhatsApp's own doc in VideoCallScreen for why this is
            // its own flag rather than reusing notSignedInMessage's mere
            // non-null-ness: camera-permission-denied and not-signed-in
            // also set notSignedInMessage to a non-null string, but a
            // WhatsApp button on either of those screens would be a
            // non-sequitur - neither has anything to do with the other
            // person's number being unreachable.
            var calleeNotFoundOnDirectory by remember { mutableStateOf(false) }
            // Checked once per call, not re-checked reactively - whether
            // WhatsApp is installed cannot meaningfully change in the
            // middle of a single call screen's lifetime, and gates whether
            // onOpenWhatsApp below is offered at all (see VideoCallScreen's
            // own "don't show a button that can't do anything" rule,
            // already followed by onSwitchToVoiceCall).
            val whatsAppInstalled = remember { isWhatsAppInstalled(this@VideoCallActivity) }
            // Populated from the signaling session's callerNumber field,
            // but only relevant/used on the ROLE_CALLEE side - the
            // ROLE_CALLER side already knows who it's calling directly
            // from the calleeNumber intent extra, so fallbackVoiceNumber
            // below covers both without this needing to be read on that
            // side.
            var remoteCallerNumber by remember { mutableStateOf<String?>(null) }
            // The one number this screen actually offers a voice-call
            // fallback to, regardless of which role we're playing:
            // - ROLE_CALLER already has calleeNumber from the intent that
            //   started this Activity (the number this device dialed).
            // - ROLE_CALLEE has no such extra (calleeIntent() never passed
            //   one - see EXTRA_CALLEE_NUMBER usage above), so it relies
            //   entirely on remoteCallerNumber arriving from the
            //   signaling session above. Sessions created before
            //   callerNumber existed as a field, or where the caller had
            //   no myPhoneNumber configured in settings, will leave this
            //   null - the fallback button (see VideoCallScreen) simply
            //   doesn't render rather than offering a call that can't
            //   actually be placed.
            val fallbackVoiceNumber = if (role == ROLE_CALLER) calleeNumber.takeIf { it.isNotBlank() } else remoteCallerNumber?.takeIf { it.isNotBlank() }

            val hasCameraPermission = cameraPermissionResult.value
                ?: (ContextCompat.checkSelfPermission(this@VideoCallActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)

            // Only ever requests camera access once there's actually
            // something to use it for - either this is the caller (who is
            // placing the call right now, so camera is needed immediately)
            // or the callee has explicitly tapped Accept
            // (hasAcceptedIncoming). Declining, or simply leaving an
            // incoming call ringing without answering, never triggers a
            // camera permission prompt at all - exactly like never
            // triggers a WebRtcCallManager below either.
            LaunchedEffect(hasAcceptedIncoming) {
                if (hasAcceptedIncoming && !hasCameraPermission) {
                    requestCameraPermission.launch(Manifest.permission.CAMERA)
                }
            }

            LaunchedEffect(cameraPermissionResult.value) {
                if (cameraPermissionResult.value == false) {
                    notSignedInMessage = "Camera access is needed for video calls. You can allow it from your phone's app settings."
                    phase = VideoCallPhase.FAILED
                }
            }

            // DEEP IMPLEMENTATION of same-carrier vs different-carrier
            // video calling, as requested: "agar same sim like jio to jio
            // hoga to call connect ho jaye agar different hoga to warning
            // aa jaye". The previous version of this hint only ever
            // displayed THIS device's own carrier name - it never actually
            // compared it against the other person's carrier at all, so
            // it could never distinguish a same-carrier call from a
            // different-carrier one. That comparison is now real:
            //
            // - myCarrierId comes from CarrierDetector, same as before.
            // - remoteCarrierId comes from the *other* side of the
            //   signaling session - callee side reads callerCarrierId
            //   (present from the moment the offer arrives, since the
            //   caller writes it in createCall); caller side reads
            //   calleeCarrierId (only present once the callee has
            //   actually answered - see submitAnswer/receiveOfferAndSendAnswer
            //   above for where that gets written now).
            // - Both calls, in either direction, ALWAYS still connect over
            //   the internet via the exact same WebRTC/STUN path below -
            //   this hint is purely informational text, never a gate on
            //   whether the call itself proceeds. That matches the
            //   request precisely: same carrier gets a quieter "connecting"
            //   message since nothing unusual is happening, different
            //   carriers gets an explicit one-line heads-up that quality
            //   can vary since the call is crossing operator networks
            //   over general internet infrastructure rather than staying
            //   on one operator's own network end-to-end - never a block,
            //   never an extra confirmation step, exactly the "chhota sa
            //   warning" that was asked for, not a hard stop.
            var remoteCarrierIdFromSession by remember { mutableStateOf<String?>(null) }
            val myCarrierIdForHint = remember { CarrierDetector.currentCarrierId(this@VideoCallActivity) }
            val carrierRelationHint by produceState<String?>(
                initialValue = null,
                myCarrierIdForHint,
                remoteCarrierIdFromSession
            ) {
                val myCarrierDisplay = CarrierDetector.currentCarrierDisplayName(this@VideoCallActivity) ?: "your carrier"
                value = when {
                    myCarrierIdForHint == null -> null
                    remoteCarrierIdFromSession == null -> "On $myCarrierDisplay — connecting over the internet"
                    myCarrierIdForHint == remoteCarrierIdFromSession -> "Both on $myCarrierDisplay — connecting over the internet"
                    else -> "Different carriers — connecting over the internet, call quality may vary"
                }
            }


            // Gated on hasAcceptedIncoming (in addition to
            // hasCameraPermission, unchanged from before) so this entire
            // block - which is what actually creates WebRtcCallManager,
            // publishes this device's phone-directory entry, resolves the
            // remote uid, and (for the caller) sends the offer - never
            // runs at all for an incoming call still sitting at
            // RINGING_INCOMING. hasAcceptedIncoming is already true from
            // the very first frame for ROLE_CALLER (see its initial value
            // above), so this doesn't change anything about how placing a
            // call behaves - only about what happens before an incoming
            // one is actually answered.
            DisposableEffect(hasCameraPermission, hasAcceptedIncoming) {
                if (!hasAcceptedIncoming) {
                    return@DisposableEffect onDispose {}
                }
                if (localUid == null) {
                    notSignedInMessage = "Video calling couldn't sign in — check your connection and try again."
                    phase = VideoCallPhase.FAILED
                    return@DisposableEffect onDispose {}
                }
                if (!hasCameraPermission) {


                    return@DisposableEffect onDispose {}
                }

                this@VideoCallActivity.lifecycleScope.launch {


                    val myNumber = app.appSettingsRepository.settingsFlow.first().myPhoneNumber
                    if (myNumber.isNotBlank()) {
                        app.videoCallSignalingRepository.publishPhoneDirectoryEntry(localUid, myNumber)
                    }

                    val resolvedRemoteUid = when (role) {
                        ROLE_CALLER -> app.videoCallSignalingRepository.resolveUidForNumber(calleeNumber)
                        else -> null
                    }

                    if (role == ROLE_CALLER && resolvedRemoteUid == null) {
                        notSignedInMessage = "$displayName hasn't set up video calling yet."
                        calleeNotFoundOnDirectory = true
                        phase = VideoCallPhase.FAILED
                        return@launch
                    }

                    val manager = WebRtcCallManager(
                        context = this@VideoCallActivity,
                        signaling = app.videoCallSignalingRepository,
                        scope = this@VideoCallActivity.lifecycleScope,
                        callId = callId,
                        localUid = localUid,
                        remoteUid = resolvedRemoteUid ?: "",
                        onEvent = { event ->
                            when (event) {
                                is WebRtcCallEvent.RemoteStreamAdded -> {
                                    remoteVideoTrack = event.stream.videoTracks.firstOrNull()
                                    remoteAudioTrack = event.stream.audioTracks.firstOrNull()
                                }
                                is WebRtcCallEvent.ConnectionStateChanged -> {
                                    phase = when (event.state) {
                                        PeerConnection.PeerConnectionState.CONNECTED -> VideoCallPhase.ACTIVE
                                        PeerConnection.PeerConnectionState.DISCONNECTED -> VideoCallPhase.RECONNECTING
                                        PeerConnection.PeerConnectionState.FAILED -> VideoCallPhase.FAILED
                                        PeerConnection.PeerConnectionState.CLOSED -> VideoCallPhase.ENDED
                                        else -> phase
                                    }
                                }
                            }
                        }
                    )
                    manager.initialize()
                    callManager = manager

                    if (role == ROLE_CALLER) {
                        val myCarrier = CarrierDetector.currentCarrierId(this@VideoCallActivity)
                        // myNumber (resolved above) is now also stored on
                        // the signaling session as callerNumber, so the
                        // callee side has an actual dialable number to
                        // offer a voice-call fallback with - see
                        // "switch to voice call" wiring below, which reads
                        // this same session field on the callee's side.
                        manager.createAndSendOffer(calleeNumber, myCarrier, myNumber.takeIf { it.isNotBlank() })
                        phase = VideoCallPhase.RINGING_REMOTE
                    }

                }

                onDispose {
                    callManager?.release()
                }
            }



            LaunchedEffect(callId, localUid) {
                if (localUid == null) return@LaunchedEffect
                app.videoCallSignalingRepository.observeCall(callId).collect { session ->
                    if (session == null) return@collect
                    // Captured regardless of status, from whichever role's
                    // side we're on - see fallbackVoiceNumber below for
                    // why this needs both this and the ROLE_CALLER literal
                    // extra to cover both directions of the call.
                    if (role == ROLE_CALLEE) {
                        remoteCallerNumber = session.callerNumber
                    }
                    // Feeds carrierRelationHint above: whichever role we're
                    // NOT playing is "the other side", so the callee reads
                    // the caller's carrier and vice versa. The caller's
                    // value is present from the very first offer; the
                    // callee's is only present after they've actually
                    // answered (see submitAnswer) - until then this stays
                    // null on the caller's side and carrierRelationHint
                    // above correctly falls back to the "connecting over
                    // the internet" wording with no comparison yet, rather
                    // than showing a stale/wrong same-or-different verdict
                    // before there's real data for both sides.
                    remoteCarrierIdFromSession = if (role == ROLE_CALLEE) {
                        session.callerCarrierId
                    } else {
                        session.calleeCarrierId
                    }
                    when (session.status) {
                        SignalingSession.STATUS_RINGING -> {
                            // hasAcceptedIncoming guard is explicit here
                            // (in addition to callManager being null until
                            // accepted, via the DisposableEffect above) so
                            // it's unambiguous that an unanswered/declined
                            // incoming call never processes the offer or
                            // sends an answer, rather than relying only on
                            // callManager's null-safety as an implicit side
                            // effect of it not having been created yet.
                            if (role == ROLE_CALLEE && hasAcceptedIncoming && session.offerSdp != null && remoteUidResolved == null) {
                                remoteUidResolved = session.callerUid
                                val myCarrier = CarrierDetector.currentCarrierId(this@VideoCallActivity)
                                callManager?.receiveOfferAndSendAnswer(session.offerSdp, myCarrier)
                            }
                        }
                        SignalingSession.STATUS_ACCEPTED -> {
                            if (role == ROLE_CALLER && session.answerSdp != null && phase == VideoCallPhase.RINGING_REMOTE) {
                                callManager?.applyRemoteAnswer(session.answerSdp)
                            }
                        }
                        SignalingSession.STATUS_DECLINED, SignalingSession.STATUS_ENDED -> {
                            phase = VideoCallPhase.ENDED
                            finish()
                        }
                    }
                }
            }

            fun endCall() {
                this@VideoCallActivity.lifecycleScope.launch {
                    app.videoCallSignalingRepository.updateStatus(callId, SignalingSession.STATUS_ENDED)
                    localUid?.let { app.videoCallSignalingRepository.teardown(callId, it) }
                }
                finish()
            }

            AshuDialerTheme(themeId = themeId, fontSizeIndex = settings.fontSizeIndex, buttonDepth = settings.buttonDepth) {
                // Phase-aware back handling - see the long comment above
                // onBackPressedDispatcher's old registration (now removed)
                // for why this moved here. RINGING_INCOMING: back declines
                // the call outright (the same action as the Decline
                // button) rather than doing nothing or trying to PiP a
                // screen with no video yet - an incoming call a person
                // backs away from should behave like they walked away
                // from it, not like it's still silently ringing
                // somewhere. Every other phase keeps the exact same
                // PiP-first behavior as before, falling through to a
                // normal finish() only if the OEM build declines to let
                // this Activity enter PiP at all.
                BackHandler(enabled = true) {
                    if (phase == VideoCallPhase.RINGING_INCOMING) {
                        this@VideoCallActivity.lifecycleScope.launch {
                            app.videoCallSignalingRepository.updateStatus(callId, SignalingSession.STATUS_DECLINED)
                        }
                        finish()
                    } else if (!enterPipIfPossible()) {
                        finish()
                    }
                }

                // Same fix as InCallActivity - VideoCallActivity shares
                // Theme.AshuDialer.Call (see AndroidManifest.xml) but, being
                // a separate Activity with its own setContent, needs its own
                // runtime status bar icon-color sync rather than inheriting
                // InCallActivity's.
                val palette = com.ashudialer.app.ui.theme.LocalDialerPalette.current
                val view = androidx.compose.ui.platform.LocalView.current
                LaunchedEffect(palette.isDark) {
                    androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !palette.isDark
                        isAppearanceLightNavigationBars = !palette.isDark
                    }
                }

                VideoCallScreen(
                    callerName = displayName,
                    phase = phase,
                    eglBaseContext = callManager?.eglBase?.eglBaseContext,
                    localVideoTrack = callManager?.localVideoTrack,
                    remoteVideoTrack = remoteVideoTrack,
                    isMicEnabled = isMicEnabled,
                    isCameraEnabled = isCameraEnabled,
                    sameCarrierHint = notSignedInMessage ?: carrierRelationHint,
                    onToggleMic = {
                        isMicEnabled = !isMicEnabled
                        callManager?.setMicEnabled(isMicEnabled)
                    },
                    onToggleCamera = {
                        isCameraEnabled = !isCameraEnabled
                        callManager?.setCameraEnabled(isCameraEnabled)
                    },
                    onSwitchCamera = { callManager?.switchCamera() },
                    onEndCall = { endCall() },
                    // Wires IncomingVideoCallDecision's two buttons (via
                    // VideoCallScreen's RINGING_INCOMING branch) to the
                    // actual accept/decline behavior:
                    //
                    // Accept: only flips hasAcceptedIncoming to true and
                    // advances phase to CONNECTING. That single state
                    // change is what unblocks the two effects above
                    // (camera permission request, then the
                    // DisposableEffect that creates WebRtcCallManager and
                    // starts processing the already-arrived offer) - nothing
                    // else needs to happen here directly, since those
                    // effects are already keyed on hasAcceptedIncoming and
                    // will each recompose off this one flag.
                    //
                    // Decline: writes STATUS_DECLINED straight to the
                    // signaling session (mirroring what endCall() does for
                    // STATUS_ENDED) and finishes immediately, without ever
                    // touching hasAcceptedIncoming, camera, or
                    // WebRtcCallManager at all - so declining an incoming
                    // video call is exactly as inert to this device's
                    // camera/mic as never answering a regular phone call
                    // is. teardown() is intentionally skipped here (unlike
                    // endCall()): teardown releases signaling resources
                    // this device set up as part of *accepting/placing* a
                    // call (see its call at line 369 and in
                    // onSwitchToVoiceCall below), and a declined call was
                    // never accepted, so there's nothing on this side to
                    // release.
                    onAcceptIncoming = {
                        hasAcceptedIncoming = true
                        phase = VideoCallPhase.CONNECTING
                    },
                    onDeclineIncoming = {
                        this@VideoCallActivity.lifecycleScope.launch {
                            app.videoCallSignalingRepository.updateStatus(callId, SignalingSession.STATUS_DECLINED)
                        }
                        finish()
                    },
                    // Only offered when there's an actual number to call -
                    // see fallbackVoiceNumber's doc above for the two
                    // cases (ROLE_CALLER always has one; ROLE_CALLEE only
                    // does once the signaling session's callerNumber
                    // field has arrived). VideoCallScreen treats a null
                    // callback as "don't show this control at all" rather
                    // than showing a button that would fail when tapped.
                    onSwitchToVoiceCall = fallbackVoiceNumber?.let { number ->
                        {
                            // End the video call/signaling session first,
                            // same teardown endCall() already does, then
                            // hand off to the same placeCall() helper the
                            // rest of the app uses for every other call -
                            // no separate/duplicate calling path here.
                            this@VideoCallActivity.lifecycleScope.launch {
                                app.videoCallSignalingRepository.updateStatus(callId, SignalingSession.STATUS_ENDED)
                                localUid?.let { app.videoCallSignalingRepository.teardown(callId, it) }
                            }
                            DialerPermissions.placeCall(this@VideoCallActivity, number)
                            finish()
                        }
                    },
                    // See calleeNotFoundOnDirectory/isWhatsAppInstalled
                    // above and onOpenWhatsApp's own doc in VideoCallScreen
                    // for the three things all have to be true at once:
                    // this failure is specifically "couldn't find them on
                    // our own directory" (not e.g. a camera-permission
                    // failure), WhatsApp is actually installed, and there's
                    // an actual number to open a chat with. Tapping this
                    // does NOT end the video call/signaling session the way
                    // onSwitchToVoiceCall does above - there's nothing
                    // running to tear down yet at this FAILED phase
                    // (WebRtcCallManager was never even created - see the
                    // early return right above the "hasn't set up video
                    // calling yet" line this flag is set alongside), so
                    // this can simply launch WhatsApp and leave this
                    // Activity in place underneath it rather than finishing.
                    onOpenWhatsApp = if (calleeNotFoundOnDirectory && whatsAppInstalled) {
                        fallbackVoiceNumber?.let { number -> { openWhatsAppChat(this@VideoCallActivity, number) } }
                    } else null,
                    isInPip = isInPipMode.value
                )
            }
        }
    }

    private fun enterPipIfPossible(): Boolean = try {
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(9, 16))
            .build()
        enterPictureInPictureMode(params)
    } catch (e: Exception) {
        false
    }

    // Covers leaving via Home/Recents - the onBackPressedDispatcher callback
    // in onCreate covers Back. Together, every way of leaving this screen
    // keeps the call running in a floating window instead of ending it.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPipIfPossible()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode.value = isInPictureInPictureMode
    }
}
