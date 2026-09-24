package com.ashudialer.app.telecom

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ashudialer.app.data.db.CallbackReminderEntity

/**
 * Schedules/cancels the system alarm behind a callback reminder. Deliberately
 * uses setAndAllowWhileIdle() rather than setExactAndAllowWhileIdle() - a
 * "remind me to call back around 6pm" reminder does not need to-the-second
 * precision, and setAndAllowWhileIdle() needs no extra runtime permission
 * (SCHEDULE_EXACT_ALARM/USE_EXACT_ALARM), no Settings redirect, and no
 * special-app-access screen, matching the app's existing pattern of asking
 * for as few permissions as the feature can actually get away with.
 */
object CallbackReminderScheduler {

    private const val TAG = "CallbackReminderSched"

    fun schedule(context: Context, reminder: CallbackReminderEntity) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, CallbackReminderReceiver::class.java).apply {
                action = CallbackReminderReceiver.ACTION_FIRE
                putExtra(CallbackReminderReceiver.EXTRA_REMINDER_ID, reminder.id)
                putExtra(CallbackReminderReceiver.EXTRA_PHONE_NUMBER, reminder.phoneNumber)
                putExtra(CallbackReminderReceiver.EXTRA_DISPLAY_NAME, reminder.displayName)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reminder.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                reminder.triggerAtMillis,
                pendingIntent
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to schedule callback reminder", e)
        }
    }

    fun cancel(context: Context, reminderId: Long) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, CallbackReminderReceiver::class.java).apply {
                action = CallbackReminderReceiver.ACTION_FIRE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reminderId.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cancel callback reminder", e)
        }
    }
}
