// Adapted from ShizuCallRecorder (github.com/kitsumed/ShizuCallRecorder), GPLv3+.
package com.ashudialer.app.appcalls.scrcpy

import android.os.ParcelFileDescriptor
import com.ashudialer.app.appcalls.AppCallsLogger
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.EOFException
import java.io.FileInputStream

/**
 * Reads the binary audio stream that scrcpy-server sends over the pipe.
 *
 * Stream connection header (once): 4 bytes, codec FourCC (e.g. "aac" = 0x00616163).
 *
 * Per-packet header (12 bytes), mirroring Streamer.java#writeFrameMeta() in scrcpy-server:
 *   putLong(ptsAndFlags) then putInt(packetSize). The top 3 bits of the 8-byte PTS carry flags:
 *   bit 63 = media/session (audio always 0=media), bit 62 = config packet, bit 61 = key frame.
 *
 * See: https://github.com/Genymobile/scrcpy/blob/master/server/src/main/java/com/genymobile/scrcpy/device/Streamer.java
 */
class ScrcpyClient(
    private val inputPfd: ParcelFileDescriptor,
    private val expectedCodec: ScrcpyAudioCodec,
    private val listener: AudioPacketListener
) : Closeable {

    companion object {
        private const val TAG = "AppCalls:ScrcpyClient"
        private const val MEDIA_PACKET_FLAG = 1L shl 63
        private const val PACKET_FLAG_CONFIG = 1L shl 62
        private const val PACKET_FLAG_KEY_FRAME = 1L shl 61
        /** 1 MiB hard cap - a legitimate AAC packet is well under 64 KB; anything past this means stream misalignment. */
        private const val MAX_PACKET_SIZE = 1 * 1024 * 1024
    }

    interface AudioPacketListener {
        fun onMetadataReceived(codec: ScrcpyAudioCodec)
        fun onAudioPacket(packet: AudioPacket)
        fun onStreamEnd(error: String?)
    }

    data class AudioPacket(val pts: Long, val isConfigPacket: Boolean, val data: ByteArray)

    private data class AudioEnvelope(
        val rawPtsAndFlags: Long,
        val pts: Long,
        val isMedia: Boolean,
        val isConfig: Boolean,
        val isKeyFrame: Boolean,
        val payloadSize: Int
    )

    @Volatile
    private var running = false

    fun start() {
        running = true
        val inputStream = DataInputStream(BufferedInputStream(FileInputStream(inputPfd.fileDescriptor)))
        try {
            val receivedFourCC = inputStream.readInt()
            val resolvedCodec = ScrcpyAudioCodec.fromFourCC(receivedFourCC)
            AppCallsLogger.d(TAG, "Codec FourCC: received=0x${receivedFourCC.toString(16)} resolved=${resolvedCodec.cliKey} expected=${expectedCodec.cliKey}")

            if (resolvedCodec != expectedCodec) {
                AppCallsLogger.w(TAG, "Codec mismatch: requested ${expectedCodec.cliKey} but server sent ${resolvedCodec.cliKey}")
            }
            listener.onMetadataReceived(resolvedCodec)

            var hasReceivedConfig = false
            while (running) {
                val header = readPacketHeader(inputStream)

                if (!hasReceivedConfig) {
                    if (!header.isConfig) {
                        throw java.io.IOException(
                            "Protocol error: first packet must be CONFIG, got a standard packet " +
                                "(isMedia=${header.isMedia}, isConfig=${header.isConfig}, isKeyFrame=${header.isKeyFrame}). " +
                                "Did the scrcpy packet format change?"
                        )
                    }
                    hasReceivedConfig = true
                }

                if (!header.isMedia) {
                    AppCallsLogger.w(TAG, "Unexpected session packet on audio stream! flags=0x${header.rawPtsAndFlags.toString(16)}")
                }

                if (header.payloadSize <= 0 || header.payloadSize > MAX_PACKET_SIZE) {
                    throw java.io.IOException(
                        "Implausible packet size ${header.payloadSize} after PTS/Flags 0x${header.rawPtsAndFlags.toString(16)} " +
                            "(pts=${header.pts}, config=${header.isConfig}, keyFrame=${header.isKeyFrame}) - probable stream misalignment."
                    )
                }

                val payloadBytes = ByteArray(header.payloadSize)
                inputStream.readFully(payloadBytes)

                listener.onAudioPacket(AudioPacket(pts = header.pts, isConfigPacket = header.isConfig, data = payloadBytes))
            }
        } catch (e: EOFException) {
            AppCallsLogger.d(TAG, "Stream fully read (EOF), ending normally.")
            listener.onStreamEnd(null)
        } catch (e: Exception) {
            AppCallsLogger.e(TAG, "Stream ended with error: ${e.message}", e)
            listener.onStreamEnd(e.message)
        } finally {
            runCatching { inputStream.close() }
        }
    }

    fun stop() { running = false }

    override fun close() {
        stop()
        runCatching { inputPfd.close() }
    }

    private fun readPacketHeader(stream: DataInputStream): AudioEnvelope {
        val ptsAndFlags = stream.readLong()
        val payloadSize = stream.readInt()
        return AudioEnvelope(
            rawPtsAndFlags = ptsAndFlags,
            pts = ptsAndFlags and (MEDIA_PACKET_FLAG or PACKET_FLAG_CONFIG or PACKET_FLAG_KEY_FRAME).inv(),
            isMedia = (ptsAndFlags and MEDIA_PACKET_FLAG) == 0L,
            isConfig = (ptsAndFlags and PACKET_FLAG_CONFIG) != 0L,
            isKeyFrame = (ptsAndFlags and PACKET_FLAG_KEY_FRAME) != 0L,
            payloadSize = payloadSize
        )
    }
}
