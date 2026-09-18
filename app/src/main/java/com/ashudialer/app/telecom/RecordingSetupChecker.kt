package com.ashudialer.app.telecom

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File

enum class SetupCheckResult { GRANTED, NOT_GRANTED, UNKNOWN }

/**
 * Per-source result from [RecordingSetupChecker.checkAllAudioSources] - which
 * exact MediaRecorder.AudioSource values this device's audio policy actually
 * grants right now, independent of anything CallRecorder.start() decides to
 * do with that information. failureReasons carries the last exception
 * message for any source that failed, keyed by source int, so the setup
 * screen can show *why* rather than just a red X - a plain boolean threw
 * away the one piece of information that actually explains a failure
 * (permission denied vs. HAL rejection vs. something else entirely).
 */
data class AudioSourceProbeResult(
    val voiceCall: Boolean,
    val voiceUplink: Boolean,
    val voiceDownlink: Boolean,
    val voiceCommunication: Boolean,
    val failureReasons: Map<Int, String> = emptyMap()
)

/**
 * Every check here actually probes the real thing it claims to check -
 * none of these are guesses or version-number heuristics. A false "yes"
 * would be worse than no check at all, since it would tell someone their
 * setup works when it doesn't.
 */
object RecordingSetupChecker {

    /**
     * Probes every MediaRecorder.AudioSource this app's CallRecorder might
     * fall back to (not just VOICE_CALL), so the setup screen can tell the
     * person exactly which one this device's audio policy actually grants
     * access to right now - rather than only a pass/fail on VOICE_CALL,
     * which leaves "why didn't recording work" unanswerable when
     * VOICE_CALL fails but, say, VOICE_UPLINK is fine (mic-side only) or
     * nothing at all is available (a genuine vendor audio-policy block,
     * not fixable from this app).
     *
     * THE FIX for every source except VOICE_COMMUNICATION always showing
     * red on this screen even when the priv-app permission genuinely is
     * granted: this used to require *actual non-silent signal* (checking
     * for real audio content, not just digital zeros) to count a source
     * as available. That distinction matters during a real call - see
     * CallRecorder's own history of a HAL that opens VOICE_CALL fine but
     * silently feeds it nothing but zeros - but this screen's own "Test
     * now" button explicitly runs "a one-second silent test, not a real
     * call" (see the screen's own copy), with no call active at all. With
     * no call in progress, VOICE_CALL/VOICE_UPLINK/VOICE_DOWNLINK have no
     * audio stream to carry *regardless of whether the permission is
     * granted* - there's nothing playing on either call leg to pick up.
     * VOICE_COMMUNICATION was the only one that could ever pass this way,
     * since it can bind to the ordinary microphone and picks up ambient
     * room noise even with no call active - which is exactly the "only 1
     * checkmark, always the same one" result this was producing. The
     * right question for an idle-time check is "does this device's audio
     * policy let this app's process open this source at all" (a
     * permission/access question) - not "is there audio on it right this
     * second" (a content question, which depends on a call being active).
     * probeSourceAccessGranted below checks exactly the access question.
     */
    fun checkAllAudioSources(context: Context): AudioSourceProbeResult {
        val failures = mutableMapOf<Int, String>()
        val voiceCall = probeSourceAccessGranted(MediaRecorder.AudioSource.VOICE_CALL, failures)
        val voiceUplink = probeSourceAccessGranted(MediaRecorder.AudioSource.VOICE_UPLINK, failures)
        val voiceDownlink = probeSourceAccessGranted(MediaRecorder.AudioSource.VOICE_DOWNLINK, failures)
        val voiceCommunication = probeSourceAccessGranted(MediaRecorder.AudioSource.VOICE_COMMUNICATION, failures)
        return AudioSourceProbeResult(
            voiceCall = voiceCall,
            voiceUplink = voiceUplink,
            voiceDownlink = voiceDownlink,
            voiceCommunication = voiceCommunication,
            failureReasons = failures
        )
    }

    /**
     * Opens a raw AudioRecord on [source] and checks only whether the
     * device's audio policy actually lets this app open and start that
     * source - not whether it currently carries real (non-silent) audio.
     * This is the right check for an idle-time "is access granted" probe,
     * since VOICE_CALL/VOICE_UPLINK/VOICE_DOWNLINK legitimately produce no
     * audio at all outside of an active call, independent of whether the
     * permission is granted.
     *
     * A source whose access is genuinely blocked (CAPTURE_AUDIO_OUTPUT
     * missing, or the vendor audio policy rejecting the input type
     * outright) fails here differently: either the AudioRecord
     * constructor/Builder throws (commonly a SecurityException), or the
     * resulting AudioRecord never reaches STATE_INITIALIZED, or
     * startRecording() never reaches RECORDSTATE_RECORDING - all of which
     * this treats as "not granted". A source that opens and starts
     * cleanly is treated as granted regardless of what it captures in the
     * next moment, since that part depends on whether a call happens to
     * be active, which this standalone check has no control over.
     */
    private fun probeSourceAccessGranted(source: Int, failures: MutableMap<Int, String>): Boolean {
        val rateCandidates = listOf(48000, 16000, 8000)
        val channelConfig = android.media.AudioFormat.CHANNEL_IN_MONO
        val encoding = android.media.AudioFormat.ENCODING_PCM_16BIT
        var lastFailureReason: String? = null

        for (sampleRate in rateCandidates) {
            val minBufSize = android.media.AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
            if (minBufSize <= 0) {
                lastFailureReason = "getMinBufferSize returned $minBufSize at ${sampleRate}Hz"
                continue
            }

            var audioRecord: android.media.AudioRecord? = null
            try {
                val record = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    android.media.AudioRecord.Builder()
                        .setAudioSource(source)
                        .setAudioFormat(
                            android.media.AudioFormat.Builder()
                                .setEncoding(encoding)
                                .setSampleRate(sampleRate)
                                .setChannelMask(channelConfig)
                                .build()
                        )
                        .setBufferSizeInBytes(minBufSize * 4)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    android.media.AudioRecord(source, sampleRate, channelConfig, encoding, minBufSize * 4)
                }
                audioRecord = record

                if (record.state != android.media.AudioRecord.STATE_INITIALIZED) {
                    lastFailureReason = "AudioRecord.state = ${record.state} (not STATE_INITIALIZED) at ${sampleRate}Hz"
                    continue
                }

                record.startRecording()
                if (record.recordingState != android.media.AudioRecord.RECORDSTATE_RECORDING) {
                    lastFailureReason = "recordingState = ${record.recordingState} (not RECORDSTATE_RECORDING) at ${sampleRate}Hz"
                    continue
                }

                // Reached RECORDSTATE_RECORDING with no exception - the
                // audio policy let this app open and start the source.
                // That is access granted, whether or not a call happens
                // to be feeding it real audio at this exact moment.
                return true
            } catch (e: Throwable) {
                lastFailureReason = "${e.javaClass.simpleName}: ${e.message} at ${sampleRate}Hz"
                Log.i("RecordingSetupChecker", "access probe for source $source at ${sampleRate}Hz failed", e)
            } finally {
                try { audioRecord?.stop() } catch (_: Throwable) {}
                try { audioRecord?.release() } catch (_: Throwable) {}
            }
        }
        lastFailureReason?.let { failures[source] = it }
        return false
    }

    /**
     * Attempts a real, very short MediaRecorder session on
     * AudioSource.VOICE_CALL. This is the same source CallRecorder.kt uses
     * during an actual call - the only difference is this can be run at any
     * time (not just mid-call) purely to report whether access to the
     * source is granted at all, since that grant either exists for this
     * app or it doesn't, regardless of whether a call is active. A
     * short-lived probe file is written to the app's own cache and deleted
     * immediately after. Same access-vs-content distinction as
     * probeSourceAccessGranted above: this reports whether MediaRecorder
     * could open/start on VOICE_CALL, not whether it captured real audio,
     * since no call being active means there's nothing to capture either
     * way.
     */
    fun checkVoiceCallSourceAvailable(context: Context): SetupCheckResult {
        val probeFile = File(context.cacheDir, "recording_setup_probe.m4a")
        var recorder: MediaRecorder? = null
        return try {
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_CALL)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioChannels(1)
            recorder.setAudioSamplingRate(16000)
            recorder.setOutputFile(probeFile.absolutePath)
            recorder.prepare()
            recorder.start()
            android.os.SystemClock.sleep(150)
            // File existing at all - even a header-only, near-zero-length
            // file from 150ms of silence - means MediaRecorder was
            // allowed to open and run on this source. Requiring "enough"
            // bytes to imply real audio content would reintroduce the
            // same access-vs-content mixup described on
            // checkAllAudioSources above.
            val ok = probeFile.exists()
            if (ok) SetupCheckResult.GRANTED else SetupCheckResult.NOT_GRANTED
        } catch (e: Throwable) {
            Log.i("RecordingSetupChecker", "VOICE_CALL source not available: ${e.message}")
            SetupCheckResult.NOT_GRANTED
        } finally {
            try { recorder?.stop() } catch (_: Throwable) {}
            try { recorder?.release() } catch (_: Throwable) {}
            try { probeFile.delete() } catch (_: Throwable) {}
        }
    }


    /**
     * True only if this app is actually installed at the priv-app path the
     * Magisk module places it at - i.e. the Magisk module is genuinely
     * active, not just that the person once flashed it. Reading this path
     * requires no special permission; it's a normal filesystem read
     * available to any app, since /system is world-readable.
     *
     * IMPORTANT LIMITATION this check cannot see past: a file sitting at a
     * /priv-app/ or /system/app/ path does NOT by itself guarantee
     * PackageManager actually treats this install as privileged.
     * Signature mismatches, a stale APK inside the Magisk module zip (built
     * from an older version than whatever is actually installed/sideloaded
     * right now), or a Magic Mount ordering issue can all leave the file
     * sitting at the right path while PackageManager still denies the
     * privileged permissions the priv-app XML tries to grant. This is
     * exactly why hasCaptureAudioOutputPermission below exists as a
     * separate, more authoritative check - "is the file at the right path"
     * and "does PackageManager actually consider the permission granted"
     * are two different questions, and this method only answers the first
     * one.
     */
    fun isRunningAsPrivApp(context: Context): Boolean {
        return try {
            val path = context.applicationInfo.sourceDir ?: return false
            path.contains("/priv-app/") || path.contains("/system/app/")
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Asks PackageManager directly whether this app's own install actually
     * holds CAPTURE_AUDIO_OUTPUT right now - the single permission that
     * gates VOICE_CALL/VOICE_UPLINK/VOICE_DOWNLINK. This is the
     * authoritative answer isRunningAsPrivApp cannot give: a green
     * "Installed as system app" only means a file exists at a priv-app
     * path, not that PackageManager granted this specific permission to
     * it. If this comes back false while isRunningAsPrivApp is true, the
     * file is at the right path but the grant itself failed - a mismatch
     * between the APK Magisk actually mounted and what this app expects,
     * a signature check failing, or Magisk's Magic Mount not fully taking
     * effect for this file are the most common real-world causes, and no
     * app-level fix in this app's own code can force a grant PackageManager
     * itself is refusing to hand out at install/mount time.
     */
    fun hasCaptureAudioOutputPermission(context: Context): Boolean {
        return try {
            ContextCompat.checkSelfPermission(context, "android.permission.CAPTURE_AUDIO_OUTPUT") ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Shizuku is a separate app the person installs themselves; this app
     * never bundles or auto-installs it. Detecting it only checks whether
     * it's present on the device - this app does not request Shizuku
     * permission or send it any commands from this check.
     */
    fun isShizukuInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("moe.shizuku.manager", 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean =
        OemPermissionHelper.isAccessibilityServiceEnabled(context)

    /**
     * Whether the person has granted this app Notification Access (Settings > Notification
     * access), which AppCallNotificationListenerService (in the :appcalls module) needs to
     * detect WhatsApp/Telegram calls - a separate system permission from Shizuku, since one
     * grants shell-level audio capture and the other only lets the app read notification
     * content. isNotificationListenerAccessGranted is the real system API for this (added API
     * 27), rather than parsing the enabled_notification_listeners Settings.Secure string by
     * hand, which is the older/deprecated way of checking the same thing.
     */
    fun isAppCallNotificationAccessGranted(context: Context): Boolean {
        return try {
            val notificationManager = context.getSystemService(android.app.NotificationManager::class.java)
            val component = android.content.ComponentName(context, "com.ashudialer.app.appcalls.AppCallNotificationListenerService")
            notificationManager?.isNotificationListenerAccessGranted(component) ?: false
        } catch (_: Exception) {
            false
        }
    }
}
