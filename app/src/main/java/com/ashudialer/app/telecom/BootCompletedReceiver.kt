package com.ashudialer.app.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ashudialer.app.AshuDialerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AlarmManager alarms are cleared when the device reboots. Without this,
 * a callback reminder set for "tomorrow at 9am" would silently vanish if
 * the phone happened to restart overnight - the DB row would still say
 * "pending" in the UI, but no alarm would actually be scheduled to fire it.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val app = context.applicationContext as? AshuDialerApp ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = app.database.callbackReminderDao()
                val now = System.currentTimeMillis()
                dao.getAllPending().forEach { reminder ->
                    if (reminder.triggerAtMillis <= now) {
                        // Already overdue by the time the phone came back up -
                        // fire it right away rather than scheduling something
                        // in the past (which some OEM AlarmManager
                        // implementations silently drop) or losing it forever.
                        CallbackReminderReceiver().onReceive(
                            context,
                            Intent(CallbackReminderReceiver.ACTION_FIRE).apply {
                                putExtra(CallbackReminderReceiver.EXTRA_REMINDER_ID, reminder.id)
                                putExtra(CallbackReminderReceiver.EXTRA_PHONE_NUMBER, reminder.phoneNumber)
                                putExtra(CallbackReminderReceiver.EXTRA_DISPLAY_NAME, reminder.displayName)
                            }
                        )
                    } else {
                        CallbackReminderScheduler.schedule(context, reminder)
                    }
                }
            } catch (e: Exception) {
                Log.w("BootCompletedReceiver", "Failed to reschedule callback reminders", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
