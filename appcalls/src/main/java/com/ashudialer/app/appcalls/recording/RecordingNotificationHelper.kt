// Adapted from ShizuCallRecorder's RecordingNotificationHelper (github.com/kitsumed/ShizuCallRecorder), GPLv3+.
package com.ashudialer.app.appcalls.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.ashudialer.app.appcalls.R
import java.io.File

/** Builds every notification and small piece of user feedback (toast / vibration) the recorder shows. */
class RecordingNotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_SERVICE = "rec_channel_service"
        const val CHANNEL_ERROR = "rec_channel_error"
        const val CHANNEL_POST_CALL = "rec_channel_post_call"
        const val CHANNEL_HIDDEN = "rec_channel_hidden"

        const val SERVICE_NOTIFICATION_ID = 7101
        const val ERROR_NOTIFICATION_ID = 7102
        const val POST_CALL_NOTIFICATION_ID = 7103

        const val ACTION_DELETE_RECORDING = "com.ashudialer.app.appcalls.action.DELETE_RECORDING"
        const val EXTRA_RECORDING_PATH = "com.ashudialer.app.appcalls.EXTRA_RECORDING_PATH"
        const val EXTRA_RECORDING_URI = "com.ashudialer.app.appcalls.EXTRA_RECORDING_URI"
    }

    private val prefs = RecordingPrefs(context)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        // Re-created every start so a change to "show recording notification" takes effect.
        val visible = prefs.showRecordingNotification

        notificationManager.deleteNotificationChannel(CHANNEL_SERVICE)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE, "Call recording",
                if (visible) NotificationManager.IMPORTANCE_LOW else NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Shown while a call is being recorded"
                setSound(null, null); enableVibration(false); enableLights(false); setShowBadge(false)
            }
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ERROR, "Recording problems", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Tells you when a call could not be recorded and why"
                enableVibration(false)
            }
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_POST_CALL, "Recording saved", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Open / Share / Delete a recording right after the call"
                enableVibration(false); setShowBadge(false)
            }
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_HIDDEN, "Preparing recording (hidden)", NotificationManager.IMPORTANCE_MIN).apply {
                setSound(null, null); enableVibration(false); enableLights(false); setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
        )
    }

    /** Silent, contentless placeholder that satisfies startForeground() without popping up over the answer/decline UI. */
    fun hiddenNotification(): Notification =
        NotificationCompat.Builder(context, CHANNEL_HIDDEN)
            .setSmallIcon(R.drawable.ic_rec_mic)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true).setOngoing(true).setShowWhen(false)
            .build()

    fun serviceNotification(state: RecordingServiceState): Notification {
        val label = state.session?.displayLabel
        val via = state.session?.sourceApp?.let { "via $it" }

        val title: String
        val text: String
        var actionText: String? = null
        var actionIntent: String? = null

        when (state) {
            is RecordingServiceState.Starting -> {
                title = "Preparing to record"
                text = "Connecting to Shizuku..."
            }
            is RecordingServiceState.Active -> if (state.isPaused) {
                title = "Recording paused"
                text = label ?: "Tap resume to continue"
                actionText = "Resume"; actionIntent = RecordingForegroundService.ACTION_RESUME_RECORDING
            } else {
                title = "Recording call"
                text = listOfNotNull(label, via).joinToString(" - ").ifBlank { "Recording in progress" }
                actionText = "Pause"; actionIntent = RecordingForegroundService.ACTION_PAUSE_RECORDING
            }
            is RecordingServiceState.Standby -> {
                title = "Call recording ready"
                text = label ?: "Tap Record to start recording this call"
                actionText = "Record"; actionIntent = RecordingForegroundService.ACTION_MANUAL_START
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_rec_mic)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (actionText != null && actionIntent != null) {
            builder.addAction(0, actionText, servicePendingIntent(actionIntent, 1))
        }
        if (state is RecordingServiceState.Active) {
            builder.addAction(R.drawable.ic_rec_stop, "Stop", servicePendingIntent(RecordingForegroundService.ACTION_STOP_RECORDING, 2))
        }
        return builder.build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, RecordingForegroundService::class.java).setAction(action)
        return PendingIntent.getService(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun showError(message: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        val n = NotificationCompat.Builder(context, CHANNEL_ERROR)
            .setSmallIcon(R.drawable.ic_rec_mic)
            .setContentTitle("Call not recorded")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { notificationManager.notify(ERROR_NOTIFICATION_ID, n) }
        toast(message)
    }

    /** "Recording saved" notification with Open / Share / Delete. */
    fun showPostCall(saved: RecordingStorage.SavedRecording, session: RecordingSession?) {
        if (!prefs.postRecordingActionsNotification) return
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return

        val playUri = shareableUri(saved) ?: return
        val mime = if (saved.file.name.endsWith(".ogg", true)) "audio/ogg" else "audio/mp4"

        val open = PendingIntent.getActivity(
            context, 10,
            Intent(Intent.ACTION_VIEW).setDataAndType(playUri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val share = PendingIntent.getActivity(
            context, 11,
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, playUri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                "Share recording"
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val delete = PendingIntent.getBroadcast(
            context, 12,
            Intent(context, PostRecordingActionReceiver::class.java)
                .setAction(ACTION_DELETE_RECORDING)
                .putExtra(EXTRA_RECORDING_PATH, saved.file.absolutePath)
                .putExtra(EXTRA_RECORDING_URI, saved.uri?.toString()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val n = NotificationCompat.Builder(context, CHANNEL_POST_CALL)
            .setSmallIcon(R.drawable.ic_rec_mic)
            .setContentTitle("Recording saved")
            .setContentText(session?.displayLabel ?: saved.file.name)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "Open", open)
            .addAction(0, "Share", share)
            .addAction(0, "Delete", delete)
            .build()
        runCatching { notificationManager.notify(POST_CALL_NOTIFICATION_ID, n) }
    }

    private fun shareableUri(saved: RecordingStorage.SavedRecording): Uri? {
        saved.uri?.let { return it }
        return runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", saved.file)
        }.getOrNull()
    }

    fun toast(message: String) {
        if (!prefs.showToasts) return
        mainHandler.post { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }

    fun vibrate(long: Boolean = false) {
        if (!prefs.vibrateOnStartStop) return
        runCatching {
            val effect = VibrationEffect.createOneShot(if (long) 200L else 80L, VibrationEffect.DEFAULT_AMPLITUDE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(effect)
            }
        }
    }
}
