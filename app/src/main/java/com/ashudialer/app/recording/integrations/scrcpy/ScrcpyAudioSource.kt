package com.ashudialer.app.recording.integrations.scrcpy

enum class ScrcpyAudioSource(val cliKey: String) {
    VOICE_CALL("voice-call"),
    VOICE_CALL_UPLINK("voice-call-uplink"),
    VOICE_CALL_DOWNLINK("voice-call-downlink"),
    VOICE_COMMUNICATION("mic-voice-communication");

    companion object {
        fun fromKey(key: String): ScrcpyAudioSource =
            entries.firstOrNull { it.cliKey == key }
                ?: throw IllegalArgumentException("Unknown ScrcpyAudioSource key: $key")
    }
}
