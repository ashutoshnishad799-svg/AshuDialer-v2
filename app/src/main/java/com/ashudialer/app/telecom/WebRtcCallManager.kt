package com.ashudialer.app.telecom

import android.content.Context
import android.media.AudioManager
import com.ashudialer.app.data.SignalingIceCandidate
import com.ashudialer.app.data.VideoCallSignalingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


private val ICE_SERVERS = listOf(
    PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
    PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
)

sealed class WebRtcCallEvent {
    data class RemoteStreamAdded(val stream: MediaStream) : WebRtcCallEvent()
    data class ConnectionStateChanged(val state: PeerConnection.PeerConnectionState) : WebRtcCallEvent()
}


class WebRtcCallManager(
    private val context: Context,
    private val signaling: VideoCallSignalingRepository,
    private val scope: CoroutineScope,
    private val callId: String,
    private val localUid: String,
    private val remoteUid: String,
    private val onEvent: (WebRtcCallEvent) -> Unit
) {
    val eglBase: EglBase = EglBase.create()

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var audioSource: AudioSource? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    var localVideoTrack: VideoTrack? = null
        private set
    var localAudioTrack: AudioTrack? = null
        private set

    private var remoteCandidatesJob: Job? = null

    fun initialize() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )

        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        setupLocalTracks()
        peerConnection = createPeerConnection()
        setSpeakerphoneEnabled(true)
    }

    private val audioManager: AudioManager? by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    private var previousAudioMode: Int? = null

    /**
     * Routes call audio to the speaker. A video call is normally held away
     * from the ear so the screen stays visible, so - unlike a voice call -
     * defaulting to the earpiece leaves the other person barely audible.
     */
    fun setSpeakerphoneEnabled(enabled: Boolean) {
        val am = audioManager ?: return
        if (previousAudioMode == null) previousAudioMode = am.mode
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = enabled
    }

    private fun setupLocalTracks() {
        val f = factory ?: return


        val enumerator = Camera2Enumerator(context)
        val frontCameraName = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            ?: enumerator.deviceNames.firstOrNull()
        if (frontCameraName != null) {
            val capturer = enumerator.createCapturer(frontCameraName, null)
            val helper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
            surfaceTextureHelper = helper
            val source = f.createVideoSource(false)
            capturer.initialize(helper, context, source.capturerObserver)
            capturer.startCapture(1280, 720, 30)
            videoCapturer = capturer
            videoSource = source
            localVideoTrack = f.createVideoTrack("video_$localUid", source)
        }


        val audioConstraints = MediaConstraints()
        val aSource = f.createAudioSource(audioConstraints)
        audioSource = aSource
        localAudioTrack = f.createAudioTrack("audio_$localUid", aSource)
    }

    private fun createPeerConnection(): PeerConnection? {
        val f = factory ?: return null
        val rtcConfig = PeerConnection.RTCConfiguration(ICE_SERVERS).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val pc = f.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                scope.launchSafely {
                    signaling.addIceCandidate(
                        callId, localUid,
                        SignalingIceCandidate(candidate.sdpMid ?: "", candidate.sdpMLineIndex, candidate.sdp)
                    )
                }
            }

            override fun onAddStream(stream: MediaStream) {
                onEvent(WebRtcCallEvent.RemoteStreamAdded(stream))
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                onEvent(WebRtcCallEvent.ConnectionStateChanged(newState))
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            override fun onSignalingChange(newState: PeerConnection.SignalingState) {}
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {}
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onDataChannel(channel: org.webrtc.DataChannel) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: org.webrtc.RtpReceiver, streams: Array<out MediaStream>) {}
        }) ?: return null

        localVideoTrack?.let { pc.addTrack(it, listOf("stream_$localUid")) }
        localAudioTrack?.let { pc.addTrack(it, listOf("stream_$localUid")) }


        remoteCandidatesJob = signaling.observeRemoteIceCandidates(callId, remoteUid)
            .onEach { c -> pc.addIceCandidate(IceCandidate(c.sdpMid, c.sdpMLineIndex, c.candidate)) }
            .launchIn(scope)

        return pc
    }


    suspend fun createAndSendOffer(calleeNumber: String, callerCarrierId: String?, callerNumber: String?) {
        val pc = peerConnection ?: return
        val offer = pc.createOfferSuspend(MediaConstraints())
        pc.setLocalDescriptionSuspend(offer)
        signaling.createCall(callId, localUid, calleeNumber, callerCarrierId, callerNumber, offer.description)
    }


    suspend fun receiveOfferAndSendAnswer(offerSdp: String) {
        val pc = peerConnection ?: return
        pc.setRemoteDescriptionSuspend(SessionDescription(SessionDescription.Type.OFFER, offerSdp))
        val answer = pc.createAnswerSuspend(MediaConstraints())
        pc.setLocalDescriptionSuspend(answer)
        signaling.submitAnswer(callId, answer.description)
    }


    suspend fun applyRemoteAnswer(answerSdp: String) {
        val pc = peerConnection ?: return
        pc.setRemoteDescriptionSuspend(SessionDescription(SessionDescription.Type.ANSWER, answerSdp))
    }

    fun setMicEnabled(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }

    fun setCameraEnabled(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    fun switchCamera() {
        videoCapturer?.switchCamera(null)
    }

    fun release() {
        remoteCandidatesJob?.cancel()
        try {
            videoCapturer?.stopCapture()
        } catch (_: Exception) {
        }
        videoCapturer?.dispose()
        surfaceTextureHelper?.dispose()
        videoSource?.dispose()
        audioSource?.dispose()
        peerConnection?.close()
        peerConnection?.dispose()
        factory?.dispose()
        eglBase.release()
        previousAudioMode?.let { mode ->
            @Suppress("DEPRECATION")
            audioManager?.isSpeakerphoneOn = false
            audioManager?.mode = mode
        }
    }
}

private fun CoroutineScope.launchSafely(block: suspend () -> Unit) {
    launch {
        try {
            block()
        } catch (_: Exception) {
        }
    }
}


private suspend fun PeerConnection.createOfferSuspend(constraints: MediaConstraints): SessionDescription =
    suspendCancellableCoroutine { cont ->
        createOffer(object : SdpObserver by NoopSdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) = cont.resume(sdp)
            override fun onCreateFailure(error: String) = cont.resumeWithException(IllegalStateException(error))
        }, constraints)
    }

private suspend fun PeerConnection.createAnswerSuspend(constraints: MediaConstraints): SessionDescription =
    suspendCancellableCoroutine { cont ->
        createAnswer(object : SdpObserver by NoopSdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) = cont.resume(sdp)
            override fun onCreateFailure(error: String) = cont.resumeWithException(IllegalStateException(error))
        }, constraints)
    }

private suspend fun PeerConnection.setLocalDescriptionSuspend(sdp: SessionDescription) =
    suspendCancellableCoroutine<Unit> { cont ->
        setLocalDescription(object : SdpObserver by NoopSdpObserver {
            override fun onSetSuccess() = cont.resume(Unit)
            override fun onSetFailure(error: String) = cont.resumeWithException(IllegalStateException(error))
        }, sdp)
    }

private suspend fun PeerConnection.setRemoteDescriptionSuspend(sdp: SessionDescription) =
    suspendCancellableCoroutine<Unit> { cont ->
        setRemoteDescription(object : SdpObserver by NoopSdpObserver {
            override fun onSetSuccess() = cont.resume(Unit)
            override fun onSetFailure(error: String) = cont.resumeWithException(IllegalStateException(error))
        }, sdp)
    }


private object NoopSdpObserver : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {}
    override fun onSetFailure(error: String?) {}
}
