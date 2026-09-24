package com.ashudialer.app.appcalls.recording

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.io.File

/** Handles the "Delete" button on the "Recording saved" notification. */
class PostRecordingActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RecordingNotificationHelper.ACTION_DELETE_RECORDING) return
        val path = intent.getStringExtra(RecordingNotificationHelper.EXTRA_RECORDING_PATH) ?: return
        RecordingLibrary.delete(context.applicationContext, File(path))
        context.getSystemService(NotificationManager::class.java)
            ?.cancel(RecordingNotificationHelper.POST_CALL_NOTIFICATION_ID)
    }
}
