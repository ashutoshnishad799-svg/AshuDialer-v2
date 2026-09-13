package com.ashudialer.app.telecom

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/** One message the person typed and had spoken into the call. isSpeaking=true while the TTS engine is actively voicing it - lets the call-screen UI show which message (if several were sent in quick succession) is playing right now. */
data class TypedMessage(val id: String, val text: String, val isSpeaking: Boolean)

/**
 * Speaks typed text into an active call, using the identical
 * USAGE_VOICE_COMMUNICATION audio routing RecordingAnnouncement already
 * uses to make the recording notice audible to the other party - that
 * AudioAttributes usage is what routes TTS output into the call's own
 * audio stream instead of the phone's normal media/notification stream,
 * and is the whole reason this same approach can be reused here for
 * arbitrary text instead of one fixed sentence.
 *
 * Works identically on both the WebRTC call path and, on the Root build,
 * a normal carrier call - unlike live captions, this direction only ever
 * needs to *produce* audio into the call, never read the other party's
 * audio, so it has none of live captions' far-end-audio-access constraint
 * (see CallCaptionEngine's class doc) and needs no flavor gating at all.
 */
class TypeToTalkEngine(context: Context) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false

    // Messages typed before the TTS engine finished initializing (a real
    // possibility if someone types and hits send within the first moment
    // of a call) are queued here rather than dropped silently.
    private val pendingQueue = mutableListOf<String>()

    private var onMessageStateChanged: ((TypedMessage) -> Unit)? = null
    private val idsByUtteranceId = mutableMapOf<String, String>()

    init {
        tts = TextToSpeech(appContext, this)
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (!ready) return
        tts?.language = Locale.getDefault()
        tts?.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                idsByUtteranceId[utteranceId]?.let { messageId ->
                    onMessageStateChanged?.invoke(TypedMessage(messageId, "", isSpeaking = true))
                }
            }
            override fun onDone(utteranceId: String) {
                idsByUtteranceId.remove(utteranceId)?.let { messageId ->
                    onMessageStateChanged?.invoke(TypedMessage(messageId, "", isSpeaking = false))
                }
            }
            // onError(String) is deprecated in favor of onError(String, Int)
            // but remains abstract on UtteranceProgressListener (confirmed
            // against the Android SDK source), so it must still be
            // overridden regardless of the newer overload's existence.
            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String) {
                idsByUtteranceId.remove(utteranceId)?.let { messageId ->
                    onMessageStateChanged?.invoke(TypedMessage(messageId, "", isSpeaking = false))
                }
            }
        })
        // Flush anything typed before initialization finished.
        val queued = pendingQueue.toList()
        pendingQueue.clear()
        queued.forEach { speakInternal(it) }
    }

    fun setOnMessageStateChanged(listener: (TypedMessage) -> Unit) {
        onMessageStateChanged = listener
    }

    /**
     * Speaks the given text into the call. QUEUE_ADD (not QUEUE_FLUSH,
     * unlike RecordingAnnouncement's one-shot announcement) is deliberate:
     * a person typing several short messages in a row - the realistic
     * pattern for this feature, e.g. "library mein hoon" then a moment
     * later "2 minute mein bahar aata hoon" - should have each one spoken
     * in full and in order, not have a fast second message cut the first
     * one off mid-sentence.
     */
    fun speak(text: String): String {
        val trimmed = text.trim()
        val messageId = UUID.randomUUID().toString()
        if (trimmed.isBlank()) return messageId
        if (!ready) {
            pendingQueue += trimmed
            return messageId
        }
        speakInternal(trimmed, messageId)
        return messageId
    }

    private fun speakInternal(text: String, messageId: String = UUID.randomUUID().toString()) {
        val utteranceId = "type_to_talk_$messageId"
        idsByUtteranceId[utteranceId] = messageId
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    /** Stops whatever is currently speaking and clears anything queued - used when the person ends the call while a message is still playing. */
    fun stopAll() {
        pendingQueue.clear()
        idsByUtteranceId.clear()
        tts?.stop()
    }

    fun release() {
        ready = false
        stopAll()
        tts?.shutdown()
        tts = null
    }
}
