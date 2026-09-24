package com.ashudialer.app.ui.screens

import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Renders the two live video surfaces for an active *native* (carrier
 * ViLTE/VoLTE) video call - the piece InCallActivity's onUpgradeToNativeVideo
 * doc comment explicitly called out as "a separate, larger UI surface of
 * its own - not yet built here". Everything upstream of this screen
 * (capability detection, sendSessionModifyRequest, auto-accepting an
 * incoming upgrade, and now PixelInCallService's nativeVideoUpgradeActiveFlow
 * confirming the upgrade actually succeeded) was already correct; this is
 * the part that turns "the call is now bidirectional video at the network
 * level" into something the person can actually see.
 *
 * This is deliberately a plain TextureView wrapped with AndroidView, not a
 * WebRTC SurfaceViewRenderer like VideoCallActivity/VideoCallScreen use for
 * this app's own app-to-app video calls - the two are unrelated pipelines.
 * A native call's video frames are decoded and rendered by the carrier's
 * own Connection.VideoProvider (out-of-process, on the modem/IMS stack
 * side), which only knows how to draw into a plain android.view.Surface
 * handed to it via InCallService.VideoCall.setDisplaySurface/
 * setPreviewSurface - it has no concept of WebRTC at all. TextureView is
 * the standard, minimal way to obtain that Surface inside Compose.
 *
 * videoCall is passed in directly (from InCallActivity's own already-held
 * current.videoCall) rather than re-derived here, since InCallActivity is
 * already the single source of truth for "which Call, and its VideoCall,
 * is on screen right now" - this screen only ever draws what it's given.
 */
@Composable
fun NativeVideoCallScreen(
    videoCall: android.telecom.InCallService.VideoCall,
    callerName: String,
    isConnected: Boolean,
    onEndCall: () -> Unit
) {
    val context = LocalContext.current

    // One-shot device rotation, read at surface-creation time rather than
    // tracked continuously via OrientationEventListener - the call still
    // works correctly if the person rotates mid-call (the carrier side
    // simply keeps whatever orientation was last sent), it just won't
    // re-adjust live. Called out here rather than silently done, since a
    // continuous listener is the natural next improvement if rotation
    // during a call turns out to matter in practice.
    val view = LocalView.current
    val deviceRotationDegrees = remember(view) {
        val rotation = view.display?.rotation ?: Surface.ROTATION_0
        when (rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    // The carrier VideoProvider needs a specific camera id (setCamera),
    // not just "some front camera" - resolved once via Camera2's own
    // enumeration rather than assuming id "1" is always the front camera,
    // which isn't guaranteed across OEMs/multi-camera devices.
    val frontCameraId = remember {
        runCatching {
            val cameraManager = context.getSystemService(android.content.Context.CAMERA_SERVICE) as CameraManager
            cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            }
        }.getOrNull()
    }

    // Tracks whether the remote TextureView has delivered its first frame
    // worth of surface setup yet, purely to decide whether to still show
    // "Connecting video..." over a black rectangle or the live feed -
    // setDisplaySurface succeeding doesn't by itself guarantee the peer's
    // video has started arriving, but there is no separate Telecom
    // callback for "first frame rendered" to key off instead.
    var remoteSurfaceReady by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Remote party's video - fills the screen. Handed to the carrier's
        // VideoProvider via setDisplaySurface; the carrier decodes and
        // draws directly into it from here on, this app never touches the
        // pixels itself.
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                            runCatching {
                                videoCall.setDisplaySurface(Surface(surfaceTexture))
                                videoCall.setDeviceOrientation(deviceRotationDegrees)
                            }
                            remoteSurfaceReady = true
                        }
                        override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {}
                        // Returning true (rather than managing the
                        // SurfaceTexture's lifetime manually) tells the
                        // TextureView it's safe to release the underlying
                        // buffers itself - correct here since nothing else
                        // in this screen holds a competing reference to
                        // this exact SurfaceTexture once the view is gone.
                        override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean = true
                        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (!remoteSurfaceReady) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Connecting video…", color = Color.White, fontSize = 15.sp)
            }
        }

        // This device's own camera preview - small corner PIP, the same
        // layout convention as every other video-calling UI (including
        // this app's own WebRTC VideoCallScreen). Handed to the carrier's
        // VideoProvider via setPreviewSurface/setCamera - the actual
        // camera capture happens on the carrier/modem side of the IMS
        // stack, not through this app's own Camera2 session, which is why
        // there's no CameraDevice.open() anywhere in this file.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 16.dp)
                .size(width = 110.dp, height = 148.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                .background(Color(0xFF1C1C1E))
        ) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                                runCatching {
                                    videoCall.setPreviewSurface(Surface(surfaceTexture))
                                    frontCameraId?.let { videoCall.setCamera(it) }
                                }
                            }
                            override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {}
                            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean = true
                            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Caller name + a status line, top-left - deliberately minimal
        // rather than reusing CallScreen's full header (mute/speaker/hold
        // row), since none of that has been wired to work correctly
        // *while this screen is showing* yet. Keeping this screen's own
        // scope to exactly "show the video, let me hang up" avoids
        // implying those controls work here when they haven't been
        // verified to.
        Column(modifier = Modifier.align(Alignment.TopStart).padding(top = 48.dp, start = 20.dp)) {
            Text(callerName, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (isConnected) "Video call" else "Connecting…",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp
            )
        }

        // Single hang-up control, bottom-center - same red
        // (0xFFE53E3E) as CallScreen's own end-call button, so this
        // screen doesn't introduce a second, different-looking "end call"
        // affordance elsewhere in the same app.
        IconButton(
            onClick = onEndCall,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .size(64.dp)
                .clip(CircleShape)
                .background(Color(0xFFE53E3E))
        ) {
            Icon(Icons.Filled.CallEnd, contentDescription = "End call", tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}
