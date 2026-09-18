package com.ashudialer.app.appcalls.scrcpy

/** Audio sources exposed by scrcpy-server 4.0. */
enum class ScrcpyAudioSource(val cliKey: String) {
    VOICE_CALL("voice-call"),
    VOICE_CALL_UPLINK("voice-call-uplink"),
    VOICE_CALL_DOWNLINK("voice-call-downlink"),
    VOICE_COMMUNICATION("mic-voice-communication"),
    OUTPUT("output"),
    PLAYBACK("playback"),
    MIC("mic");

    companion object {
        fun fromKey(key: String): ScrcpyAudioSource = entries.firstOrNull { it.cliKey == key } ?: VOICE_CALL
    }
}
