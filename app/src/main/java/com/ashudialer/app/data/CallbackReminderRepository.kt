package com.ashudialer.app.data

import android.content.Context
import com.ashudialer.app.data.db.CallbackReminderDao
import com.ashudialer.app.data.db.CallbackReminderEntity
import com.ashudialer.app.telecom.CallbackReminderScheduler
import kotlinx.coroutines.flow.Flow

class CallbackReminderRepository(
    private val context: Context,
    private val dao: CallbackReminderDao
) {
    fun observePending(): Flow<List<CallbackReminderEntity>> = dao.observePending()

    suspend fun isReminderSet(phoneNumber: String): Boolean =
        dao.getPendingForNumber(phoneNumber) != null

    /**
     * Replaces any existing pending reminder for this number rather than
     * stacking a second one - a person re-setting a reminder for someone
     * they already have one for almost certainly means "change the time",
     * not "remind me twice".
     */
    suspend fun setReminder(phoneNumber: String, displayName: String, triggerAtMillis: Long) {
        dao.getPendingForNumber(phoneNumber)?.let { existing ->
            CallbackReminderScheduler.cancel(context, existing.id)
            dao.deleteById(existing.id)
        }
        val entity = CallbackReminderEntity(
            phoneNumber = phoneNumber,
            displayName = displayName,
            triggerAtMillis = triggerAtMillis
        )
        val id = dao.insert(entity)
        CallbackReminderScheduler.schedule(context, entity.copy(id = id))
    }

    suspend fun cancelReminder(reminder: CallbackReminderEntity) {
        CallbackReminderScheduler.cancel(context, reminder.id)
        dao.deleteById(reminder.id)
    }
}
