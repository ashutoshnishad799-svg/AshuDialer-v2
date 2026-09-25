// Adapted from ShizuCallRecorder (github.com/kitsumed/ShizuCallRecorder), GPLv3+.
package com.ashudialer.app.appcalls.scrcpy

import android.content.Context
import com.ashudialer.app.appcalls.BuildConfig
import java.security.SecureRandom

/**
 * Centralises configuration constants for interacting with scrcpy-server.
 *
 * scrcpy-server runs with `app_process` under the shell UID (2000) that Shizuku grants this
 * module access to, and exposes audio-capture capabilities normal apps cannot reach directly.
 * Invoked here with `audio=true video=false` so it acts purely as an audio source.
 *
 * References:
 *  - https://github.com/Genymobile/scrcpy/blob/master/doc/develop.md
 *  - https://github.com/Genymobile/scrcpy/blob/master/doc/audio.md
 */
object ScrcpyConfig {

    /** Injected by this module's build.gradle.kts from the bundled server jar's real version. */
    const val SCRCPY_VERSION: String = BuildConfig.SCRCPY_VERSION

    /** Injected by this module's build.gradle.kts; verified against the bundled jar's actual SHA-256 before use. */
    const val EXPECTED_SERVER_SHA256: String = BuildConfig.SCRCPY_SERVER_SHA256

    /**
     * Absolute path where the scrcpy-server jar is (or should be) extracted to on shared storage.
     *
     * Why shared storage? The shell process (UID 2000) cannot read this app's private data
     * directory, but CAN read paths under /storage/emulated/0/Android/data/ - the external
     * files directory is the one location writable by this app and readable by the shell
     * process without root.
     */
    fun getServerPath(context: Context): String {
        val folder = context.getExternalFilesDir(null)
            ?: context.externalCacheDir
            ?: throw IllegalStateException("Shared storage unavailable - cannot stage scrcpy-server.")
        return folder.absolutePath + "/appcalls-scrcpy-${SCRCPY_VERSION}-server.jar"
    }

    const val SERVER_MAIN_CLASS = "com.genymobile.scrcpy.Server"

    /** See: https://github.com/Genymobile/scrcpy/blob/master/server/src/main/java/com/genymobile/scrcpy/device/DesktopConnection.java */
    const val SERVER_SOCKET_NAME_PREFIX = "scrcpy_"

    /** scrcpy-server's fixed audio output format - see AudioConfig.java in scrcpy-server. */
    const val AUDIO_SAMPLE_RATE = 48000
    const val AUDIO_CHANNELS = 2

    /**
     * Builds the argument list passed to scrcpy-server after the version string.
     * Same shape as Ever Dialer's recorder module: caller picks the audio source and codec.
     *
     * @param socketName  8-hex-digit socket id parsed by scrcpy as Integer.parseInt(..., 16).
     * @param audioSource What to capture (voice-call, mic-voice-communication, output, ...).
     * @param audioCodec  aac or opus.
     * @param bitRate     bps; omitted from the args when <= 0.
     */
    fun buildServerArgs(
        socketName: String,
        audioSource: ScrcpyAudioSource,
        audioCodec: ScrcpyAudioCodec,
        bitRate: Int
    ): List<String> {
        val args = mutableListOf(
            SCRCPY_VERSION,
            "log_level=info",
            "video=false",
            "audio=true",
            "control=false",
            // tunnel_forward=false: scrcpy-server dials OUR LocalServerSocket, not the reverse.
            // The very first bytes on the socket are then the 4-byte codec FourCC with no dummy
            // byte in front - see DesktopConnection.java.
            "tunnel_forward=false",
            "send_dummy_byte=false",
            "scid=$socketName",
            "audio_source=${audioSource.cliKey}",
            "audio_codec=${audioCodec.cliKey}",
            "send_device_meta=false",
            "send_frame_meta=true",
            "send_stream_meta=true"
        )
        if (bitRate > 0) args.add("audio_bit_rate=$bitRate")
        return args
    }

    /** Random 8-hex-digit socket name; scrcpy-server parses it with Integer.parseInt(_, 16). */
    fun getRandomSocketName(): String =
        SecureRandom().nextInt(Int.MAX_VALUE).toString(16).padStart(8, '0')
}
