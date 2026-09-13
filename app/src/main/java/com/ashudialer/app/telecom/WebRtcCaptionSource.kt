package com.ashudialer.app.telecom

import kotlinx.coroutines.flow.Flow
import org.webrtc.AudioTrack

/**
 * WebRTC caption source.
 *
 * stream-webrtc-android 1.1.1 does not expose AudioTrackSink as an
 * org.webrtc Java/Kotlin type, so raw PCM cannot be consumed here
 * through AudioTrack.addSink(AudioTrackSink).
 *
 * Keep the source compile-safe until the underlying audio callback
 * provided by the Stream/WebRTC audio pipeline is wired in.
 */
object WebRtcCaptionSource {

    fun captions(
        remoteAudioTrack: AudioTrack,
        engine: CallCaptionEngine
    ): Flow<CaptionLine> {
        return kotlinx.coroutines.flow.emptyFlow()
    }
}
