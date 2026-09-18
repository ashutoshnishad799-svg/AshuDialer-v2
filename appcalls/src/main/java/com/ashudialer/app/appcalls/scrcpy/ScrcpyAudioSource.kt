// Adapted from ShizuCallRecorder (github.com/kitsumed/ShizuCallRecorder), GPLv3+.
package com.ashudialer.app.appcalls.scrcpy

/**
 * The one scrcpy audio_source this module uses: OUTPUT (REMOTE_SUBMIX), which captures the
 * final mixed audio rendered to the speaker output. This module exists specifically for
 * WhatsApp/Telegram VoIP calls, which AshuDialer's native CallRecorder.kt cannot reach at all
 * (there is no modem-level VOICE_CALL tap for a VoIP call - see AppCallRecordingEngine.kt's own
 * doc comment for why OUTPUT is the source that actually works there, and why the two
 * MIC-class/PLAYBACK alternatives upstream also tried do not).
 *
 * Upstream ShizuCallRecorder exposes a much larger debug/picker matrix of sources
 * (VOICE_CALL, VOICE_COMMUNICATION, MIC, MIC_UNPROCESSED, etc.) for its own general-purpose
 * recorder UI. Every one of those is redundant here - AshuDialer's CallRecorder.kt already
 * owns the equivalent native-telephony sources for real phone calls - so only OUTPUT is
 * ported, deliberately, to keep this module scoped to the one capability gap it actually fills.
 */
enum class ScrcpyAudioSource(val cliKey: String) {
    /** Requires API 19+ (Android 4.4); gated behind CAPTURE_AUDIO_OUTPUT like VOICE_CALL is. */
    OUTPUT(cliKey = "output")
}
