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

enum class VideoCallPhase { CONNECTING, RINGING_REMOTE, ACTIVE, RECONNECTING, FAILED, ENDED }


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
    // Live captions of the other person's voice (see CallCaptionEngine) -
    // captionsAvailable gates whether the CC toggle button even appears at
    // all, separately from whether captionLines currently has anything in
    // it, since a person with captions off in Settings shouldn't see a
    // button for a feature that isn't running for this call.
    captionLines: List<com.ashudialer.app.telecom.CaptionLine> = emptyList(),
    captionsAvailable: Boolean = false,
    // Type-to-talk: typed text spoken into the call via TTS (see
    // TypeToTalkEngine). typeToTalkAvailable gates the keyboard-icon
    // button the same way captionsAvailable gates the CC button above.
    typeToTalkAvailable: Boolean = false,
    typedMessages: List<com.ashudialer.app.telecom.TypedMessage> = emptyList(),
    onSendTypedMessage: (String) -> Unit = {},
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
            }
        }


        CaptionsAndTypeToTalkOverlay(
            captionsAvailable = captionsAvailable,
            captionLines = captionLines,
            typeToTalkAvailable = typeToTalkAvailable,
            typedMessages = typedMessages,
            onSendTypedMessage = onSendTypedMessage,
            bottomInsetForCaptions = 104.dp,
            bottomInsetForComposer = 92.dp
        )

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
    VideoCallPhase.CONNECTING -> "Connecting…"
    VideoCallPhase.RINGING_REMOTE -> "Ringing…"
    VideoCallPhase.ACTIVE -> "Video call"
    VideoCallPhase.RECONNECTING -> "Reconnecting…"
    VideoCallPhase.FAILED -> "Couldn't connect"
    VideoCallPhase.ENDED -> "Call ended"
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
