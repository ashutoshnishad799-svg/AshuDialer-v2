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
                if (!ShizukuConnectionManager.waitForServer()) {
                    throw IllegalStateException("Shizuku is not running")
                }
                val service = shizuku.getShellService()
                shellService = service
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

    private suspend fun startPipeline(service: IShellService, session: RecordingSession) {
        val codec = prefs.audioCodec
        // WhatsApp/Telegram are VoIP: no modem tap exists, only the speaker mix (OUTPUT) works.
        val source = if (session.isAppCall) ScrcpyAudioSource.OUTPUT else prefs.audioSource
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
        startedAtElapsedMs = android.os.SystemClock.elapsedRealtime()
        state = RecordingServiceState.Active(engine, false, session)
        notifications.vibrate()
        notifications.toast("Recording started")
        AppCallsLogger.i(TAG, "Recording started: source=${source.cliKey} codec=${codec.cliKey}")
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
        val active = state as? RecordingServiceState.Active
        val session = state.session
        var saved: RecordingStorage.SavedRecording? = null

        if (active != null) {
            AppCallsLogger.i(TAG, "Stopping active recording and saving file...")
            active.engine.stop(shellService)
            val temp = tempFile
            if (temp != null) {
                saved = RecordingStorage.finalize(this, temp, active.engine.activeCodec, prefs)
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
