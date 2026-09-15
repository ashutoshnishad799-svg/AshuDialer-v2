package com.ashudialer.app.telecom

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Plays the recording notice through the device TTS engine. */
class RecordingAnnouncement(context: Context) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingAnnouncement = false

    // Same fix as TypeToTalkEngine's identical field, for the identical
    // reason: setAudioAttributes() alone isn't reliably honored per-
    // utterance by every OEM TTS engine, so this is also passed directly
    // in speakNow()'s own params Bundle rather than relied on solely as
    // an engine-wide default set once at init.
    private val communicationAudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    init {
        tts = TextToSpeech(appContext, this)
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts?.language = Locale.getDefault()
            tts?.setAudioAttributes(communicationAudioAttributes)
            if (pendingAnnouncement) {
                pendingAnnouncement = false
                speakNow()
            }
        }
    }

    fun speak() {
        if (!ready) {
            pendingAnnouncement = true
            return
        }
        speakNow()
    }

    private fun speakNow() {
        val params = android.os.Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_VOICE_CALL)
        }
        tts?.setAudioAttributes(communicationAudioAttributes)
        tts?.speak(
            "This call is now being recorded.",
            TextToSpeech.QUEUE_FLUSH,
            params,
            "ashu_recording_notice"
        )
    }

    fun release() {
        ready = false
        pendingAnnouncement = false
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
