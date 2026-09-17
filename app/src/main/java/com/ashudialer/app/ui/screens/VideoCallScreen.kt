package com.ashudialer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import kotlin.math.roundToInt

// RINGING_INCOMING: added so an incoming video call actually stops for a
// real accept/decline decision on this device, instead of connecting the
// instant the notification is tapped. See VideoCallActivity's role ==
// ROLE_CALLEE handling for exactly where this phase is entered and how
// accept/decline each resolve it.
enum class VideoCallPhase { RINGING_INCOMING, CONNECTING, RINGING_REMOTE, ACTIVE, RECONNECTING, FAILED, ENDED }


@Composable
fun VideoCallScreen(
    callerName: String,
    phase: VideoCallPhase,
    eglBaseContext: EglBase.Context?,
    localVideoTrack: VideoTrack?,
    remoteVideoTrack: VideoTrack?,
    isMicEnabled: Boolean,
    isCameraEnabled: Boolean,
    sameCarrierHint: String?,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onEndCall: () -> Unit,
    isInPip: Boolean = false,
    // Only non-null during VideoCallPhase.RINGING_INCOMING - see
    // VideoCallActivity's ROLE_CALLEE handling for where this phase is
    // entered/resolved. When both are null (every other phase), this
    // screen renders its normal in-call controls exactly as before;
    // RINGING_INCOMING instead renders IncomingVideoCallDecision below and
    // nothing else, so a call can never silently connect without one of
    // these two actually being tapped.
    onAcceptIncoming: (() -> Unit)? = null,
    onDeclineIncoming: (() -> Unit)? = null,
    // Non-null only when there's an actual number this screen can hand
    // off to for a plain voice call instead - see VideoCallActivity's
    // fallbackVoiceNumber for exactly when that is. Kept nullable rather
    // than always showing a button, since a null callback here means
    // there's genuinely no number available to fall back to on this
    // particular call (most commonly: this is the callee side and the
    // caller's signaling session hasn't reported a callerNumber, either
    // because it predates this field or they had no number configured in
    // their own settings) - showing a button that can't actually place a
    // call would be worse than not showing one.
    onSwitchToVoiceCall: (() -> Unit)? = null,
    // Non-null only when this specific failure is "the number we dialed
    // isn't reachable through our own signaling directory" (i.e. the other
    // person isn't on AshuDialer, or hasn't opened it since installing) AND
    // WhatsApp is actually installed on this device - see
    // VideoCallActivity's calleeNotFoundOnDirectory/isWhatsAppInstalled for
    // exactly when both are true. Deliberately NOT offered for every FAILED
    // reason (camera permission denied, not signed in, etc.) - those have
    // nothing to do with the other person's number being unreachable, so a
    // WhatsApp button there would be a non-sequitur. And deliberately never
    // shown when WhatsApp isn't installed - same "don't show a button that
    // can't actually do anything" rule onSwitchToVoiceCall already follows.
    //
    // What tapping this actually does (see VideoCallActivity): opens a
    // WhatsApp chat with this number via WhatsApp's own click-to-chat deep
    // link. It does NOT programmatically start a WhatsApp video call -
    // WhatsApp doesn't expose any public way for another app to do that,
    // only to open the chat - so from there the person still taps
    // WhatsApp's own video-call button themselves. That's genuinely the
    // most any third-party app can offer here.
    onOpenWhatsApp: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // The system's floating PiP window is only a couple of centimetres
    // across - no room for controls, name/status text, or the self-view -
    // so it shows just the remote feed (or plain black before it arrives),
    // the same way most call apps render their PiP window.
    if (isInPip) {
        Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
            if (remoteVideoTrack != null && eglBaseContext != null) {
                VideoSurface(
                    track = remoteVideoTrack,
                    eglBaseContext = eglBaseContext,
                    mirror = false,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        return
    }

    // DEEP FIX for "a received video call request should show me an
    // accept screen, not connect automatically": RINGING_INCOMING is a
    // dead end on its own screen, deliberately never falling through to
    // the normal in-call layout below it. Nothing about camera/mic/remote
    // video is touched or shown here - WebRtcCallManager.initialize()
    // (which starts this device's own camera capture) only ever runs
    // after onAcceptIncoming is actually tapped, on the VideoCallActivity
    // side - so declining, or simply never answering, this phase never
    // once turns this device's camera on or sends anything to the other
    // side, exactly like declining a real call never does.
    if (phase == VideoCallPhase.RINGING_INCOMING) {
        IncomingVideoCallDecision(
            callerName = callerName,
            onAccept = onAcceptIncoming ?: {},
            onDecline = onDeclineIncoming ?: {},
            modifier = modifier
        )
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(Color.Black)) {

        if (remoteVideoTrack != null && eglBaseContext != null && phase == VideoCallPhase.ACTIVE) {
            VideoSurface(
                track = remoteVideoTrack,
                eglBaseContext = eglBaseContext,
                mirror = false,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(callerName, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(statusLabel(phase), color = Color.White.copy(alpha = 0.65f), fontSize = 15.sp)
            }
        }


        if (localVideoTrack != null && eglBaseContext != null && isCameraEnabled) {
            // Draggable self-view: starts in the same top-right corner the
            // fixed layout used to place it, but can now be moved anywhere
            // on screen. Position is tracked in raw pixels (remember, not
            // rememberSaveable - it's fine for this to reset to the default
            // corner on a fresh call) and clamped to the container's own
            // measured bounds each drag, so a fast flick can't throw it
            // off-screen in any direction.
            val density = LocalDensity.current
            val pipWidthPx = with(density) { 100.dp.toPx() }
            val pipHeightPx = with(density) { 140.dp.toPx() }
            val maxWidthPx = with(density) { maxWidth.toPx() }
            val maxHeightPx = with(density) { maxHeight.toPx() }
            val edgeMarginPx = with(density) { 16.dp.toPx() }
            val topMarginPx = WindowInsets.statusBars.getTop(density).toFloat() + with(density) { 12.dp.toPx() }

            var offsetX by remember { mutableStateOf(maxWidthPx - pipWidthPx - edgeMarginPx) }
            var offsetY by remember { mutableStateOf(topMarginPx) }

            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .size(width = 100.dp, height = 140.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1C1C22))
                    .pointerInput(maxWidthPx, maxHeightPx) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount.x)
                                .coerceIn(0f, (maxWidthPx - pipWidthPx).coerceAtLeast(0f))
                            offsetY = (offsetY + dragAmount.y)
                                .coerceIn(0f, (maxHeightPx - pipHeightPx).coerceAtLeast(0f))
                        }
                    }
            ) {
                VideoSurface(
                    track = localVideoTrack,
                    eglBaseContext = eglBaseContext,
                    mirror = true,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                // Same cutout gap as CallScreen/IncomingCallScreen/etc. -
                // this is the caller-name row directly under the top edge,
                // which is exactly the kind of element that ends up
                // partly hidden behind a punch-hole camera on some
                // devices if only the status bar height (not the cutout
                // itself) is accounted for.
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                .padding(start = 20.dp, top = 12.dp, end = 100.dp)
        ) {
            Text(callerName, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(statusLabel(phase), color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            if (sameCarrierHint != null) {
                Spacer(Modifier.height(4.dp))
                val isFailureMessage = phase == VideoCallPhase.FAILED
                Text(
                    sameCarrierHint,
                    color = if (isFailureMessage) Color(0xFFFF8A75) else Color(0xFF7FE0D6),
                    fontSize = if (isFailureMessage) 13.sp else 12.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 17.sp
                )
                // Only offered once video has actually failed (not merely
                // "connecting" or mid-call reconnecting) and only when
                // there's a real number to hand off to - see
                // onSwitchToVoiceCall's own doc above for when that is.
                // Voice genuinely doesn't need data/Firebase/WebRTC the
                // way this video call does, so this is a real working
                // alternative in exactly the moment video isn't panning
                // out, not a placeholder.
                if (isFailureMessage && onSwitchToVoiceCall != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF34C759))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSwitchToVoiceCall() }
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Icon(Icons.Filled.Call, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Switch to voice call", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                // Second, independent fallback row - see onOpenWhatsApp's
                // own doc above for exactly when this is non-null. Kept as
                // its own Row directly below rather than merged into one
                // wide button, since the two can appear together (a call
                // that couldn't reach this app's own directory has no
                // reason voice AND WhatsApp couldn't both be offered) and
                // each needs to stay independently tappable.
                if (isFailureMessage && onOpenWhatsApp != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF25D366))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onOpenWhatsApp() }
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Icon(Icons.Filled.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Video call via WhatsApp", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp, start = 32.dp, end = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            VideoControlButton(
                icon = if (isMicEnabled) Icons.Filled.Mic else Icons.Filled.MicOff,
                active = !isMicEnabled,
                onClick = onToggleMic
            )
            VideoControlButton(
                icon = if (isCameraEnabled) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                active = !isCameraEnabled,
                onClick = onToggleCamera
            )
            VideoControlButton(
                icon = Icons.Filled.Cameraswitch,
                active = false,
                onClick = onSwitchCamera
            )
            IconButton(
                onClick = onEndCall,
                modifier = Modifier.size(58.dp).clip(CircleShape).background(Color(0xFFE0442E))
            ) {
                Icon(Icons.Filled.CallEnd, contentDescription = "End call", tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
    }
}

@Composable
private fun VideoControlButton(icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (active) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.16f))
    ) {
        Icon(icon, contentDescription = null, tint = if (active) Color(0xFF1C1C22) else Color.White, modifier = Modifier.size(22.dp))
    }
}

private fun statusLabel(phase: VideoCallPhase): String = when (phase) {
    VideoCallPhase.RINGING_INCOMING -> "Incoming video call"
    VideoCallPhase.CONNECTING -> "Connecting…"
    VideoCallPhase.RINGING_REMOTE -> "Ringing…"
    VideoCallPhase.ACTIVE -> "Video call"
    VideoCallPhase.RECONNECTING -> "Reconnecting…"
    VideoCallPhase.FAILED -> "Couldn't connect"
    VideoCallPhase.ENDED -> "Call ended"
}

/**
 * The screen shown while VideoCallPhase.RINGING_INCOMING - a real,
 * deliberate accept/decline decision, the same as this app's regular
 * IncomingCallScreen makes for a normal phone call. Kept visually distinct
 * from that screen (full-screen black backdrop matching the rest of
 * VideoCallScreen, a simple centered avatar-initial circle rather than a
 * themed aurora background) since it's a different call type entirely and
 * doesn't need to match the dialer's own per-theme styling the way a
 * regular call's screen does - what matters is that it's unmistakably an
 * incoming call needing a decision, with nothing auto-connecting.
 */
@Composable
private fun IncomingVideoCallDecision(
    callerName: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0B0B10))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))
            Text(
                "INCOMING VIDEO CALL",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )

            Spacer(Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2A2A35)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = callerName.trim().firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                callerName.ifBlank { "Unknown caller" },
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text("Video call", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onDecline,
                        modifier = Modifier.size(64.dp).clip(CircleShape).background(Color(0xFFE0442E))
                    ) {
                        Icon(Icons.Filled.CallEnd, contentDescription = "Decline", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Decline", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onAccept,
                        modifier = Modifier.size(64.dp).clip(CircleShape).background(Color(0xFF34C759))
                    ) {
                        Icon(Icons.Filled.Videocam, contentDescription = "Accept", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Accept", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}


@Composable
private fun VideoSurface(
    track: VideoTrack,
    eglBaseContext: EglBase.Context,
    mirror: Boolean,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(eglBaseContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setMirror(mirror)
                setEnableHardwareScaler(true)
                track.addSink(this)
            }
        },
        onRelease = { renderer ->
            track.removeSink(renderer)
            renderer.release()
        }
    )
}
