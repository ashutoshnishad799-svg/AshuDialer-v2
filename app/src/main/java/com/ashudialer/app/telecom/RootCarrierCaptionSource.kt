package com.ashudialer.app.telecom

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log

/**
 * Live captions for a NORMAL SIM CALL, root/priv-app build only - see
 * CallCaptionEngine's class doc and AppSettings.liveCaptionsEnabled's
 * comment for why this doesn't exist at all on the Normal build. Gate this
 * behind BuildConfig.CARRIER_CALL_CAPTIONS_ENABLED at every call site, the
 * same way CallRecorder's recording features are gated behind
 * BuildConfig.CALL_RECORDING_ENABLED - this class assumes that check has
 * already happened and does not re-check it itself.
 *
 * Deliberately NOT built on top of MediaRecorder (what CallRecorder uses):
 * MediaRecorder's job is "encode audio to a file," with no API to observe
 * samples as they arrive, which is useless for a feature that needs to
 * react to audio in real time. AudioRecord is the correct API for
 * streaming raw PCM as it's captured, and Android's own AudioRecord.Builder
 * accepts the identical MediaRecorder.AudioSource constants (VOICE_CALL,
 * VOICE_UPLINK, VOICE_DOWNLINK, MIC) as MediaRecorder does, so this reuses
 * exactly the same source values and the same per-device
 * loadKnownSilentSources learning CallRecorder already has - not a second,
 * independently-guessing detector for the same hardware quirks.
 *
 * Important real constraint (confirmed against the Android CDD's Concurrent
 * Capture section): only one capture can hold a privacy-sensitive source
 * like VOICE_CALL at a time. If the person has BOTH call recording and
 * live captions turned on for the same call, only one of the two
 * AudioRecord-based captures can actually succeed - see
 * canRunAlongsideRecording below, which callers should check before
 * starting this, and CaptionSettingsScreen's explanation text should stay
 * honest about this rather than silently letting one feature go quiet.
 */
class RootCarrierCaptionSource(private val context: Context) {

    private var audioRecord: AudioRecord? = null
    private var readThread: Thread? = null
    @Volatile private var isRunning = false

    /**
     * True if it's safe to also attempt captions right now. Not a hard OS-
     * level check (Android doesn't expose "is VOICE_CALL currently held by
     * someone" as a queryable API) - instead reflects this app's own
     * knowledge of whether ITS OWN CallRecorder instance is presently
     * mid-recording, since that's the one concurrent user of this same
     * protected source this app itself can create. A conflict with some
     * OTHER app also holding VOICE_CALL is a real but rare possibility this
     * can't detect in advance; captions would simply fail to start in that
     * case, the same graceful failure path as a device with no working
     * source at all.
     */
    fun canRunAlongsideRecording(callRecorder: CallRecorder): Boolean = !callRecorder.isRecording

    /**
     * Starts tapping the call audio and delivers PCM chunks via onChunk
     * (matching captionFlow's audioSource shape: pcm, length, sampleRateHz -
     * sampleRateHz here is fixed at construction, unlike WebRTC's
     * AudioTrackSink, since AudioRecord's sample rate is chosen once when
     * opening the source and does not change mid-call the way a WebRTC
     * jitter buffer's negotiated rate can). Returns false if no AudioSource
     * on this device could be opened at all, mirroring CallRecorder's own
     * RecordingMode.FAILED outcome for the identical underlying reason -
     * the caller should treat this as captions-unavailable-on-this-device
     * rather than an error to retry.
     */
    fun start(onChunk: (pcm: ByteArray, length: Int, sampleRateHz: Int) -> Unit): Boolean {
        val sampleRate = 16_000
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBufferSize <= 0) return false

        val knownSilent = CallRecorder.loadKnownSilentSources(context)
        // Same priority order as CallRecorder.start(): VOICE_CALL (combined
        // both-sides, best case) down to MIC (last resort, unprivileged,
        // picks up only what's audible - typically just this side unless
        // speakerphone is on).
        val candidates = listOf(
            MediaRecorder.AudioSource.VOICE_CALL,
            MediaRecorder.AudioSource.VOICE_UPLINK,
            MediaRecorder.AudioSource.VOICE_DOWNLINK,
            MediaRecorder.AudioSource.MIC
        ).filterNot { it in knownSilent }
            .ifEmpty {
                listOf(
                    MediaRecorder.AudioSource.VOICE_CALL,
                    MediaRecorder.AudioSource.VOICE_UPLINK,
                    MediaRecorder.AudioSource.VOICE_DOWNLINK,
                    MediaRecorder.AudioSource.MIC
                )
            }

        for (source in candidates) {
            val record = try {
                @Suppress("MissingPermission") // CAPTURE_AUDIO_OUTPUT is declared in app/src/root/AndroidManifest.xml and allowlisted via the Magisk module's privapp-permissions XML - only reachable on the root build, gated by BuildConfig.CARRIER_CALL_CAPTIONS_ENABLED at every call site per this class's own doc above.
                AudioRecord(source, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBufferSize * 2)
            } catch (e: Exception) {
                Log.w("RootCarrierCaptionSource", "AudioSource $source threw on construction", e)
                null
            }
            if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                record?.release()
                continue
            }

            try {
                record.startRecording()
            } catch (e: Exception) {
                Log.w("RootCarrierCaptionSource", "AudioSource $source failed to start", e)
                record.release()
                continue
            }
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                record.release()
                continue
            }

            // A source that opens and starts is accepted here without the
            // silence-detection CallRecorder itself does (comparing several
            // seconds of amplitude against a noise floor) - that logic is
            // genuinely useful for a FILE the person will judge afterwards
            // by listening to it, but captions fail visibly and immediately
            // (no caption lines ever appear) rather than silently producing
            // an unusable artifact, so the person is never left thinking
            // they have working captions when they don't the way a silent
            // recording file could mislead them.
            audioRecord = record
            isRunning = true
            readThread = Thread {
                val buffer = ByteArray(minBufferSize)
                while (isRunning) {
                    val read = try {
                        record.read(buffer, 0, buffer.size)
                    } catch (_: Exception) {
                        break
                    }
                    if (read > 0) onChunk(buffer, read, sampleRate)
                }
            }.apply { start() }
            return true
        }
        return false
    }

    fun stop() {
        isRunning = false
        readThread?.join(500)
        readThread = null
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
            // AudioRecord.stop() throws IllegalStateException if the record
            // was never successfully started - already-not-running is not
            // a failure state worth surfacing here.
        }
        audioRecord?.release()
        audioRecord = null
    }
}
