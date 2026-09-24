// Adapted from ShizuCallRecorder (github.com/kitsumed/ShizuCallRecorder), GPLv3+.
package com.ashudialer.app.appcalls.scrcpy

import android.media.MediaFormat
import android.media.MediaMuxer

/**
 * Audio codecs scrcpy-server can encode, with everything the muxer and file-saver need.
 * FourCC/MIME values mirror AudioCodec.java in scrcpy-server.
 *
 * AAC/.m4a is the default here (widest player support, matches what the Recordings screen
 * already lists). Opus/.ogg is smaller at the same quality and needs Android 10+ (API 29).
 */
enum class ScrcpyAudioCodec(
    val cliKey: String,
    val label: String,
    val codecFourCC: Int,
    val defaultBitRate: Int,
    val outputFormat: Int,
    val mimeType: String,
    val fileMimeType: String,
    val containerExtension: String
) {
    /** FourCC: ASCII "\0aac" = 0x00616163. */
    AAC(
        cliKey = "aac",
        label = "AAC (.m4a) - works everywhere",
        codecFourCC = 0x00616163,
        defaultBitRate = 32000,
        outputFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
        mimeType = MediaFormat.MIMETYPE_AUDIO_AAC,
        fileMimeType = "audio/mp4",
        containerExtension = ".m4a"
    ),

    /** FourCC: ASCII "opus" = 0x6F707573. */
    OPUS(
        cliKey = "opus",
        label = "Opus (.ogg) - smallest files",
        codecFourCC = 0x6F707573,
        defaultBitRate = 16000,
        outputFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG,
        mimeType = MediaFormat.MIMETYPE_AUDIO_OPUS,
        fileMimeType = "audio/ogg",
        containerExtension = ".ogg"
    );

    companion object {
        fun fromFourCC(fourCC: Int): ScrcpyAudioCodec =
            entries.firstOrNull { it.codecFourCC == fourCC }
                ?: throw IllegalArgumentException("Unknown ScrcpyAudioCodec fourCC: 0x${fourCC.toString(16)}")

        fun fromKey(key: String): ScrcpyAudioCodec =
            entries.firstOrNull { it.cliKey == key }
                ?: throw IllegalArgumentException("Unknown ScrcpyAudioCodec key: $key")

        fun fromKeyOrDefault(key: String?): ScrcpyAudioCodec =
            entries.firstOrNull { it.cliKey == key } ?: AAC
    }
}
