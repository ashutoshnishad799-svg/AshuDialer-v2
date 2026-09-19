package com.ashudialer.app.telecom

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.ashudialer.app.AshuDialerApp
import com.ashudialer.app.data.db.AshuDialerDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch


class PixelCallScreeningService : CallScreeningService() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart ?: run {
            respondAllow(callDetails)
            return
        }

        scope.launch {
            val db = AshuDialerDatabase.getInstance(applicationContext)
            val isBlocked = db.blockedNumberDao().isBlocked(number)

            if (isBlocked) {
                Log.d("PixelCallScreening", "Rejecting blocked number: $number")
                respondReject(callDetails)
                return@launch
            }

            val app = applicationContext as? AshuDialerApp
            val settings = app?.appSettingsRepository?.settingsFlow?.first()

            val quietHoursResult = checkQuietHours(number)
            if (quietHoursResult) {
                Log.d("PixelCallScreening", "Declining during Quiet Hours: $number")
                respondReject(callDetails)
                return@launch
            }

            if (settings?.silenceUnknownCallers == true) {
                val contact = app?.contactsRepository?.lookupNameForNumber(number)
                if (contact == null) {
                    Log.d("PixelCallScreening", "Silencing unknown caller: $number")
                    respondSilenced(callDetails)
                    return@launch
                }
            }

            if (settings?.spamProtectionEnabled != false) {
                val history = db.callLogDao().getHistoryForNumber(number)
                val assessment = SpamDetector.scoreNumber(number, history)
                if (assessment.isLikelySpam) {
                    Log.d("PixelCallScreening", "Flagged as likely spam: $number (${assessment.confidence}%)")
                    PendingSpamFlags.mark(number, assessment)
                }
            }

            if (settings?.flagInternationalNumbers == true && number.startsWith("+")) {
                val digits = number.filter(Char::isDigit)
                if (!digits.startsWith("91")) {
                    PendingSpamFlags.mark(number, SpamAssessment(true, 65, listOf("International caller")))
                }
            }

            respondAllow(callDetails)
        }
    }

    /**
     * Returns true if this call should be silently declined for Quiet Hours.
     * Favorites always ring through when allowFavorites is on (the schedule's
     * whole point is "don't wake me for just anyone, but my people can still
     * reach me"), and a number calling a second time within the configured
     * window rings through too, on the assumption a repeat call in a short
     * window might be urgent - the same logic real phones' native DND modes
     * use for repeat callers.
     */
    private suspend fun checkQuietHours(number: String): Boolean {
        val app = applicationContext as? AshuDialerApp ?: return false
        val isQuietNow = app.quietHoursRepository.isCurrentlyQuietHours()
        if (!isQuietNow) return false

        val schedule = app.quietHoursRepository.observe()
        // observe() is a Flow meant for UI; here a one-shot read is enough,
        // so take the first emission rather than collecting continuously.
        val current = schedule.first()

        if (current.allowFavorites) {
            val contact = app.contactsRepository.lookupNameForNumber(number)
            if (contact?.isFavorite == true) return false
        }

        if (current.allowRepeatCallerBypass) {
            val isRepeat = app.quietHoursRepository.shouldBypassAsRepeatCaller(
                number, current.repeatCallerWindowMinutes
            )
            if (isRepeat) return false
        }

        return true
    }

    private fun respondAllow(callDetails: Call.Details) {
        val response = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()
        respondToCall(callDetails, response)
    }

    private fun respondSilenced(callDetails: Call.Details) {
        val builder = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setSilenceCall(true)
        }
        val response = builder.build()
        respondToCall(callDetails, response)
    }

    private fun respondReject(callDetails: Call.Details, skipNotification: Boolean = true) {
        val response = CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .setSkipCallLog(false)
            .setSkipNotification(skipNotification)
            .build()
        respondToCall(callDetails, response)
    }
}
