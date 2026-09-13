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

    init {
        tts = TextToSpeech(appContext, this)
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts?.language = Locale.getDefault()
            tts?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
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
        tts?.speak(
            "This call is now being recorded.",
            TextToSpeech.QUEUE_FLUSH,
            null,
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
