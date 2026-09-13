package com.ashudialer.app.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ashudialer.app.AshuDialerApp
import com.ashudialer.app.MainActivity
import com.ashudialer.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires when a scheduled callback reminder's time arrives (see
 * CallbackReminderScheduler). Posts a normal heads-up notification - not a
 * full-screen one like an incoming call - since a self-set reminder is not
 * as urgent as someone actually calling right now, and tapping "Call now"
 * routes straight through the same DialerPermissions.placeCall() path used
 * everywhere else in the app, keeping the SIM-routing/default-dialer logic
 * in exactly one place.
 */
class CallbackReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE -> handleFire(context, intent)
            ACTION_CALL_NOW -> handleCallNow(context, intent)
            ACTION_DISMISS -> handleDismiss(context, intent)
        }
    }

    private fun handleFire(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        val number = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: return
        val name = intent.getStringExtra(EXTRA_DISPLAY_NAME)?.takeIf { it.isNotBlank() } ?: number

        try {
            postReminderNotification(context, reminderId, number, name)
        } catch (e: Exception) {
            Log.e("CallbackReminderRx", "Failed to post reminder notification", e)
        }

        // Mark fired in the DB on a short-lived coroutine (BroadcastReceivers
        // don't get their own lifecycle scope) so the Reminders list in
        // Settings stops showing it as pending even if the person never
        // taps the notification at all.
        if (reminderId >= 0) {
            val app = context.applicationContext as? AshuDialerApp
            if (app != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        app.database.callbackReminderDao().markFired(reminderId)
                    } catch (e: Exception) {
                        Log.w("CallbackReminderRx", "Failed to mark reminder fired", e)
                    }
                }
            }
        }
    }

    private fun handleCallNow(context: Context, intent: Intent) {
        val number = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: return
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        NotificationManagerCompat.from(context).cancel(reminderId.toInt())
        try {
            if (DialerPermissions.isDefaultDialer(context)) {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
                telecomManager.placeCall(Uri.fromParts("tel", number, null), null)
            } else {
                val callIntent = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(callIntent)
            }
        } catch (e: Exception) {
            Log.w("CallbackReminderRx", "Direct call failed, opening app instead", e)
            val fallback = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallback)
        }
    }

    private fun handleDismiss(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        NotificationManagerCompat.from(context).cancel(reminderId.toInt())
    }

    private fun postReminderNotification(context: Context, reminderId: Long, number: String, name: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val contentPendingIntent = android.app.PendingIntent.getActivity(
            context, reminderId.toInt(), contentIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val callNowIntent = Intent(context, CallbackReminderReceiver::class.java).apply {
            action = ACTION_CALL_NOW
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_PHONE_NUMBER, number)
        }
        val callNowPendingIntent = android.app.PendingIntent.getBroadcast(
            context, (reminderId * 10 + 1).toInt(), callNowIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, CallbackReminderReceiver::class.java).apply {
            action = ACTION_DISMISS
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }
        val dismissPendingIntent = android.app.PendingIntent.getBroadcast(
            context, (reminderId * 10 + 2).toInt(), dismissIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, AshuDialerApp.CHANNEL_CALLBACK_REMINDERS)
            .setSmallIcon(R.drawable.ic_call_notification)
            .setContentTitle("Call back $name?")
            .setContentText("You asked to be reminded to call this number")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(0, "Call now", callNowPendingIntent)
            .addAction(0, "Dismiss", dismissPendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(reminderId.toInt(), notification)
    }

    companion object {
        const val ACTION_FIRE = "com.ashudialer.app.ACTION_CALLBACK_REMINDER_FIRE"
        const val ACTION_CALL_NOW = "com.ashudialer.app.ACTION_CALLBACK_REMINDER_CALL_NOW"
        const val ACTION_DISMISS = "com.ashudialer.app.ACTION_CALLBACK_REMINDER_DISMISS"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_PHONE_NUMBER = "phone_number"
        const val EXTRA_DISPLAY_NAME = "display_name"
    }
}
