package com.ashudialer.app.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.ashudialer.app.AshuDialerApp
import com.ashudialer.app.R
import com.ashudialer.app.data.SignalingSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch


class VideoCallListenerService : Service() {

    private var scopeJob: Job? = null
    private lateinit var scope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        scopeJob = SupervisorJob()
        scope = CoroutineScope(scopeJob!!)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundWithNotification()
        startListening()


        return START_STICKY
    }


    private fun startListening() {
        val app = application as AshuDialerApp
        scope.launch {
            app.appSettingsRepository.settingsFlow
                .flatMapLatest { settings ->
                    val myNumber = settings.myPhoneNumber
                    if (myNumber.isBlank() || !app.authRepository.isSignedIn()) {
                        emptyFlow()
                    } else {
                        app.videoCallSignalingRepository.observeIncomingCalls(myNumber)
                    }
                }
                .collectLatest { session ->
                    handleIncomingCall(session)
                }
        }
    }

    // Tracks whichever call is currently ringing on this device, so a
    // status change on that same call (answered elsewhere, cancelled,
    // etc.) can stop the right ring/vibration loop. Nullable/blank-tolerant
    // by design - see stopRinging's own doc comment for why this must
    // never throw if called when nothing is ringing.
    private var currentlyRingingCallId: String? = null
    private var ringWatcherJob: Job? = null

    private suspend fun handleIncomingCall(session: SignalingSession) {
        val app = application as AshuDialerApp

        // DEEP FIX for "no tu-tu-tu sound for an incoming video call, and
        // the caller's actual name never shows": this used to hardcode
        // "Incoming video call" as the notification's title with no sound
        // or vibration attached at all - every stock dialer's video-call
        // ring genuinely rings (audibly and with vibration) and shows who
        // is calling, so silently posting a notification with a generic
        // label was a real, visible gap against that expectation.
        //
        // Video calls in this app don't go through Android's Telecom
        // framework at all (they're a separate Firestore-signaled WebRTC
        // path - see VideoCallSignalingRepository/WebRtcCallManager), so
        // unlike a *regular* call - where Telecom itself plays the system
        // ringtone the moment the call reaches STATE_RINGING, with zero
        // app-side ringtone code needed (see CallNotificationHelper's own
        // channel, which deliberately sets setSound(null, null) for
        // exactly that reason) - nothing in the OS is going to ring this
        // phone unless this app does it itself. That's what
        // startRinging/stopRinging below now do.
        //
        // Caller identity: session.callerNumber (added specifically for
        // the voice-call-fallback feature, now doing double duty here) is
        // looked up against the same contacts repository every regular
        // incoming call already uses, so a saved contact's name/photo
        // context shows exactly as it would for a normal call. Falls back
        // to the bare number, then only to the old generic label if
        // literally no number came through on this session at all (an
        // old/pre-callerNumber session, or the caller had no number saved
        // in Settings when they placed the call).
        val callerNumber = session.callerNumber?.takeIf { it.isNotBlank() }
        val resolvedName = callerNumber?.let { number ->
            try {
                app.contactsRepository.lookupNameForNumber(number)?.displayName
            } catch (e: Exception) {
                null
            }
        }
        val callerLabel = resolvedName ?: callerNumber ?: "Incoming video call"

        currentlyRingingCallId = session.callId
        showIncomingVideoCallNotification(app, session.callId, callerLabel)
        startRinging()
        watchForRingStop(app, session.callId)
    }

    /**
     * Once a call starts ringing, this watches that SAME call's signaling
     * document for it moving out of STATUS_RINGING (answered on this
     * device via the notification, answered/declined by the caller
     * cancelling, or ended) and stops the ring/vibration loop the instant
     * that happens - without this, the ring would otherwise keep going for
     * its own fixed duration regardless of what actually happened to the
     * call, which is exactly the kind of mismatch a stock dialer never
     * has (the ring always tracks the real call state).
     */
    private fun watchForRingStop(app: AshuDialerApp, callId: String) {
        ringWatcherJob?.cancel()
        ringWatcherJob = scope.launch {
            app.videoCallSignalingRepository.observeCall(callId).collectLatest { session ->
                if (session == null || session.status != SignalingSession.STATUS_RINGING) {
                    if (currentlyRingingCallId == callId) {
                        stopRinging()
                        currentlyRingingCallId = null
                    }
                }
            }
        }
    }

    private var ringtonePlayer: android.media.Ringtone? = null
    private var ringVibrator: Vibrator? = null

    private fun startRinging() {
        stopRinging()
        try {
            val ringtoneUri: Uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getValidRingtoneUri(this)
            val ringtone = RingtoneManager.getRingtone(this, ringtoneUri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ringtone?.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            ringtone?.isLooping = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
            ringtone?.play()
            ringtonePlayer = ringtone
        } catch (e: Exception) {
            // Missing/invalid default ringtone, audio-focus denial, etc. -
            // the notification and its own sound (see the channel below)
            // still fire regardless, so an incoming call is never silent
            // even if this specific extra ring loop can't start.
        }

        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            // Repeating waveform (index 0 = repeat from the start) so the
            // phone keeps buzzing for as long as the call keeps ringing,
            // the same as any real incoming call - not a single one-shot
            // buzz that goes silent while the call is still ringing.
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0))
            ringVibrator = vibrator
        } catch (e: Exception) {
        }
    }

    /**
     * Safe to call any time, including when nothing is currently ringing
     * (e.g. this service's own onDestroy, or a ring that already stopped
     * itself via watchForRingStop) - every call here is wrapped so a
     * teardown path never crashes on a null/already-stopped player.
     */
    private fun stopRinging() {
        try {
            ringtonePlayer?.stop()
        } catch (e: Exception) {
        }
        ringtonePlayer = null
        try {
            ringVibrator?.cancel()
        } catch (e: Exception) {
        }
        ringVibrator = null
    }

    private fun showIncomingVideoCallNotification(context: Context, callId: String, callerLabel: String) {
        val fullScreenIntent = VideoCallActivity.calleeIntent(context, callId, callerLabel)
        val pendingIntent = PendingIntent.getActivity(
            context,
            callId.hashCode(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_INCOMING_VIDEO)
            .setSmallIcon(R.drawable.ic_call_notification)
            .setContentTitle(callerLabel)
            .setContentText("Incoming video call")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID_INCOMING_VIDEO, notification)
    }

    private fun startForegroundWithNotification() {
        createListenerChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_LISTENER)
            .setSmallIcon(R.drawable.ic_call_notification)
            .setContentTitle("Video calling active")
            .setContentText("Listening for incoming video calls")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID_LISTENER,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
            )
        } else {
            startForeground(NOTIFICATION_ID_LISTENER, notification)
        }
    }

    private fun createListenerChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_LISTENER) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_LISTENER,
                    "Video calling status",
                    NotificationManager.IMPORTANCE_MIN
                ).apply { description = "Shows that video calling is active in the background" }
            )
        }
        if (manager.getNotificationChannel(CHANNEL_INCOMING_VIDEO) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_INCOMING_VIDEO,
                    "Incoming video calls",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Full-screen alert for incoming video calls"
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    // Deliberately left at this channel's own default sound
                    // (unlike CallNotificationHelper's regular-call channel,
                    // which explicitly silences itself because Telecom
                    // already rings on its own). There's no equivalent
                    // framework-level ringing for this Firestore-signaled
                    // call path, so this channel's own notification sound
                    // is one more/backup way this rings - the primary one is
                    // startRinging() above, which plays the actual current
                    // default ringtone (not just a generic notification blip)
                    // and adds the repeating vibration pattern neither this
                    // channel nor a plain notification sound alone would
                    // provide.
                }
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopRinging()
        ringWatcherJob?.cancel()
        scopeJob?.cancel()
    }

    companion object {
        const val CHANNEL_LISTENER = "video_call_listener_channel"
        const val CHANNEL_INCOMING_VIDEO = "incoming_video_call_channel"
        private const val NOTIFICATION_ID_LISTENER = 7101
        private const val NOTIFICATION_ID_INCOMING_VIDEO = 7102

        fun start(context: Context) {
            val intent = Intent(context, VideoCallListenerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stops the background listener entirely (foreground service + its
         * persistent notification) — used when video calling isn't configured
         * (signed out, or no number saved yet), so the app has no standing
         * background footprint until the person actually turns the feature on.
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, VideoCallListenerService::class.java))
        }
    }
}
