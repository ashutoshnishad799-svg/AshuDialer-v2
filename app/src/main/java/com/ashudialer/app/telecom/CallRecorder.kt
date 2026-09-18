package com.ashudialer.app.telecom

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class RecordingMode { VOICE_CALL, VOICE_UPLINK_DOWNLINK, VOICE_COMMUNICATION, MICROPHONE, APP_CALL, FAILED }

data class RecordingResult(
    val file: File,
    val mode: RecordingMode,
    val callerLabel: String
)

private const val RECORDINGS_FOLDER_NAME = "Ashu Dialer"

class CallRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var currentMode: RecordingMode = RecordingMode.FAILED
    private var currentCallerLabel: String = ""
    private var recordingStartedAtMs: Long = 0L

    // Remaining untried (mode, source) candidates from the same start()
    // call, so a real-world-observed failure mode can still recover
    // mid-call: on at least one confirmed device, VOICE_UPLINK opens and
    // starts successfully (passes every check tryStartSource runs, so it
    // is NOT a false "granted" the way the setup screen's idle probe can
    // be) but produces genuine digital silence for the call's entire
    // duration, with real audio only bleeding through for about a second
    // during the AudioManager mode transition as the call tears down -
    // confirmed by directly listening to an affected recording. A source
    // that "opens fine" is therefore not sufficient proof it will
    // actually carry audio on every device, which is why this restart
    // path exists alongside (not instead of) BCR/SKVALEX's simpler
    // commit-to-one-source model: neither of those was ever confirmed
    // against this exact opens-but-silent-until-teardown failure mode.
    private var remainingCandidates: List<Pair<RecordingMode, Int>> = emptyList()
    private var recordingDir: File? = null
    private var recordingSafeLabel: String = "call"
    private var recordingTimestamp: String = ""
    // True once a mid-call restart has already happened for this
    // recording - capped at one restart per call (not an unbounded
    // retry loop) so a device where every single source is genuinely
    // silent (a real vendor block, not this specific opens-but-silent
    // bug) doesn't cycle through sources indefinitely, discarding
    // several seconds of audio at each switch, for no eventual gain.
    private var hasRestartedOnce = false

    // Tracks whether every amplitude poll since start() has read silent,
    // BCR-style: BCR's own RecorderThread checks every single decoded PCM
    // sample for the whole call and only flags PureSilenceException if
    // literally all of them were exactly 0 - not a fixed short probe
    // before starting, and not a mid-call source switch. This mirrors
    // that at the whole-recording level using MediaRecorder's own
    // getMaxAmplitude() instead of raw PCM (this app uses MediaRecorder,
    // not AudioRecord+manual encoding the way BCR does), or a device
    // that opens a source successfully but the OEM audio policy silently
    // routes zero signal into it for the source's entire lifetime, this
    // still results in wasEntirelySilent staying true. The first couple
    // of polls are skipped in InCallActivity (elapsedSeconds > 2) before
    // this is ever updated, for the same reason BCR doesn't judge the
    // very first buffer - the encoder hasn't flushed a first real frame
    // yet.
    private var wasEntirelySilent = true

    val isRecording: Boolean
        get() = recorder != null

    fun currentMode(): RecordingMode? = currentMode.takeIf { recorder != null && it != RecordingMode.FAILED }

    fun currentCallerLabel(): String? = currentCallerLabel.takeIf { recorder != null && it.isNotBlank() }

    /**
     * Records the whole call from a single source, chosen once at the
     * start and kept for the call's full duration - BCR does the same
     * (its `sources` list is fixed for the lifetime of one RecorderThread,
     * set once in init{} and never reconsidered mid-call). No mid-call
     * source switching: an earlier version of this app tried that, but
     * neither BCR nor SKVALEX (two independently working, widely-used
     * rooted call recorders, one of them proven on MIUI/HyperOS devices)
     * do any such thing - BCR's own README instead documents the honest
     * fallback for a device that plain doesn't expose VOICE_CALL: the
     * person manually turns on speakerphone and lets MIC pick it up,
     * there's no in-app auto-detection magic beyond that.
     *
     * Source priority, closest to what BCR's own AudioSource enum offers
     * (VOICE_CALL, then the uplink/downlink pair, matching
     * VOICE_UPLINK_DOWNLINK) plus this app's own MIC last-resort, since
     * this app uses MediaRecorder's single-source API rather than BCR's
     * dual-AudioRecord stereo interleaving and so can't combine uplink +
     * downlink into one true two-way stream the way BCR's
     * VOICE_UPLINK_DOWNLINK does:
     *   1. VOICE_CALL - combined both-sides stream, when the OEM audio
     *      policy exposes it to a priv-app holding CAPTURE_AUDIO_OUTPUT.
     *   2. VOICE_UPLINK - this device's own mic leg of the call.
     *   3. VOICE_DOWNLINK - the other party's leg of the call.
     *   4. MIC - the ordinary unprivileged microphone, always available,
     *      picks up whatever's audible (both sides if on speakerphone,
     *      otherwise this side only) with no CAPTURE_AUDIO_OUTPUT needed.
     */
    fun start(callerLabel: String): RecordingMode {
        currentCallerLabel = callerLabel
        val dir = File(context.cacheDir, "recordings_tmp").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeLabel = callerLabel.filter { it.isLetterOrDigit() }.ifBlank { "call" }

        // THE FIX for "only the last 1-2 seconds have audio": on a device
        // where e.g. VOICE_UPLINK opens fine but is genuinely silent for
        // the whole call (see restartOnSustainedSilence's doc comment),
        // every short call used to record silence start-to-finish because
        // the mid-call restart takes ~8+ seconds to trigger and a short
        // call ends before that ever fires. Once a source has been
        // confirmed silent-for-the-whole-call on THIS device (persisted
        // below in confirmSourceKnownSilent), skip straight past it on
        // every future call instead of re-trying and re-losing the first
        // several seconds (or the whole call) to it again.
        val knownSilentSources = loadKnownSilentSources(context)
        val candidates = listOf(
            RecordingMode.VOICE_CALL to MediaRecorder.AudioSource.VOICE_CALL,
            RecordingMode.VOICE_UPLINK_DOWNLINK to MediaRecorder.AudioSource.VOICE_UPLINK,
            RecordingMode.VOICE_COMMUNICATION to MediaRecorder.AudioSource.VOICE_DOWNLINK,
            RecordingMode.MICROPHONE to MediaRecorder.AudioSource.MIC,
        ).distinctBy { it.second }
            .let { all ->
                val filtered = all.filterNot { it.second in knownSilentSources }
                // Never filter down to nothing - if every source has been
                // marked silent (or the list is empty for some reason),
                // fall back to the full list rather than recording nothing.
                filtered.ifEmpty { all }
            }

        wasEntirelySilent = true
        hasRestartedOnce = false
        recordingDir = dir
        recordingSafeLabel = safeLabel
        recordingTimestamp = timestamp

        for ((index, candidate) in candidates.withIndex()) {
            val (mode, source) = candidate
            val file = File(dir, "${safeLabel}_${timestamp}_${mode.name.lowercase(Locale.US)}.m4a")
            if (tryStartSource(file, source)) {
                outputFile = file
                currentMode = mode
                remainingCandidates = candidates.drop(index + 1)
                Log.i("CallRecorder", "recording started using $mode")
                return mode
            }
        }

        currentMode = RecordingMode.FAILED
        outputFile = null
        recordingStartedAtMs = 0L
        currentCallerLabel = ""
        remainingCandidates = emptyList()
        return currentMode
    }

    fun elapsedSeconds(): Int {
        val started = recordingStartedAtMs
        return if (started <= 0L) 0 else ((SystemClock.elapsedRealtime() - started) / 1000L).toInt().coerceAtLeast(0)
    }

    /**
     * Live amplitude reading from the encoder, used by InCallActivity's
     * existing recording-status poll for the in-call "recording looks
     * silent" warning. Also updates wasEntirelySilent for stop()'s
     * end-of-call BCR-style silence flag - see that field's doc comment.
     */
    fun currentAmplitude(): Int {
        val amplitude = try {
            recorder?.maxAmplitude ?: 0
        } catch (_: Throwable) {
            0
        }
        if (amplitude > 0) wasEntirelySilent = false
        return amplitude
    }

    /**
     * Call this once sustained silence has been observed (InCallActivity
     * already tracks this via quietPollsInARow for the "recording looks
     * silent" warning - this reuses that same signal rather than each
     * side implementing its own silence-duration timer). If the current
     * source has genuinely never produced any signal since start() (not
     * just this poll - wasEntirelySilent covers the whole recording so
     * far) and a restart hasn't already happened this call, stops the
     * current recorder, discards its silent-so-far file, and starts the
     * next untried candidate from the same fallback chain start() built -
     * continued, not restarted from VOICE_CALL again, since a source
     * already proven silent this call has no reason to be retried later
     * in the same call.
     *
     * Returns the new mode if a restart happened, else null (nothing to
     * do, or already restarted once this call, or the current source
     * did produce some signal at some point so there's no reason to
     * believe switching would help).
     *
     * This exists specifically because a source can pass tryStartSource
     * (opens, initializes, produces a non-empty file in the first 200ms)
     * and still turn out to carry no real audio for the rest of the call
     * on some devices - confirmed directly: VOICE_UPLINK opening
     * successfully but recording nothing but silence until literally the
     * last ~1 second of the call, when the AudioManager mode transition
     * during teardown let a brief window of real signal through. BCR and
     * SKVALEX's "commit to one source" model assumes a source that opens
     * is a source that works - true on the hardware those were verified
     * against, not proven true here, which is why this app adds a capped,
     * one-time recovery path on top of that same model rather than
     * abandoning it outright.
     */
    fun restartOnSustainedSilence(): RecordingMode? {
        if (recorder == null) return null
        if (hasRestartedOnce) return null
        if (!wasEntirelySilent) return null

        // The source that never produced a single sample of signal since
        // start() is confirmed silent-for-the-whole-call on this device -
        // remember it so start() skips it on every future call instead of
        // losing the first several seconds (or a whole short call) to it
        // again. This is the persisted half of the fix for "only the last
        // 1-2 seconds have audio" - see start()'s knownSilentSources.
        currentSourceForCurrentMode()?.let { markSourceKnownSilent(context, it) }

        val next = remainingCandidates.firstOrNull() ?: return null
        val dir = recordingDir ?: return null

        Log.w("CallRecorder", "source $currentMode produced no signal at all since start() - restarting on ${next.first}")

        try { recorder?.stop() } catch (_: Throwable) {}
        try { recorder?.release() } catch (_: Throwable) {}
        recorder = null
        outputFile?.let { try { it.delete() } catch (_: Throwable) {} }

        val (mode, source) = next
        val file = File(dir, "${recordingSafeLabel}_${recordingTimestamp}_${mode.name.lowercase(Locale.US)}_retry.m4a")
        hasRestartedOnce = true
        remainingCandidates = remainingCandidates.drop(1)

        return if (tryStartSource(file, source)) {
            outputFile = file
            currentMode = mode
            wasEntirelySilent = true
            Log.i("CallRecorder", "restarted recording using $mode")
            mode
        } else {
            currentMode = RecordingMode.FAILED
            outputFile = null
            null
        }
    }

    /** Maps the current RecordingMode back to its MediaRecorder.AudioSource int, for persisting a confirmed-silent source. */
    private fun currentSourceForCurrentMode(): Int? = when (currentMode) {
        RecordingMode.VOICE_CALL -> MediaRecorder.AudioSource.VOICE_CALL
        RecordingMode.VOICE_UPLINK_DOWNLINK -> MediaRecorder.AudioSource.VOICE_UPLINK
        RecordingMode.VOICE_COMMUNICATION -> MediaRecorder.AudioSource.VOICE_DOWNLINK
        RecordingMode.MICROPHONE -> MediaRecorder.AudioSource.MIC
        // APP_CALL recordings (WhatsApp/Telegram, via the :appcalls module) never go through
        // this file's MediaRecorder or silent-source-tracking system at all - that whole
        // mechanism is specific to native telephony AudioSource values. No mapping applies,
        // same as FAILED.
        RecordingMode.APP_CALL -> null
        RecordingMode.FAILED -> null
    }

    private fun tryStartSource(file: File, source: Int): Boolean {
        // 16000 Hz first: this is BCR's own default sample rate for call
        // recording (confirmed directly from BCR's source - WaveFormat's
        // SampleRateInfo default is 16_000u, and AAC/M4A formats fall
        // back to the same default when no rate is explicitly chosen).
        // 48000 and 8000 remain as fallbacks for devices/sources whose
        // HAL prefers a different native rate - MediaRecorder.prepare()
        // itself fails cleanly if a rate genuinely isn't supported, so
        // trying a short list here costs nothing on a device where the
        // first one already works.
        val sampleRateCandidates = listOf(16000, 48000, 8000)
        for (sampleRate in sampleRateCandidates) {
            var r: MediaRecorder? = null
            try {
                r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }
                r.setAudioSource(source)
                r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                r.setAudioChannels(1)
                r.setAudioSamplingRate(sampleRate)
                r.setAudioEncodingBitRate(64000)
                r.setOutputFile(file.absolutePath)
                r.prepare()
                r.start()
                SystemClock.sleep(200)
                if (!file.exists() || file.length() <= 0L) throw IllegalStateException("recorder produced no output")
                recorder = r
                recordingStartedAtMs = SystemClock.elapsedRealtime()
                return true
            } catch (e: Throwable) {
                Log.w("CallRecorder", "audio source $source at ${sampleRate}Hz unavailable", e)
                try { r?.reset() } catch (_: Throwable) {}
                try { r?.release() } catch (_: Throwable) {}
                try { file.delete() } catch (_: Throwable) {}
            }
        }

        // Absolute bare-minimum fallback, MIC only: let MediaRecorder pick
        // every audio parameter itself (no explicit rate/channels/bitrate
        // at all) instead of any candidate above. Only reached if MIC
        // already failed every explicit rate tried above too - a genuine
        // last resort so recording can never end up completely
        // unavailable because of a rate this app guessed wrong, on a
        // source that fundamentally should always be recordable.
        if (source == MediaRecorder.AudioSource.MIC) {
            var r: MediaRecorder? = null
            try {
                r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }
                r.setAudioSource(source)
                r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                r.setOutputFile(file.absolutePath)
                r.prepare()
                r.start()
                SystemClock.sleep(200)
                if (!file.exists() || file.length() <= 0L) throw IllegalStateException("recorder produced no output")
                recorder = r
                recordingStartedAtMs = SystemClock.elapsedRealtime()
                return true
            } catch (e: Throwable) {
                Log.w("CallRecorder", "MIC bare-default fallback also failed", e)
                try { r?.reset() } catch (_: Throwable) {}
                try { r?.release() } catch (_: Throwable) {}
                try { file.delete() } catch (_: Throwable) {}
            }
        }
        return false
    }

    fun stop(): RecordingResult? {
        val r = recorder ?: return null
        return try {
            // Covers the short-call case: a call that ends before the ~8s
            // in-call restart threshold ever fires (see InCallActivity's
            // quietPollsInARow) still finished with zero signal captured
            // the entire time on the current source. Mark it now so the
            // *next* call skips this source from the start rather than
            // losing its first several seconds to the same dead source
            // yet again - same persisted list restartOnSustainedSilence
            // writes to, just triggered from the other exit path.
            if (wasEntirelySilent) {
                currentSourceForCurrentMode()?.let { markSourceKnownSilent(context, it) }
            }
            try { r.stop() } catch (e: RuntimeException) { Log.w("CallRecorder", "Recorder stop failed", e) }
            r.release()
            recorder = null
            val tempFile = outputFile ?: return null
            val publicFile = moveToPublicStorage(tempFile)
            recordingStartedAtMs = 0L
            // THE FIX for recordings that seem to vanish / "save fake": if
            // moveToPublicStorage() above failed for any reason (MediaStore
            // insert() returning null, a permission hiccup, disk pressure,
            // etc.), this used to fall back to `tempFile` directly - which
            // lives under context.cacheDir. That's invisible to
            // listRecordings() below (it only ever scans MediaStore and the
            // permanent context.filesDir/recordings/ legacy folder, never
            // cacheDir), and Android is free to purge cache-directory
            // contents at any time under storage pressure with zero
            // warning. The net effect was a recording that genuinely
            // existed right after the call, yet could never be found in the
            // app's own Recordings list and could disappear on its own -
            // indistinguishable from "did this even save anything real."
            // Falling back to a permanent, app-owned directory that
            // listRecordings() actually knows about turns that silent,
            // unrecoverable failure into a recording the person can still
            // find and play, even on the rare path where the normal
            // MediaStore save didn't go through.
            val finalFile = publicFile ?: run {
                try {
                    val permanentDir = File(context.filesDir, "recordings").apply { mkdirs() }
                    val destination = File(permanentDir, tempFile.name)
                    tempFile.copyTo(destination, overwrite = true)
                    tempFile.delete()
                    Log.w("CallRecorder", "Public storage save failed; kept recording at ${destination.absolutePath} instead")
                    destination
                } catch (e: Exception) {
                    Log.w("CallRecorder", "Fallback save to permanent app storage also failed", e)
                    tempFile
                }
            }
            val result = RecordingResult(finalFile, currentMode, currentCallerLabel)
            currentMode = RecordingMode.FAILED
            outputFile = null
            currentCallerLabel = ""
            remainingCandidates = emptyList()
            recordingDir = null
            hasRestartedOnce = false
            result
        } catch (e: Exception) {
            Log.w("CallRecorder", "Recording stop failed", e)
            releaseQuietly()
            null
        }
    }

    private fun moveToPublicStorage(tempFile: File): File? {
        return try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, tempFile.name)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/$RECORDINGS_FOLDER_NAME")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
            }
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val itemUri = resolver.insert(collection, values) ?: return null
            resolver.openOutputStream(itemUri)?.use { out -> tempFile.inputStream().use { input -> input.copyTo(out) } } ?: return null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(itemUri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            }
            val legacyFile = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                File(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), RECORDINGS_FOLDER_NAME).apply { mkdirs() }, tempFile.name)
            } else null
            if (legacyFile != null) {
                resolver.openInputStream(itemUri)?.use { input -> legacyFile.outputStream().use { out -> input.copyTo(out) } }
            }
            tempFile.delete()
            legacyFile ?: File(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), RECORDINGS_FOLDER_NAME), tempFile.name)
        } catch (e: Exception) {
            Log.w("CallRecorder", "Couldn't move recording to public storage", e)
            null
        }
    }

    private fun releaseQuietly() {
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        recordingStartedAtMs = 0L
        outputFile = null
        currentMode = RecordingMode.FAILED
        currentCallerLabel = ""
        remainingCandidates = emptyList()
        recordingDir = null
        hasRestartedOnce = false
    }

    companion object {
        private const val PRIVATE_SPACE_RECORDINGS_DIR = "private_space_recordings"
        private const val SILENT_SOURCES_PREFS = "call_recorder_silent_sources"
        private const val SILENT_SOURCES_KEY = "known_silent_audio_sources"

        /**
         * Per-device memory of which MediaRecorder.AudioSource ints have
         * been directly confirmed to produce zero signal for an entire
         * call on this hardware (see restartOnSustainedSilence/stop()).
         * Stored as a small SharedPreferences set of source-int strings -
         * deliberately separate from AppSettingsRepository's DataStore
         * since this is an internal recovery cache the person never sees
         * or edits, not a user-facing setting.
         *
         */
        internal fun loadKnownSilentSources(context: Context): Set<Int> {
            return try {
                context.getSharedPreferences(SILENT_SOURCES_PREFS, Context.MODE_PRIVATE)
                    .getStringSet(SILENT_SOURCES_KEY, emptySet())
                    ?.mapNotNull { it.toIntOrNull() }
                    ?.toSet()
                    ?: emptySet()
            } catch (e: Exception) {
                Log.w("CallRecorder", "Failed to load known-silent sources", e)
                emptySet()
            }
        }

        private fun markSourceKnownSilent(context: Context, source: Int) {
            try {
                val prefs = context.getSharedPreferences(SILENT_SOURCES_PREFS, Context.MODE_PRIVATE)
                val current = prefs.getStringSet(SILENT_SOURCES_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
                if (current.add(source.toString())) {
                    prefs.edit().putStringSet(SILENT_SOURCES_KEY, current).apply()
                    Log.i("CallRecorder", "audio source $source confirmed silent on this device - will be skipped on future calls")
                }
            } catch (e: Exception) {
                Log.w("CallRecorder", "Failed to persist known-silent source", e)
            }
        }

        /** Lets Settings offer a "forget learned silent sources" reset if the person's audio setup ever changes (new ROM, root state, etc). */
        fun clearKnownSilentSources(context: Context) {
            try {
                context.getSharedPreferences(SILENT_SOURCES_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
            } catch (e: Exception) {
                Log.w("CallRecorder", "Failed to clear known-silent sources", e)
            }
        }

        fun moveToPrivateSpace(context: Context, publicFile: File): File? {
            return try {
                val privateDir = File(context.filesDir, PRIVATE_SPACE_RECORDINGS_DIR).apply { mkdirs() }
                val destination = File(privateDir, publicFile.name)
                publicFile.copyTo(destination, overwrite = true)

                try {
                    val resolver = context.contentResolver
                    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    }
                    resolver.delete(collection, "${MediaStore.Audio.Media.DATA} = ?", arrayOf(publicFile.absolutePath))
                } catch (e: Exception) {
                    Log.w("CallRecorder", "Couldn't remove MediaStore row for moved recording", e)
                }
                if (publicFile.exists()) publicFile.delete()

                destination
            } catch (e: Exception) {
                Log.w("CallRecorder", "Failed to move recording to Private Space", e)
                null
            }
        }

        fun moveOutOfPrivateSpace(context: Context, privateFile: File): File? {
            return try {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, privateFile.name)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/$RECORDINGS_FOLDER_NAME")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Audio.Media.IS_PENDING, 1)
                    }
                }
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                val uri = resolver.insert(collection, values) ?: return null
                resolver.openOutputStream(uri)?.use { out -> privateFile.inputStream().use { it.copyTo(out) } }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                privateFile.delete()

                queryMediaStoreRecordings(context).firstOrNull { it.name == privateFile.name }
            } catch (e: Exception) {
                Log.w("CallRecorder", "Failed to move recording out of Private Space", e)
                null
            }
        }

        fun listPrivateSpaceRecordings(context: Context): List<File> {
            val dir = File(context.filesDir, PRIVATE_SPACE_RECORDINGS_DIR)
            if (!dir.exists()) return emptyList()
            return (dir.listFiles()?.toList() ?: emptyList()).sortedByDescending { it.lastModified() }
        }

        fun wipeAllPrivateSpaceRecordings(context: Context) {
            val dir = File(context.filesDir, PRIVATE_SPACE_RECORDINGS_DIR)
            if (!dir.exists()) return
            dir.listFiles()?.forEach { it.delete() }
        }

        fun listRecordings(context: Context): List<File> {
            val fromMediaStore = queryMediaStoreRecordings(context)

            val legacyDir = File(context.filesDir, "recordings")
            val fromLegacyStorage = if (legacyDir.exists()) {
                legacyDir.listFiles()?.toList() ?: emptyList()
            } else emptyList()

            return (fromMediaStore + fromLegacyStorage)
                .distinctBy { it.absolutePath }
                .sortedByDescending { it.lastModified() }
        }

        private fun queryMediaStoreRecordings(context: Context): List<File> {
            val results = mutableListOf<File>()
            try {
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.DATA
                )
                val selection: String?
                val selectionArgs: Array<String>?
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    selection = "${MediaStore.Audio.Media.RELATIVE_PATH} = ?"
                    selectionArgs = arrayOf("Music/$RECORDINGS_FOLDER_NAME/")
                } else {
                    selection = null
                    selectionArgs = null
                }

                context.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                    val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                    while (cursor.moveToNext()) {
                        val path = if (dataCol >= 0) cursor.getString(dataCol) else null
                        if (path != null) {
                            results.add(File(path))
                        } else {
                            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                            results.add(File(File(musicDir, RECORDINGS_FOLDER_NAME), cursor.getString(nameCol)))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("CallRecorder", "MediaStore recordings query failed", e)
            }
            return results
        }

        /**
         * THE FIX for recordings that won't delete from inside the app: the
         * old version deleted via resolver.delete(collection, "DISPLAY_NAME
         * = ?", ...) - a bulk delete against the whole MediaStore audio
         * collection matched only by filename, with no RELATIVE_PATH/volume
         * constraint, and its result was never checked. If that delete
         * matched zero rows for any reason (a DISPLAY_NAME MediaStore
         * silently adjusted to avoid a collision, timing, etc.), the code
         * still fell through to a plain File.delete() - which fails
         * silently under scoped storage (Android 10+) for a file this app
         * doesn't have raw filesystem access to, since it was written via
         * MediaStore's resolver.insert()+openOutputStream() (see
         * moveToPublicStorage above), not direct file I/O. The net result:
         * deleteRecording() could return with nothing actually deleted, no
         * exception thrown, and the caller (MainActivity's deleteRecording)
         * never checked the return value either - so the file just
         * reappeared the next time the list refreshed, with no feedback
         * that anything had gone wrong.
         *
         * This version first queries for the SPECIFIC row this file
         * corresponds to (matched by DISPLAY_NAME AND RELATIVE_PATH
         * together, not filename alone) to get its real item URI, then
         * deletes that exact row - the precise, reliable form, rather than
         * a bulk delete hoping the WHERE clause matches the right thing.
         * It then verifies success by checking the file is actually gone
         * from disk afterward, rather than trusting either individual
         * call's return value - MediaStore's own delete() normally removes
         * the underlying physical file as part of removing the row, so
         * this is the one check that reflects what the person actually
         * cares about (is it gone).
         */
        fun deleteRecording(context: Context, file: File): Boolean {
            try {
                val resolver = context.contentResolver
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                val projection = arrayOf(MediaStore.Audio.Media._ID)
                val selection: String
                val selectionArgs: Array<String>
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} = ?"
                    selectionArgs = arrayOf(file.name, "Music/$RECORDINGS_FOLDER_NAME/")
                } else {
                    selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ?"
                    selectionArgs = arrayOf(file.name)
                }
                resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val itemUri = ContentUris.withAppendedId(collection, id)
                        try {
                            resolver.delete(itemUri, null, null)
                        } catch (e: Exception) {
                            Log.w("CallRecorder", "MediaStore delete failed for $itemUri", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("CallRecorder", "MediaStore query-then-delete failed", e)
            }
            // Legacy/fallback path for files not tracked in MediaStore at
            // all (pre-Q devices, or the permanent app-storage fallback
            // path in stop() above when moveToPublicStorage failed) - a
            // plain file this app's own package directory or a legacy
            // public path it wrote directly still owns.
            if (file.exists()) {
                try { file.delete() } catch (_: Exception) {}
            }
            return !file.exists()
        }
    }
}
