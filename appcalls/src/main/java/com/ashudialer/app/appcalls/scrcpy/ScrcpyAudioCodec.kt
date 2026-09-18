// Adapted from ShizuCallRecorder (github.com/kitsumed/ShizuCallRecorder), GPLv3+.
// See this module's README.md "Provenance" section for the full adaptation notes.
package com.ashudialer.app.appcalls.scrcpy

import android.media.MediaFormat
import android.media.MediaMuxer

/**
 * The one audio codec this module actually uses. Upstream ShizuCallRecorder supports both
 * OPUS and AAC with a user-facing picker; this module only ports AAC, deliberately, so that
 * AppCallRecorder's output container matches what AshuDialer's own CallRecorder.kt already
 * produces for native telephony calls (.m4a / MPEG_4 / AudioEncoder.AAC) - one recordings list,
 * one player, one file format across both engines, rather than two different container types
 * depending on which engine happened to record a given call.
 *
 * FourCC and MIME values mirror AudioCodec.java in scrcpy-server:
 * https://github.com/Genymobile/scrcpy/blob/master/server/src/main/java/com/genymobile/scrcpy/audio/AudioCodec.java
 */
enum class ScrcpyAudioCodec(
    val cliKey: String,
    val codecFourCC: Int,
    val defaultBitRate: Int,
    val outputFormat: Int,
    val mimeType: String,
    val containerExtension: String
) {
    /** FourCC: ASCII "\0aac" = 0x00616163. */
    AAC(
        cliKey = "aac",
        codecFourCC = 0x00616163,
        defaultBitRate = 64000, // Matches CallRecorder.kt's own AudioEncodingBitRate for consistency.
        outputFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
        mimeType = MediaFormat.MIMETYPE_AUDIO_AAC,
        containerExtension = ".m4a"
    );

    companion object {
        fun fromFourCC(fourCC: Int): ScrcpyAudioCodec =
            entries.firstOrNull { it.codecFourCC == fourCC }
                ?: throw IllegalArgumentException("Unknown ScrcpyAudioCodec fourCC: 0x${fourCC.toString(16)} (this module only supports AAC)")
    }
}
