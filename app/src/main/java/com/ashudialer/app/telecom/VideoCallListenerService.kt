package com.ashudialer.app.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
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

    private suspend fun handleIncomingCall(session: SignalingSession) {
        val app = application as AshuDialerApp


        val callerLabel = "Incoming video call"
        showIncomingVideoCallNotification(app, session.callId, callerLabel)
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
                }
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
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
