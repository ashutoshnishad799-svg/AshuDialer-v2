package com.ashudialer.app.telecom

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.webrtc.AudioTrack

object WebRtcCaptionSource {

    fun captions(
        remoteAudioTrack: AudioTrack,
        engine: CallCaptionEngine
    ): Flow<CaptionLine> = emptyFlow()
}
