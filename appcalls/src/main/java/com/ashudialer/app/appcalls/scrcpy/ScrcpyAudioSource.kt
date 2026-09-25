// Adapted from ShizuCallRecorder (github.com/kitsumed/ShizuCallRecorder), GPLv3+.
package com.ashudialer.app.appcalls.scrcpy

/**
 * Every audio source scrcpy-server (v4.0, verified against the bundled jar) can capture: the same
 * eleven Ever Dialer's recorder module exposes.
 *
 * [cliKey] is passed verbatim to scrcpy-server's `audio_source=` argument.
 * [phoneCallPicker] marks the sources that make sense for a normal carrier call and are shown in
 * the main picker. The rest ([advanced] = true) sit under "Advanced" so a beginner isn't offered
 * sources that record the wrong thing, but a user whose phone stays silent on the usual sources
 * still has every fallback Ever offers.
 */
enum class ScrcpyAudioSource(
    val cliKey: String,
    val label: String,
    val description: String,
    val minApi: Int,
    val phoneCallPicker: Boolean,
    val advanced: Boolean = !phoneCallPicker
) {
    VOICE_CALL(
        "voice-call", "Voice call",
        "Records both sides of a phone call. The raw audio, with phone tones. Best choice.",
        4, true
    ),
    VOICE_COMMUNICATION(
        "mic-voice-communication", "Voice communication mic",
        "Microphone tuned for voice calls, with built-in echo cancellation. Try this if Voice call is silent on your phone.",
        11, true
    ),
    VOICE_CALL_UPLINK(
        "voice-call-uplink", "Voice call uplink (your side)",
        "Records only your side of a phone call.",
        4, true
    ),
    VOICE_CALL_DOWNLINK(
        "voice-call-downlink", "Voice call downlink (other side)",
        "Records only the other person's side of a phone call.",
        4, true
    ),
    MIC(
        "mic", "Standard microphone",
        "Captures audio from the main microphone with minor noise reduction. Hears both sides only on speakerphone.",
        1, true
    ),

    // ---- Advanced: Ever marks these debug-only / not meant for ordinary calls -------------
    OUTPUT(
        "output", "Audio output (remote submix)",
        "Captures the final mixed audio exactly as it is sent to the speakers. Used automatically for WhatsApp / Telegram calls.",
        19, false
    ),
    PLAYBACK(
        "playback", "Audio playback",
        "Captures audio from other apps, except those that block recording.",
        29, false
    ),
    MIC_UNPROCESSED(
        "mic-unprocessed", "Unprocessed mic",
        "Raw microphone audio without automatic filtering or enhancement.",
        24, false
    ),
    MIC_CAMCORDER(
        "mic-camcorder", "Camcorder mic",
        "Tuned for video recording, favours sound from the camera direction.",
        7, false
    ),
    MIC_VOICE_RECOGNITION(
        "mic-voice-recognition", "Voice recognition mic",
        "Microphone with processing tuned for speech recognition (reduced noise).",
        7, false
    ),
    VOICE_PERFORMANCE(
        "voice-performance", "Voice performance",
        "High-speed microphone mode designed for live music and real-time audio apps.",
        29, false
    );

    companion object {
        /** The sources shown in the main phone-call picker, in display order. */
        val phoneCallChoices: List<ScrcpyAudioSource>
            get() = entries.filter { it.phoneCallPicker }

        /** Everything else, shown under "Advanced". */
        val advancedChoices: List<ScrcpyAudioSource>
            get() = entries.filter { it.advanced }

        /** Sources the running device's Android version can actually use. */
        fun availableOnThisDevice(sdkInt: Int): List<ScrcpyAudioSource> = entries.filter { it.minApi <= sdkInt }

        fun fromKey(key: String): ScrcpyAudioSource =
            entries.firstOrNull { it.cliKey == key }
                ?: throw IllegalArgumentException("Unknown ScrcpyAudioSource key: $key")

        fun fromKeyOrDefault(key: String?): ScrcpyAudioSource =
            entries.firstOrNull { it.cliKey == key } ?: VOICE_CALL
    }
}
