package com.ashudialer.app.telecom

import android.media.AudioManager
import android.media.ToneGenerator


class DtmfPlayer {
    private var toneGenerator: ToneGenerator? = null

    private fun generator(): ToneGenerator? {
        var g = toneGenerator
        if (g == null) {
            g = try {
                ToneGenerator(AudioManager.STREAM_DTMF, 90)
            } catch (_: RuntimeException) {


                null
            }
            toneGenerator = g
        }
        return g
    }

    fun play(digit: Char) {
        val tone = when (digit) {
            '0' -> ToneGenerator.TONE_DTMF_0
            '1' -> ToneGenerator.TONE_DTMF_1
            '2' -> ToneGenerator.TONE_DTMF_2
            '3' -> ToneGenerator.TONE_DTMF_3
            '4' -> ToneGenerator.TONE_DTMF_4
            '5' -> ToneGenerator.TONE_DTMF_5
            '6' -> ToneGenerator.TONE_DTMF_6
            '7' -> ToneGenerator.TONE_DTMF_7
            '8' -> ToneGenerator.TONE_DTMF_8
            '9' -> ToneGenerator.TONE_DTMF_9
            '*' -> ToneGenerator.TONE_DTMF_S
            '#' -> ToneGenerator.TONE_DTMF_P
            else -> return
        }
        try {
            generator()?.startTone(tone, 150)
        } catch (_: Exception) {

        }
    }

    fun release() {
        try {
            toneGenerator?.release()
        } catch (_: Exception) {
        }
        toneGenerator = null
    }
}
