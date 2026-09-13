package com.ashudialer.app.telecom

import kotlinx.coroutines.flow.Flow
import org.webrtc.AudioTrack
import org.webrtc.AudioTrackSink
import java.nio.ByteBuffer

/**
 * Turns a WebRTC remote AudioTrack (from WebRtcCallEvent.RemoteStreamAdded
 * - see VideoCallActivity) into a Flow<CaptionLine> using CallCaptionEngine.
 *
 * AudioTrack.addSink(AudioTrackSink) is a real, public, documented method
 * on this exact library (stream-webrtc-android, already a project
 * dependency for the rest of video calling) and is explicitly described as
 * "only called for remote audio tracks" - i.e. it delivers the other
 * person's voice, not the local microphone, which is exactly the audio
 * this feature needs to caption.
 */
object WebRtcCaptionSource {

    /**
     * bitsPerSample is checked on every callback, not assumed to always be
     * 16: WebRTC's own OnData contract allows other bit depths in
     * principle, and feeding non-16-bit data to Vosk (which strictly
     * expects 16-bit PCM) would silently decode as noise rather than fail
     * loudly. In the observed/expected case (16-bit, which the standard
     * WebRTC audio pipeline used by this library always produces in
     * practice) this check costs one branch per callback and is otherwise
     * invisible.
     */
    fun captions(remoteAudioTrack: AudioTrack, engine: CallCaptionEngine): Flow<CaptionLine> =
        captionFlow(engine = engine, initialSampleRateHz = 16_000) { onChunk ->
            val sink = AudioTrackSink { audioData, bitsPerSample, sampleRate, numberOfChannels, numberOfFrames, _ ->
                if (bitsPerSample != 16) return@AudioTrackSink

                // onData's own doc explicitly warns the ByteBuffer must be
                // copied before this callback returns if the data is needed
                // afterwards - WebRTC reuses/frees the underlying buffer
                // immediately after this function returns, so holding onto
                // the ByteBuffer reference itself (rather than a fresh copy)
                // would read stale or already-freed memory once acceptAudio
                // runs even slightly after this callback, e.g. if a Vosk
                // call briefly blocks.
                val byteCount = numberOfFrames * numberOfChannels * (bitsPerSample / 8)
                val pcm = ByteArray(byteCount)
                val duplicate = audioData.duplicate() // duplicate(): read without disturbing the original buffer's position, in case WebRTC itself still needs it after this sink returns
                duplicate.rewind()
                duplicate.get(pcm, 0, minOf(byteCount, duplicate.remaining()))

                // Vosk's small models are trained and tuned for mono
                // 16-bit PCM; WebRTC's remote audio is very commonly
                // delivered as mono already for voice calls, but this
                // downmixes defensively rather than assuming it, since
                // feeding interleaved stereo straight to a mono-trained
                // recognizer would garble every other sample as
                // if it were a second channel's data.
                val monoPcm = if (numberOfChannels <= 1) pcm else downmixToMono(pcm, numberOfChannels)
                onChunk(monoPcm, monoPcm.size, sampleRate)
            }
            remoteAudioTrack.addSink(sink)
            AutoCloseable { remoteAudioTrack.removeSink(sink) }
        }

    private fun downmixToMono(interleaved: ByteArray, channels: Int): ByteArray {
        val frameCount = interleaved.size / (2 * channels) // 2 bytes per 16-bit sample
        val mono = ByteArray(frameCount * 2)
        val buffer = ByteBuffer.wrap(interleaved).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val monoBuffer = ByteBuffer.wrap(mono).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (frame in 0 until frameCount) {
            var sum = 0
            for (ch in 0 until channels) sum += buffer.getShort((frame * channels + ch) * 2).toInt()
            monoBuffer.putShort((sum / channels).toShort())
        }
        return mono
    }
}
