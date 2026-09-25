// Decision logic adapted from ShizuCallRecorder's CallSessionManager (github.com/kitsumed/ShizuCallRecorder), GPLv3+.
package com.ashudialer.app.appcalls.recording

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import com.ashudialer.app.appcalls.AppCallsLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Receives call-state changes, works out the call's direction and number, and decides whether to
 * start [RecordingForegroundService] (record now), hold it in standby (record when answered), or
 * do nothing (filtered out by the user's settings).
 *
 * Two input paths feed it:
 *  - [handlePhoneState]: the system PHONE_STATE broadcast (see PhoneStateReceiver). Works for any
 *    carrier call regardless of which dialer app placed it.
 *  - [notifyCallAnswered]: called by this app's own in-call service the moment a call becomes
 *    ACTIVE, which is exact - unlike the broadcast, which can only say "off hook".
 *
 * It's a process-wide singleton because a call's state must survive between separate broadcast
 * deliveries. The session is locked on the first non-idle state so a second ringing call during
 * an active call can't overwrite the first call's direction/number.
 */
class CallRecordingCoordinator private constructor(context: Context) {

    companion object {
        private const val TAG = "AppCalls:Coordinator"

        @Volatile
        private var INSTANCE: CallRecordingCoordinator? = null

        fun getInstance(context: Context): CallRecordingCoordinator =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: CallRecordingCoordinator(context.applicationContext).also { INSTANCE = it }
            }
    }

    private val appContext: Context = context.applicationContext
    private val prefs = RecordingPrefs(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ---- Per-call state (cleared when the phone goes idle) -------------------------------
    private var session: RecordingSession? = null
    private var serviceIntentSent = false
    private var recordingStarted = false
    private var answered = false
    private var pendingJob: Job? = null

    private val sessionActive: Boolean get() = session != null

    /**
     * Handles a PHONE_STATE broadcast. [number] is null on the first of the two broadcasts Android
     * sends (the one without the number), and on anonymous calls.
     */
    @Synchronized
    fun handlePhoneState(state: String, number: String?) {
        if (!prefs.callRecordingEnabled) return

        val callState = when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
            TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
            TelephonyManager.EXTRA_STATE_IDLE -> TelephonyManager.CALL_STATE_IDLE
            else -> return
        }
        AppCallsLogger.i(TAG, "Phone state=$state number=${number ?: "(none)"}")

        if (callState == TelephonyManager.CALL_STATE_IDLE) {
            pendingJob?.cancel()
            if (sessionActive) {
                AppCallsLogger.d(TAG, "Call ended - telling service to stop and finalize.")
                sendServiceCommand(RecordingForegroundService.ACTION_STOP_RECORDING)
                resetSession()
            }
            return
        }

        pendingJob?.cancel()
        pendingJob = scope.launch { processStateUpdate(callState, number) }
    }

    /**
     * Called when a call reaches ACTIVE (outgoing: the other side picked up / incoming: it was
     * answered). If the call was being held in standby for "record only when answered", starts now.
     */
    @Synchronized
    fun notifyCallAnswered(number: String? = null, isIncoming: Boolean? = null) {
        // Fallback for phones/ROMs that never deliver the PHONE_STATE broadcast to this app
        // (some OEMs restrict it). Without it there would be no session at all and a call the
        // user asked to record would silently never start. The in-call screen knows the number
        // and direction, so build the session from that instead.
        if (!sessionActive) {
            if (!prefs.callRecordingEnabled) return
            val direction = when (isIncoming) {
                true -> CallDirection.INCOMING
                false -> CallDirection.OUTGOING
                null -> CallDirection.OUTGOING
            }
            AppCallsLogger.w(TAG, "No PHONE_STATE session for an answered call - building one from the in-call screen.")
            session = RecordingSession(RecordingSession.sanitizeNumber(number), direction)
            scope.launch {
                val enriched = withContext(Dispatchers.Default) { session?.let { enrich(it) } }
                withContext(Dispatchers.Main) {
                    if (enriched != null && !serviceIntentSent) session = enriched
                    answered = true
                    evaluateAndStart()
                }
            }
            return
        }
        answered = true
        evaluateAndStart()
    }

    private suspend fun processStateUpdate(callState: Int, rawNumber: String?) {
        val sanitized = RecordingSession.sanitizeNumber(rawNumber)
        val direction = when (callState) {
            TelephonyManager.CALL_STATE_RINGING -> CallDirection.INCOMING
            TelephonyManager.CALL_STATE_OFFHOOK -> CallDirection.OUTGOING
            else -> null
        }

        // Lock the session on the first non-idle state. Later states may only fill in a number
        // that was missing (the second of the two PHONE_STATE broadcasts carries it).
        if (direction != null) {
            withContext(Dispatchers.Main) {
                val current = session
                when {
                    current == null -> session = RecordingSession(sanitized, direction)
                    current.direction == direction && current.phoneNumber.isNullOrBlank() &&
                        !sanitized.isNullOrBlank() && !serviceIntentSent ->
                        session = current.copy(phoneNumber = sanitized)
                    else -> AppCallsLogger.v(TAG, "Session already locked; ignoring state change to $direction")
                }
            }
        }

        if (callState == TelephonyManager.CALL_STATE_RINGING) {
            withContext(Dispatchers.Main) { evaluateAndStart(stillRinging = true) }
        }

        if (callState == TelephonyManager.CALL_STATE_OFFHOOK) {
            // Android sends two broadcasts; the first has no number. Give the second one a short
            // window to arrive so we don't name/filter the recording as "anonymous" by mistake.
            if (sanitized.isNullOrBlank()) delay(500)
            val enriched = withContext(Dispatchers.Default) { session?.let { enrich(it) } }
            withContext(Dispatchers.Main) {
                if (enriched != null && !serviceIntentSent) session = enriched
                evaluateAndStart()
            }
        }
    }

    /** Adds the contact name (if Contacts permission is granted) and the cross-country flag. */
    private fun enrich(base: RecordingSession): RecordingSession {
        val number = base.phoneNumber
        if (number.isNullOrBlank()) return base.copy(isCrossCountry = true)
        return base.copy(
            contactName = lookupContactName(number),
            isCrossCountry = isCrossCountry(number)
        )
    }

    private fun lookupContactName(number: String): String? {
        if (appContext.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            appContext.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (e: Exception) {
            AppCallsLogger.w(TAG, "Contact lookup failed: ${e.message}")
            null
        }
    }

    /**
     * Lightweight cross-country check without shipping a phone-number library: a number written in
     * international form (+CC...) is "cross-country" if its calling code differs from this
     * device's own country calling code. A number without a leading + is treated as local.
     */
    private fun isCrossCountry(number: String): Boolean {
        val normalized = RecordingSession.normalizeNumber(number)
        if (!normalized.startsWith("+")) return false
        val ownCode = ownCountryCallingCode() ?: return false
        return !normalized.removePrefix("+").startsWith(ownCode)
    }

    private fun ownCountryCallingCode(): String? {
        val tm = appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return null
        val iso = (runCatching { tm.networkCountryIso }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: runCatching { tm.simCountryIso }.getOrNull()?.takeIf { it.isNotBlank() })
            ?.uppercase() ?: return null
        return CALLING_CODES[iso]
    }

    /** Decides start / standby / nothing from the user's settings and sends the service command. */
    private fun evaluateAndStart(stillRinging: Boolean = false) {
        if (recordingStarted) return
        val current = session ?: return

        val configured = prefs.callRecordingEnabled &&
            (prefs.autoRecordIncoming || prefs.autoRecordOutgoing)
        if (!configured) return

        val wantsAuto = shouldAutoRecord(current)
        val waitingForAnswer = wantsAuto && prefs.recordOnAnswerOnly && !answered

        // While an incoming call is still ringing, keep the service's notification hidden so it
        // doesn't fight the answer/decline UI for the screen.
        val suppress = stillRinging && current.direction == CallDirection.INCOMING

        if (waitingForAnswer) {
            if (!serviceIntentSent) {
                AppCallsLogger.i(TAG, "Holding ${current.direction} call in standby until answered.")
                sendServiceCommand(RecordingForegroundService.ACTION_STANDBY, current, suppress)
                serviceIntentSent = true
            }
            return
        }

        if (serviceIntentSent && !wantsAuto) return

        if (wantsAuto) {
            AppCallsLogger.i(TAG, "Starting recording for ${current.direction} call.")
            sendServiceCommand(RecordingForegroundService.ACTION_START_RECORDING, current, suppress)
            recordingStarted = true
        } else {
            sendServiceCommand(RecordingForegroundService.ACTION_STANDBY, current, suppress)
        }
        serviceIntentSent = true
    }

    private fun shouldAutoRecord(s: RecordingSession): Boolean {
        val number = RecordingSession.normalizeNumber(s.phoneNumber.orEmpty())
        val anonymous = number.isBlank()

        return when (s.direction) {
            CallDirection.INCOMING -> {
                if (!prefs.autoRecordIncoming) return false
                if (anonymous && prefs.ignoreAnonymousIncoming) return false
                if (s.isCrossCountry && prefs.ignoreCrossCountryIncoming) return false
                !shouldIgnoreContact(number, prefs.ignoreContactsModeIncoming, prefs.ignoredNumbersIncoming)
            }
            CallDirection.OUTGOING -> {
                if (!prefs.autoRecordOutgoing) return false
                if (s.isCrossCountry && prefs.ignoreCrossCountryOutgoing) return false
                !shouldIgnoreContact(number, prefs.ignoreContactsModeOutgoing, prefs.ignoredNumbersOutgoing)
            }
        }
    }

    private fun shouldIgnoreContact(number: String, mode: RecordingPrefs.IgnoreContactsMode, ignored: Set<String>): Boolean {
        if (number.isBlank()) return false
        return when (mode) {
            RecordingPrefs.IgnoreContactsMode.NONE -> false
            RecordingPrefs.IgnoreContactsMode.ALL -> lookupContactName(number) != null
            RecordingPrefs.IgnoreContactsMode.SELECTED ->
                ignored.any { RecordingSession.normalizeNumber(it) == number }
        }
    }

    private fun resetSession() {
        session = null
        serviceIntentSent = false
        recordingStarted = false
        answered = false
    }

    private fun sendServiceCommand(action: String, session: RecordingSession? = null, suppress: Boolean = false) {
        val intent = Intent(appContext, RecordingForegroundService::class.java).apply {
            this.action = action
            if (session != null) putExtra(RecordingSession.EXTRA_SESSION, session)
            if (suppress) putExtra(RecordingForegroundService.EXTRA_SUPPRESS_NOTIFICATION, true)
        }
        try {
            if (action == RecordingForegroundService.ACTION_STOP_RECORDING) {
                // The service is already running in the foreground; a plain startService just
                // delivers the stop command and gives it time to finalize the file cleanly.
                appContext.startService(intent)
            } else {
                appContext.startForegroundService(intent)
            }
        } catch (e: Exception) {
            AppCallsLogger.e(TAG, "Couldn't send $action to the recording service: ${e.message}", e)
        }
    }
}

/** ISO country code -> international calling code (digits only), for the lightweight cross-country check. */
private val CALLING_CODES: Map<String, String> = mapOf(
    "IN" to "91", "US" to "1", "CA" to "1", "GB" to "44", "AU" to "61", "NZ" to "64",
    "PK" to "92", "BD" to "880", "LK" to "94", "NP" to "977", "AE" to "971", "SA" to "966",
    "QA" to "974", "KW" to "965", "OM" to "968", "BH" to "973", "SG" to "65", "MY" to "60",
    "ID" to "62", "PH" to "63", "TH" to "66", "VN" to "84", "JP" to "81", "KR" to "82",
    "CN" to "86", "HK" to "852", "DE" to "49", "FR" to "33", "IT" to "39", "ES" to "34",
    "NL" to "31", "BE" to "32", "CH" to "41", "AT" to "43", "SE" to "46", "NO" to "47",
    "DK" to "45", "FI" to "358", "IE" to "353", "PT" to "351", "PL" to "48", "RU" to "7",
    "TR" to "90", "EG" to "20", "ZA" to "27", "NG" to "234", "KE" to "254", "BR" to "55",
    "MX" to "52", "AR" to "54", "CO" to "57", "CL" to "56"
)
