// Adapted from ShizuCallRecorder (github.com/kitsumed/ShizuCallRecorder), GPLv3+.
package com.ashudialer.app.appcalls.scrcpy

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import com.ashudialer.app.appcalls.AppCallsLogger
import java.io.Closeable
import java.nio.ByteBuffer

/**
 * Writes scrcpy audio packets into an MPEG-4/M4A container via [MediaMuxer].
 *
 * You MUST call [close]: [MediaMuxer.stop] writes the container index (the MP4 "moov" atom).
 * Without it the file may be unplayable by some audio players.
 *
 * Packet flow: CONFIG packet -> addAudioTrack() (one-time setup with CSD); audio packet ->
 * writeSampleData() with a wall-clock PTS.
 *
 * Timestamp strategy: PTS is derived from [System.nanoTime] rather than scrcpy's own stream PTS,
 * so a real silence in a discontinuous source produces a correct gap instead of a squashed or
 * corrupted file. See https://github.com/Genymobile/scrcpy/pull/5870
 */
class ScrcpyAudioMuxer(
    private val outputFileDescriptor: java.io.FileDescriptor,
    private val outputDisplayPath: String
) : Closeable {

    companion object {
        private const val TAG = "AppCalls:ScrcpyAudioMuxer"
    }

    private var muxer: MediaMuxer? = null
    private var audioTrackIndex = -1
    private var isMuxerStarted = false
    private var firstPacketTimeNanos: Long = -1L
    private var lastWrittenPtsUs: Long = -1L
    private var lastPacketWallClockNanos: Long = -1L
    private var totalIgnoredGapNanos: Long = 0L

    /** Ordinary packet intervals are ~20ms; waiting past this means recording paused or a big frame drop happened. */
    private val GAP_THRESHOLD_NANOS = 400_000_000L

    /** Natural gap left behind when squashing a detected pause, rather than zero. */
    private val GAP_SLACK_NANOS = 25_000_000L

    fun initialize(codec: ScrcpyAudioCodec) {
        if (muxer != null) return
        AppCallsLogger.d(TAG, "Initialising muxer: codec=${codec.cliKey} format=${codec.outputFormat} path='$outputDisplayPath'")
        muxer = MediaMuxer(outputFileDescriptor, codec.outputFormat)
    }

    fun writePacket(packet: ScrcpyClient.AudioPacket, codec: ScrcpyAudioCodec) {
        if (packet.isConfigPacket) {
            if (audioTrackIndex < 0) addAudioTrack(configData = packet.data, codec = codec)
            return
        }

        if (!isMuxerStarted || audioTrackIndex < 0) {
            AppCallsLogger.w(TAG, "writePacket(): muxer not ready - dropping frame")
            return
        }

        val nowNanos = System.nanoTime()

        if (firstPacketTimeNanos == -1L) {
            firstPacketTimeNanos = nowNanos
            lastPacketWallClockNanos = nowNanos
            AppCallsLogger.d(TAG, "First audio frame: wall-clock origin set, pts=0")
        } else {
            val gapNanos = nowNanos - lastPacketWallClockNanos
            if (gapNanos > GAP_THRESHOLD_NANOS) {
                val ignoredNanos = gapNanos - GAP_SLACK_NANOS
                totalIgnoredGapNanos += ignoredNanos
                AppCallsLogger.d(TAG, "Detected gap of ${gapNanos / 1_000_000} ms. Squashing ${ignoredNanos / 1_000_000} ms.")
            }
            lastPacketWallClockNanos = nowNanos
        }

        val wallClockPtsUs = (nowNanos - firstPacketTimeNanos - totalIgnoredGapNanos) / 1000L
        // MediaMuxer requires strictly increasing PTS; nanoTime is monotonic but microsecond
        // truncation can produce equal values for back-to-back packets.
        val normalizedPtsUs = if (wallClockPtsUs > lastWrittenPtsUs) wallClockPtsUs else lastWrittenPtsUs + 1L

        val bufferInfo = MediaCodec.BufferInfo().apply {
            offset = 0
            size = packet.data.size
            presentationTimeUs = normalizedPtsUs
        }
        lastWrittenPtsUs = normalizedPtsUs

        muxer?.writeSampleData(audioTrackIndex, ByteBuffer.wrap(packet.data), bufferInfo)
    }

    override fun close() {
        if (isMuxerStarted) {
            AppCallsLogger.d(TAG, "Finalising muxer for '$outputDisplayPath'")
            runCatching { muxer?.stop() }.onFailure { e ->
                AppCallsLogger.e(TAG, "Muxer stop failed (file may be incomplete): ${e.message}")
            }
        }
        runCatching { muxer?.release() }
        muxer = null
        isMuxerStarted = false
        audioTrackIndex = -1
        firstPacketTimeNanos = -1L
        lastWrittenPtsUs = -1L
        lastPacketWallClockNanos = -1L
        totalIgnoredGapNanos = 0L
        AppCallsLogger.d(TAG, "Muxer closed")
    }

    private fun addAudioTrack(configData: ByteArray, codec: ScrcpyAudioCodec) {
        val csdBytes = configData.takeIf { it.isNotEmpty() }
        if (csdBytes == null) {
            AppCallsLogger.e(TAG, "Empty config data received. Cannot initialize audio track.")
            return
        }

        val mediaFormat = MediaFormat().apply {
            setString(MediaFormat.KEY_MIME, codec.mimeType)
            setInteger(MediaFormat.KEY_SAMPLE_RATE, ScrcpyConfig.AUDIO_SAMPLE_RATE)
            setInteger(MediaFormat.KEY_CHANNEL_COUNT, ScrcpyConfig.AUDIO_CHANNELS)
            setByteBuffer("csd-0", ByteBuffer.wrap(csdBytes))
        }

        audioTrackIndex = muxer?.addTrack(mediaFormat) ?: -1
        if (audioTrackIndex < 0) {
            AppCallsLogger.e(TAG, "Failed to add audio track (addTrack returned $audioTrackIndex)")
            return
        }

        muxer?.start()
        isMuxerStarted = audioTrackIndex >= 0
        AppCallsLogger.d(TAG, "Audio track added (index=$audioTrackIndex mime=${codec.mimeType}) - muxer started")
    }
}
