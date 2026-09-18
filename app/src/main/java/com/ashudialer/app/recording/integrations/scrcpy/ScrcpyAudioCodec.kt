package com.ashudialer.app.recording.integrations.scrcpy

import android.media.MediaFormat
import android.media.MediaMuxer

enum class ScrcpyAudioCodec(
    val cliKey: String,
    val codecFourCC: Int,
    val defaultBitRate: Int,
    val outputFormat: Int,
    val mimeType: String,
    val containerExtension: String
) {
    AAC(
        cliKey = "aac",
        codecFourCC = 0x00616163,
        defaultBitRate = 32000,
        outputFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
        mimeType = MediaFormat.MIMETYPE_AUDIO_AAC,
        containerExtension = ".m4a"
    );

    companion object {
        fun fromKey(key: String): ScrcpyAudioCodec =
            entries.firstOrNull { it.cliKey == key }
                ?: throw IllegalArgumentException("Unknown ScrcpyAudioCodec key: $key")
        fun fromFourCC(fourCC: Int): ScrcpyAudioCodec =
            entries.firstOrNull { it.codecFourCC == fourCC }
                ?: throw IllegalArgumentException("Unknown ScrcpyAudioCodec fourCC: $fourCC")
    }
}
