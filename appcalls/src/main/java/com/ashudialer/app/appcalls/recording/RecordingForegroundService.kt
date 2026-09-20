// Structure adapted from ShizuCallRecorder's RecordingForegroundService (github.com/kitsumed/ShizuCallRecorder), GPLv3+.
package com.ashudialer.app.appcalls.recording

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.ashudialer.app.appcalls.AppCallPipelineException
import com.ashudialer.app.appcalls.AppCallRecordingEngine
import com.ashudialer.app.appcalls.AppCallsLogger
import com.ashudialer.app.appcalls.IShellService
import com.ashudialer.app.appcalls.ShizukuConnectionManager
import com.ashudialer.app.appcalls.scrcpy.ScrcpyAudioSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * Long-running foreground service that owns one recording pipeline at a time.
 *
 * States: Standby (call in progress, not recording) -> Starting (talking to Shizuku) -> Active
 * (capturing audio). Commands arrive as Intent actions; the service is never bound.
 *
 * Android 14+ requires a declared foreground-service type: specialUse (call recording), with
 * dataSync as the type on Android 11-13, exactly like Ever Dialer's recorder.
 */
class RecordingForegroundService : Service() {

    companion object {
        private const val TAG = "AppCalls:RecService"

        const val ACTION_START_RECORDING = "com.ashudialer.app.appcalls.START_RECORDING"
        const val ACTION_STANDBY = "com.ashudialer.app.appcalls.STANDBY"
        const val ACTION_STOP_RECORDING = "com.ashudialer.app.appcalls.STOP_RECORDING"
        const val ACTION_PAUSE_RECORDING = "com.ashudialer.app.appcalls.PAUSE_RECORDING"
        const val ACTION_RESUME_RECORDING = "com.ashudialer.app.appcalls.RESUME_RECORDING"
        /** Sent by the "Record" button in the standby notification, or by the in-call record button. */
        const val ACTION_MANUAL_START = "com.ashudialer.app.appcalls.MANUAL_START_RECORDING"

        /** When true for one command, the visible notification is suppressed (incoming call still ringing). */
        const val EXTRA_SUPPRESS_NOTIFICATION = "com.ashudialer.app.appcalls.EXTRA_SUPPRESS_NOTIFICATION"

        @Volatile
        var isRunning: Boolean = false
            private set

        /** Live state for UI (in-call screen shows a recording indicator from this). */
        @Volatile
        var isRecordingNow: Boolean = false
            private set

        @Volatile
        var startedAtElapsedMs: Long = 0L
            private set
    }

    private lateinit var prefs: RecordingPrefs
    private lateinit var notifications: RecordingNotificationHelper
    private lateinit var shizuku: ShizukuConnectionManager

    private var shellService: IShellService? = null

    /**
     * The (possibly still-connecting) bind to the Shizuku shell service. There is only ever ONE of
     * these per service instance. Binding is the slowest part of starting a recording (Shizuku spawns
     * a whole new process for it), so it is started as early as possible - when a call starts
     * ringing - and [handleStart] simply waits on the same [kotlinx.coroutines.Deferred] instead of
     * binding again. Sharing one Deferred also makes a second bind impossible:
     * ShizukuConnectionManager.getShellService() overwrites its stored connection on every call, so
     * two overlapping binds would leak the first one.
     */
    private var shellServiceBind: kotlinx.coroutines.Deferred<IShellService>? = null

    // Set when the current bind attempt ended in an error, so it is not reused (see below). Tracked with
    // a plain flag instead of Deferred.getCompletionExceptionOrNull(), which is an experimental API
    // that would need an opt-in this module does not have.
    @Volatile
    private var shellServiceBindFailed = false

    private fun bindShellServiceOnce(): kotlinx.coroutines.Deferred<IShellService> {
        val existing = shellServiceBind
        // Reuse a bind that is still connecting or that succeeded. One that FAILED (e.g. Shizuku was
        // not running yet while the call was ringing) or was cancelled must not stick, or every later
        // start would fail with that same old error.
        if (existing != null && !existing.isCancelled && !shellServiceBindFailed) return existing

        shellServiceBindFailed = false
        val created = serviceScope.async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try {
                if (!ShizukuConnectionManager.waitForServer()) throw IllegalStateException("Shizuku is not running")
                shizuku.getShellService()
            } catch (e: Throwable) {
                shellServiceBindFailed = true
                throw e
            }
        }
        shellServiceBind = created
        created.start()
        return created
    }

    /** Called when a call starts ringing: begin the slow bind now so it is ready at answer time. */
    private fun prewarmShellService() {
        if (shellService != null || !ShizukuConnectionManager.isAvailable() || !ShizukuConnectionManager.hasPermission()) return
        AppCallsLogger.d(TAG, "Pre-warming the Shizuku shell service while the call is ringing")
        bindShellServiceOnce()
    }

    private var tempFile: File? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile
    private var state: RecordingServiceState = RecordingServiceState.Standby(null)
        set(value) {
            if (field != value) {
                field = value
                isRecordingNow = value is RecordingServiceState.Active
                refreshNotification()
            }
        }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        prefs = RecordingPrefs(this)
        notifications = RecordingNotificationHelper(this)
        notifications.createChannels()
        shizuku = ShizukuConnectionManager(this) {
            AppCallsLogger.w(TAG, "Shizuku connection lost.")
            if (state is RecordingServiceState.Active) {
                notifications.showError("Shizuku stopped while recording. The audio recorded so far was saved.")
                finishSessionAndStop()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Master switch: nothing runs, not even standby, unless the user turned recording on.
        if (!prefs.callRecordingEnabled) {
            AppCallsLogger.d(TAG, "Call recording is switched off - stopping.")
            satisfyForegroundRequirement()
            finishSessionAndStop()
            return START_NOT_STICKY
        }

        val action = intent?.action
        val incoming = readSession(intent)
        val current = incoming ?: state.session

        // startForegroundService() obliges us to call startForeground() quickly.
        val suppress = intent?.getBooleanExtra(EXTRA_SUPPRESS_NOTIFICATION, false) == true
        refreshNotification(suppress)

        when (action) {
            ACTION_START_RECORDING, ACTION_MANUAL_START -> handleStart(current ?: manualSession())
            ACTION_STANDBY -> {
                state = RecordingServiceState.Standby(current)
                // Warm Shizuku up early so it is ready by the time recording actually starts.
                if (prefs.shizukuAutoManage && !prefs.shizukuStartOnRecordOnly) tryStartShizukuServer()
                // Only worth binding when this call is actually going to be recorded (auto-record on
                // for that direction, or an app call that starts recording by itself). A call the
                // person records by tapping the button later just binds at that moment as before.
                if (current != null && wantsAutoRecord(current)) prewarmShellService()
            }
            ACTION_PAUSE_RECORDING -> (state as? RecordingServiceState.Active)?.let {
                it.engine.isPaused = true; state = it.copy(isPaused = true)
            }
            ACTION_RESUME_RECORDING -> (state as? RecordingServiceState.Active)?.let {
                it.engine.isPaused = false; state = it.copy(isPaused = false)
            }
            ACTION_STOP_RECORDING -> finishSessionAndStop()
        }
        return START_NOT_STICKY
    }

    /** True when this session will start recording on its own (so a pre-warm will be used, not wasted). */
    private fun wantsAutoRecord(session: RecordingSession): Boolean = when {
        session.isAppCall -> true                       // WhatsApp/Telegram/... start recording by themselves
        session.direction == CallDirection.INCOMING -> prefs.autoRecordIncoming
        else -> prefs.autoRecordOutgoing
    }

    private fun readSession(intent: Intent?): RecordingSession? {
        intent ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(RecordingSession.EXTRA_SESSION, RecordingSession::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(RecordingSession.EXTRA_SESSION)
        }
    }

    /** A user tapped Record with no call metadata (e.g. after the service restarted): assume outgoing, unknown number. */
    private fun manualSession() = RecordingSession(phoneNumber = null, direction = CallDirection.OUTGOING)

    private fun handleStart(session: RecordingSession) {
        if (state is RecordingServiceState.Active || state is RecordingServiceState.Starting) {
            AppCallsLogger.w(TAG, "Start ignored: a session is already running.")
            return
        }
        state = RecordingServiceState.Starting(session)
        if (prefs.shizukuAutoManage) tryStartShizukuServer()

        serviceScope.launch {
            try {
                val service = shellService ?: bindShellServiceOnce().await().also { shellService = it }
                startPipeline(service, session)
            } catch (e: SecurityException) {
                AppCallsLogger.e(TAG, "Shizuku permission denied", e)
                notifications.showError("Shizuku permission was not granted to Ashu Dialer. Open Call recording settings and grant it.")
                finishSessionAndStop()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppCallsLogger.e(TAG, "Could not start recording", e)
                notifications.showError(
                    if (e is IllegalStateException && e.message?.contains("not running") == true)
                        "Shizuku is not running. Start Shizuku, then try again."
                    else "Couldn't start recording: ${e.message ?: "unknown error"}"
                )
                finishSessionAndStop()
            }
        }
    }

    private suspend fun startPipeline(
        service: IShellService,
        session: RecordingSession,
        sourceOverride: ScrcpyAudioSource? = null
    ) {
        val codec = prefs.audioCodec
        // WhatsApp / Telegram / Instagram / Snapchat are VoIP: there is no modem tap, so the phone-call
        // sources do not apply. The first choice is the speaker mix (OUTPUT); if that turns out to
        // deliver silence, watchSilence() retries once with the next source in appCallFallbacks.
        val source = sourceOverride
            ?: if (session.isAppCall) appCallFallbacks().first() else prefs.audioSource
        val temp = RecordingStorage.newTempFile(this, session, codec, prefs)
        tempFile = temp

        val engine = AppCallRecordingEngine(this)
        try {
            withContext(Dispatchers.IO) {
                engine.start(service, temp, source, codec, prefs.audioBitRate)
            }
        } catch (e: AppCallPipelineException) {
            engine.cancel(service)
            throw IllegalStateException(e.message, e)
        }
        // On a fallback restart the person still sees "recording" the whole time, so the timer keeps
        // its original start and the state goes straight from one Active to the next.
        if (sourceOverride == null) startedAtElapsedMs = android.os.SystemClock.elapsedRealtime()
        state = RecordingServiceState.Active(engine, false, session)
        if (sourceOverride == null) {
            notifications.vibrate()
            notifications.toast("Recording started")
        }
        AppCallsLogger.i(TAG, "Recording started: source=${source.cliKey} codec=${codec.cliKey}")
        watchSilence(service, session, source, engine)
    }

    /**
     * The order in which sources are tried for a VoIP call. OUTPUT (speaker mix) works on the widest
     * range of phones, so it goes first. PLAYBACK captures other apps' audio directly and needs
     * Android 13+ (the bundled scrcpy-server refuses it earlier), so it is only offered there.
     */
    private fun appCallFallbacks(): List<ScrcpyAudioSource> = buildList {
        add(ScrcpyAudioSource.OUTPUT)
        if (android.os.Build.VERSION.SDK_INT >= 33) add(ScrcpyAudioSource.PLAYBACK)
    }

    private var silenceWatch: kotlinx.coroutines.Job? = null

    /**
     * A recording that "works" but contains only silence is worse than a failed one - the person
     * finds out after the call. So for the first seconds of a recording this checks whether any
     * sound is actually arriving. If the capture is digital silence:
     *   - for an app call it restarts ONCE with the next source in [appCallFallbacks] (the silent
     *     partial file is discarded, the person is not told anything went wrong);
     *   - if there is nothing left to try, or it is a phone call, it says so in a notification so
     *     the person knows to change the audio source, instead of discovering an empty file later.
     * It never stops a recording on its own and never deletes one that has any sound in it.
     */
    private fun watchSilence(
        service: IShellService,
        session: RecordingSession,
        source: ScrcpyAudioSource,
        engine: AppCallRecordingEngine
    ) {
        silenceWatch?.cancel()
        silenceWatch = serviceScope.launch {
            // Give the capture time to produce ~3 s of frames, then look every second for a while.
            kotlinx.coroutines.delay(4_000L)
            var checks = 0
            while (checks < 8) {
                val active = state as? RecordingServiceState.Active ?: return@launch
                if (active.engine !== engine) return@launch   // a different recording is running now
                if (engine.framesCaptured >= 150) break        // enough audio to judge
                kotlinx.coroutines.delay(1_000L)
                checks++
            }
            val active = state as? RecordingServiceState.Active ?: return@launch
            if (active.engine !== engine || !engine.isCapturingSilence) return@launch

            AppCallsLogger.w(TAG, "Capture is silent: source=${source.cliKey} app=${session.sourceApp} frames=${engine.framesCaptured}")

            val next = if (session.isAppCall) appCallFallbacks().dropWhile { it != source }.drop(1).firstOrNull() else null
            if (next != null) {
                AppCallsLogger.i(TAG, "Retrying with source=${next.cliKey}")
                // Throw the silent partial away and start again with the next source.
                // The state deliberately stays Active during this swap. CallRecorder.isRecording (the
                // in-call Record button and its "auto recording active" label) reads it directly, and
                // bouncing through Starting would make the button flicker off for a moment and invite
                // a tap that stops or double-starts the recording.
                // cancel() -> stopRecording() blocks (process wait + up to 2 s of relay join), and this
                // coroutine runs on the Main dispatcher, so do that part on the IO dispatcher.
                withContext(Dispatchers.IO) { engine.cancel(service) }
                tempFile = null
                try {
                    startPipeline(service, session, sourceOverride = next)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppCallsLogger.e(TAG, "Fallback source failed", e)
                    notifications.showError("Recording could not capture sound. Try a different audio source in Recording settings.")
                    finishSessionAndStop()
                }
            } else {
                notifications.showError(
                    if (session.isAppCall)
                        "Only silence is being recorded. Put the call on speaker, or try another audio source in Recording settings."
                    else
                        "Only silence is being recorded. Open Recording settings and try another audio source (for example Voice communication mic)."
                )
            }
        }
    }

    private fun tryStartShizukuServer() {
        val key = prefs.shizukuAuthKey
        if (key.isBlank()) {
            AppCallsLogger.w(TAG, "Shizuku auto-manage is on but no auth key is saved; skipping auto-start.")
            return
        }
        ShizukuConnectionManager.startServer(this, key)
    }

    /**
     * Stops any active capture, finalizes and saves the file, posts the "saved" notification, and
     * ends the service. Idempotent - safe to call from any stop path.
     */
    private fun finishSessionAndStop() {
        silenceWatch?.cancel()
        silenceWatch = null
        shellServiceBind?.cancel()
        shellServiceBind = null
        shellServiceBindFailed = false
        val active = state as? RecordingServiceState.Active
        val session = state.session
        var saved: RecordingStorage.SavedRecording? = null

        if (active != null) {
            AppCallsLogger.i(TAG, "Stopping active recording and saving file...")
            active.engine.stop(shellService)
            val temp = tempFile
            if (temp != null) {
                saved = RecordingStorage.finalize(this, temp, active.engine.activeCodec, prefs, session)
            }
            notifications.vibrate(long = true)
            if (saved != null) {
                notifications.toast("Recording saved")
                notifications.showPostCall(saved, session)
            } else {
                notifications.toast("Nothing was recorded")
            }
        } else if (state is RecordingServiceState.Starting) {
            // Cancelled before capture began: make sure no partial temp file lingers.
            tempFile?.let { runCatching { it.delete() } }
        }
        tempFile = null
        isRecordingNow = false
        startedAtElapsedMs = 0L
        // The recording is finalized, so the shell-service bind is not needed any more.
        shellService = null
        runCatching { shizuku.unbind() }

        serviceScope.launch(Dispatchers.IO) {
            RecordingStorage.runAutoDelete(
                applicationContext, prefs,
                listRecordings = { RecordingLibrary.listAll(applicationContext) },
                delete = { RecordingLibrary.delete(applicationContext, it) }
            )
        }

        state = RecordingServiceState.Standby(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        AppCallsLogger.v(TAG, "Service destroying - making sure everything is released.")
        val wasActive = state is RecordingServiceState.Active
        if (wasActive) finishSessionAndStop()
        serviceScope.cancel()
        shizuku.unbind()
        if (prefs.shizukuAutoManage && !prefs.shizukuKeepAlive && prefs.shizukuAuthKey.isNotBlank()) {
            ShizukuConnectionManager.stopServer(this, prefs.shizukuAuthKey)
        }
        isRunning = false
        isRecordingNow = false
        super.onDestroy()
    }

    // ---- Notification / foreground ---------------------------------------------------------

    private fun refreshNotification(suppress: Boolean = false) {
        if (suppress) {
            startForegroundCompat(notifications.hiddenNotification())
            return
        }
        // Never call stopForeground() while a call is being recorded: a service that leaves the
        // foreground is exactly what Android kills in the background, which would end the recording
        // mid-call. When the user turned the notification off, RecordingNotificationHelper already
        // downgrades the channel to IMPORTANCE_MIN, so it stays in the shade but silent and collapsed.
        startForegroundCompat(notifications.serviceNotification(state))
    }

    private fun satisfyForegroundRequirement() = startForegroundCompat(notifications.hiddenNotification())

    private fun startForegroundCompat(notification: Notification) {
        try {
            when {
                Build.VERSION.SDK_INT >= 34 -> startForeground(
                    RecordingNotificationHelper.SERVICE_NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
                else -> startForeground(
                    RecordingNotificationHelper.SERVICE_NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            }
        } catch (e: Exception) {
            AppCallsLogger.e(TAG, "startForeground failed: ${e.message}", e)
        }
    }
}
