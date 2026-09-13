package com.ashudialer.app.telecom

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/** One line of live caption. isFinal=false lines are still being spoken and will be replaced by the next emission for the same utterance; isFinal=true means Vosk detected a pause and committed the text. */
data class CaptionLine(val text: String, val isFinal: Boolean, val timestampMillis: Long = System.currentTimeMillis())

/**
 * Wraps a single Vosk Recognizer for one call's captioning session. This
 * class does not know or care where its PCM comes from - see
 * VideoCallActivity (WebRTC remote audio track) and, on the Root build
 * only, RootCarrierCaptionSource (the same protected VOICE_CALL-style
 * source CallRecorder already uses) for the two real audio producers. That
 * separation is deliberate: keeping this class audio-source-agnostic is
 * what let the same engine serve both the always-available WebRTC path and
 * the Root-only carrier-call path without two separate recognition
 * implementations.
 *
 * Vosk's Recognizer is explicitly documented as NOT thread-safe and not
 * shareable across calls, so a fresh instance is created per call (see
 * start()) even though the underlying Model - the actual ~50MB of
 * loaded acoustic/language data - is reused from CaptionModelManager's
 * cache across calls.
 */
class CallCaptionEngine(private val model: Model, initialSampleRateHz: Int = 16_000) {

    private var recognizer: Recognizer? = null

    // Not `val` / fixed at construction: WebRTC's AudioTrackSink.onData is
    // documented to report a sampleRate that can genuinely change partway
    // through a call (commonly starting at 16000 and settling to 48000 once
    // the jitter buffer stabilizes) - see the WebRTC discuss group thread on
    // AudioTrackSinkInterface::OnData(). Vosk's own sample rate is fixed at
    // Recognizer construction time, so a mid-call rate change has to
    // recreate the recognizer at the new rate (see acceptAudio below)
    // rather than silently feeding rate-mismatched PCM into a stale one,
    // which would produce garbled/nonsense captions with no visible error.
    private var activeSampleRateHz: Int = initialSampleRateHz

    /** Returns false if the recognizer could not be created (e.g. a corrupted or incompletely-unpacked model on disk) - the caller should treat this as captions-unavailable for this call rather than retrying, since CaptionModelManager already validated the model directory existed before this class was ever constructed. */
    fun start(sampleRateHz: Int = activeSampleRateHz): Boolean {
        activeSampleRateHz = sampleRateHz
        recognizer?.close()
        return try {
            recognizer = Recognizer(model, activeSampleRateHz.toFloat())
            true
        } catch (_: java.io.IOException) {
            recognizer = null
            false
        }
    }

    /**
     * Feeds one chunk of 16-bit PCM mono audio. sampleRateHz is the rate
     * THIS chunk actually arrived at (from AudioTrackSink.onData's own
     * sampleRate parameter, or a fixed constant for the Root carrier-call
     * path) - not assumed to match what the recognizer was built with. If
     * it doesn't match, the recognizer is transparently rebuilt at the new
     * rate first (see the class doc above for why this happens at all),
     * which costs one dropped chunk during the switch but keeps every
     * chunk after that correctly decoded rather than silently garbled.
     *
     * Returns the resulting CaptionLine, or null if Vosk had nothing new to
     * report for this chunk (a normal, frequent outcome for silence or a
     * chunk with no new recognizable content - not an error).
     *
     * acceptWaveForm's own return value (true = an utterance just completed)
     * is what decides whether to read .result (final JSON: {"text": "..."})
     * or .partialResult (in-progress JSON: {"partial": "..."}) - these are
     * Kotlin's synthetic properties for Vosk's actual getResult()/
     * getPartialResult() Java methods. Reading the wrong one after a false
     * return would silently show stale/repeated text rather than throwing,
     * which is why this distinction matters here rather than always calling
     * one or the other.
     */
    fun acceptAudio(pcm: ByteArray, length: Int, sampleRateHz: Int = activeSampleRateHz): CaptionLine? {
        if (sampleRateHz != activeSampleRateHz) {
            if (!start(sampleRateHz)) return null
        }
        val rec = recognizer ?: return null
        return try {
            val utteranceComplete = rec.acceptWaveForm(pcm, length)
            val json = if (utteranceComplete) rec.result else rec.partialResult
            val text = JSONObject(json).optString(if (utteranceComplete) "text" else "partial").trim()
            if (text.isBlank()) null else CaptionLine(text = text, isFinal = utteranceComplete)
        } catch (_: Exception) {
            null
        }
    }

    /** Flushes any trailing partial utterance into a final one - call once when the call ends, so the last thing said isn't lost as a partial that never got committed. */
    fun finish(): CaptionLine? {
        val rec = recognizer ?: return null
        return try {
            val text = JSONObject(rec.finalResult).optString("text").trim()
            if (text.isBlank()) null else CaptionLine(text = text, isFinal = true)
        } catch (_: Exception) {
            null
        }
    }

    fun release() {
        recognizer?.close()
        recognizer = null
    }
}

/**
 * Adapts WebRTC's push-based remote-audio callback (see
 * VideoCallActivity's use of AudioTrack.addSink) into a cold
 * Flow<CaptionLine> the call-screen Composable can collect with
 * collectAsState, matching how every other live call-state source in this
 * codebase (call, callState, etc.) is already surfaced to Compose.
 *
 * audioSource receives an onChunk callback taking (pcm, length,
 * sampleRateHz) - the sample rate travels with every chunk rather than
 * being fixed once, because AudioTrackSink.onData's own sampleRate
 * parameter is exactly this per-chunk shape (see acceptAudio's docs on
 * why that rate isn't assumed constant for the life of the call).
 */
fun captionFlow(
    engine: CallCaptionEngine,
    initialSampleRateHz: Int = 16_000,
    audioSource: (onChunk: (ByteArray, Int, Int) -> Unit) -> AutoCloseable
): Flow<CaptionLine> =
    callbackFlow {
        if (!engine.start(initialSampleRateHz)) {
            close()
            return@callbackFlow
        }
        val closable = audioSource { pcm, length, sampleRateHz ->
            engine.acceptAudio(pcm, length, sampleRateHz)?.let { trySend(it) }
        }
        awaitClose {
            engine.finish()?.let { trySend(it) }
            closable.close()
            engine.release()
        }
    }
