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
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
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

        // Pressing back used to finish() this activity outright, which tore
        // the WebRTC session down with it (see WebRtcCallManager.release()
        // in the DisposableEffect below). Entering picture-in-picture
        // instead shrinks the call into a small floating window that keeps
        // running - the same gesture WhatsApp/Meet use. Falls through to
        // the normal back behaviour only if PiP genuinely can't be entered
        // (declined by the OEM build).
        onBackPressedDispatcher.addCallback(this) {
            if (!enterPipIfPossible()) {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        val app = application as AshuDialerApp
        val role = intent.getStringExtra(EXTRA_ROLE) ?: ROLE_CALLER
        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: UUID.randomUUID().toString()
        val displayName = intent.getStringExtra(EXTRA_CALLEE_NAME) ?: "Unknown"
        val calleeNumber = intent.getStringExtra(EXTRA_CALLEE_NUMBER) ?: ""


        val localUid = app.authRepository.currentUserUidOrNull()

        setContent {
            val themeId by app.themePreference.themeIdFlow.collectAsState(initial = "ocean")
            val settings by app.appSettingsRepository.settingsFlow.collectAsState(initial = com.ashudialer.app.data.AppSettings())

            var phase by remember { mutableStateOf(VideoCallPhase.CONNECTING) }
            var remoteVideoTrack by remember { mutableStateOf<org.webrtc.VideoTrack?>(null) }
            var remoteAudioTrack by remember { mutableStateOf<org.webrtc.AudioTrack?>(null) }
            var isMicEnabled by remember { mutableStateOf(true) }
            var isCameraEnabled by remember { mutableStateOf(true) }
            var remoteUidResolved by remember { mutableStateOf<String?>(null) }
            var callManager by remember { mutableStateOf<WebRtcCallManager?>(null) }
            var notSignedInMessage by remember { mutableStateOf<String?>(null) }
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

            LaunchedEffect(Unit) {
                if (!hasCameraPermission) {
                    requestCameraPermission.launch(Manifest.permission.CAMERA)
                }
            }

            LaunchedEffect(cameraPermissionResult.value) {
                if (cameraPermissionResult.value == false) {
                    notSignedInMessage = "Camera access is needed for video calls. You can allow it from your phone's app settings."
                    phase = VideoCallPhase.FAILED
                }
            }

            val sameCarrierHint by produceState<String?>(initialValue = null) {
                val myCarrier = CarrierDetector.currentCarrierId(this@VideoCallActivity)
                value = if (myCarrier != null) {
                    "On ${CarrierDetector.currentCarrierDisplayName(this@VideoCallActivity) ?: "your carrier"} — connecting over the internet"
                } else null
            }


            DisposableEffect(hasCameraPermission) {
                if (localUid == null) {
                    notSignedInMessage = "Sign in from More → Account to make video calls."
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
                    when (session.status) {
                        SignalingSession.STATUS_RINGING -> {
                            if (role == ROLE_CALLEE && session.offerSdp != null && remoteUidResolved == null) {
                                remoteUidResolved = session.callerUid
                                callManager?.receiveOfferAndSendAnswer(session.offerSdp)
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

            AshuDialerTheme(themeId = themeId, fontSizeIndex = settings.fontSizeIndex) {
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
                    sameCarrierHint = notSignedInMessage ?: sameCarrierHint,
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
