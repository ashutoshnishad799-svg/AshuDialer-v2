package com.ashudialer.app

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.ashudialer.app.data.AppSettingsRepository
import com.ashudialer.app.data.AuthRepository
import com.ashudialer.app.data.CallLogRepository
import com.ashudialer.app.data.CallNoteRepository
import com.ashudialer.app.data.CloudBackupRepository
import com.ashudialer.app.data.ContactsRepository
import com.ashudialer.app.data.LocalBackupRepository
import com.ashudialer.app.data.QuietHoursRepository
import com.ashudialer.app.data.SystemCallLogRepository
import com.ashudialer.app.data.ThemePreference
import com.ashudialer.app.data.VideoCallSignalingRepository
import com.ashudialer.app.data.db.AshuDialerDatabase
import com.ashudialer.app.telecom.CallNotificationHelper
import com.ashudialer.app.telecom.CallRecorder
import com.ashudialer.app.telecom.VideoCallListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

class AshuDialerApp : Application() {

    /**
     * A CoroutineScope that lives as long as the process, not tied to any single
     * Activity. Writes that must survive the Activity that triggered them being
     * torn down (e.g. saving an in-call note right before the user ends the call,
     * or logging/placing a call while the UI is mid-recomposition) should launch
     * on this scope instead of an Activity's lifecycleScope, which cancels
     * in-flight work as soon as that Activity finishes.
     */
    val applicationScope = CoroutineScope(SupervisorJob())

    lateinit var database: AshuDialerDatabase
        private set
    lateinit var callLogRepository: CallLogRepository
        private set
    lateinit var systemCallLogRepository: SystemCallLogRepository
        private set
    lateinit var contactsRepository: ContactsRepository
        private set
    lateinit var themePreference: ThemePreference
        private set
    lateinit var appSettingsRepository: AppSettingsRepository
        private set
    lateinit var callRecorder: CallRecorder
        private set
    lateinit var authRepository: AuthRepository
        private set
    lateinit var cloudBackupRepository: CloudBackupRepository
        private set
    lateinit var onboardingPreference: com.ashudialer.app.data.OnboardingPreference
        private set
    lateinit var callNoteRepository: CallNoteRepository
        private set
    lateinit var videoCallSignalingRepository: VideoCallSignalingRepository
        private set
    lateinit var localBackupRepository: LocalBackupRepository
        private set
    lateinit var quietHoursRepository: QuietHoursRepository
        private set
    lateinit var callInsightsRepository: com.ashudialer.app.data.CallInsightsRepository
        private set
    lateinit var privateSpaceRepository: com.ashudialer.app.data.PrivateSpaceRepository
        private set
    lateinit var localAuthRepository: com.ashudialer.app.data.LocalAuthRepository
        private set
    lateinit var callbackReminderRepository: com.ashudialer.app.data.CallbackReminderRepository
        private set

    /**
     * One-time fix for builds where auto-record defaulted to ON. Anyone who had
     * already turned the master "Call recording" switch on in such a build has
     * "auto_record_incoming/outgoing = true" stored even though they never chose
     * it, so changing the default in code alone would not help them. This clears
     * those two values exactly once (guarded by a flag) so they fall back to the
     * new default (off). After this runs, whatever the person picks in Settings
     * is respected and never touched again.
     */
    private fun migrateAutoRecordDefaultsOnce() {
        val flags = getSharedPreferences("ashu_migrations", MODE_PRIVATE)
        if (flags.getBoolean("auto_record_default_off_v1", false)) return
        getSharedPreferences("ashu_call_recording_prefs", MODE_PRIVATE).edit()
            .remove("auto_record_incoming")
            .remove("auto_record_outgoing")
            // Same one-time reset for the two noisy feedback defaults that were
            // ON in earlier builds (see RecordingPrefs.showToasts / post-call).
            .remove("show_toasts")
            .remove("post_recording_notification")
            .apply()
        flags.edit().putBoolean("auto_record_default_off_v1", true).apply()
    }

    override fun onCreate() {
        super.onCreate()
        database = AshuDialerDatabase.getInstance(this)
        callLogRepository = CallLogRepository(database.callLogDao())
        systemCallLogRepository = SystemCallLogRepository(this)
        contactsRepository = ContactsRepository(this)
        themePreference = ThemePreference(this)
        appSettingsRepository = AppSettingsRepository(this)
        callRecorder = CallRecorder(this)
        authRepository = AuthRepository(this)
        cloudBackupRepository = CloudBackupRepository()
        onboardingPreference = com.ashudialer.app.data.OnboardingPreference(this)
        callNoteRepository = CallNoteRepository(database.callNoteDao())
        videoCallSignalingRepository = VideoCallSignalingRepository()
        localBackupRepository = LocalBackupRepository(
            callLogDao = database.callLogDao(),
            blockedNumberDao = database.blockedNumberDao(),
            callNoteDao = database.callNoteDao(),
            simRoutingDao = database.simRoutingDao(),
            vibrationRuleDao = database.vibrationRuleDao()
        )
        quietHoursRepository = QuietHoursRepository(database.quietHoursDao())
        callInsightsRepository = com.ashudialer.app.data.CallInsightsRepository(database.callLogDao())
        privateSpaceRepository = com.ashudialer.app.data.PrivateSpaceRepository(database.privateSpaceDao(), database.lockedNumberDao(), this)
        localAuthRepository = com.ashudialer.app.data.LocalAuthRepository(this)
        callbackReminderRepository = com.ashudialer.app.data.CallbackReminderRepository(this, database.callbackReminderDao())
        migrateAutoRecordDefaultsOnce()
        createNotificationChannels()

        CallNotificationHelper.createChannels(this)

        // Fires the silent anonymous sign-in (see AuthRepository.
        // ensureSignedIn's doc comment) once per process start, so every
        // install gets a stable Firebase identity without any manual
        // Google Sign-In step. watchVideoCallingAvailability below still
        // separately requires settings.myPhoneNumber to be set before
        // actually starting the listener service - this call only removes
        // the "signed in" half of that gate, not the "has entered their
        // own number" half, since a phone number is still needed to be
        // reachable by number the way every other call in this app works.
        applicationScope.launch {
            authRepository.ensureSignedIn()
        }

        // Removes the *other* manual step video calling used to require:
        // previously the person had to open More → Account and type their
        // own number in by hand before video calling would ever turn on,
        // even though the OS/carrier already knows this device's number
        // on most Indian SIMs. If AppSettingsRepository doesn't have a
        // number saved yet AND the person has already granted
        // READ_PHONE_NUMBERS (part of the app's normal permission
        // onboarding - see DialerPermissions), this reads it straight off
        // the SIM and saves it, the same as if they'd typed it in
        // themselves. If the permission isn't granted yet, or the
        // carrier/SIM simply doesn't expose line1Number (see
        // CarrierDetector.readSimPhoneNumberOrNull's doc comment - a
        // platform/carrier limitation on some SIMs, not something any app
        // can work around), this silently does nothing and
        // AccountScreen's manual entry field remains exactly as it always
        // was, unaffected.
        applicationScope.launch {
            val alreadySaved = appSettingsRepository.settingsFlow.first().myPhoneNumber
            if (alreadySaved.isBlank()) {
                com.ashudialer.app.telecom.CarrierDetector.readSimPhoneNumberOrNull(this@AshuDialerApp)
                    ?.let { autoDetected -> appSettingsRepository.setMyPhoneNumber(autoDetected) }
            }
        }

        watchVideoCallingAvailability()
    }

    /**
     * Video calling only makes sense once the person has signed in AND set their
     * number (VideoCallListenerService.startListening() already gates the actual
     * signaling subscription on exactly this pair of conditions). Previously the
     * *foreground service itself* — with its persistent "Video calling active"
     * notification — was force-started unconditionally at process launch, so it
     * ran 24/7 for every install regardless of whether video calling was ever
     * configured, showing up as constant background battery/activity usage.
     * This now starts the service only when both conditions are actually true,
     * and stops it again the moment either one stops being true (sign-out,
     * clearing the saved number), so the app has no persistent background
     * footprint outside of an active phone call.
     */
    private fun watchVideoCallingAvailability() {
        applicationScope.launch {
            combine(
                authRepository.currentUser,
                appSettingsRepository.settingsFlow
            ) { user, settings -> user != null && settings.myPhoneNumber.isNotBlank() }
                .distinctUntilChanged()
                .collectLatest { available ->
                    if (available) {
                        VideoCallListenerService.start(this@AshuDialerApp)
                    } else {
                        VideoCallListenerService.stop(this@AshuDialerApp)
                    }
                }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // "incoming_call_channel" - this was the missing piece behind
            // "notification center mein kuch nahi aata": CallNotificationHelper
            // has always posted incoming-call notifications against
            // CallNotificationHelper.CHANNEL_ID ("incoming_call_channel"),
            // but only CHANNEL_ONGOING_CALL was ever actually registered here.
            // Android 8+ silently drops any notification posted to a channel
            // ID that was never created via createNotificationChannel() - no
            // crash, no log from the app's own code, the notification (and
            // its Control Center chip, and its heads-up banner) just never
            // appears. IMPORTANCE_HIGH (not LOW, unlike the ongoing-call
            // channel below) is what makes this show as a heads-up banner
            // and is required for setFullScreenIntent to actually take over
            // the screen - a lower importance channel would only add a
            // silent status-bar icon.
            manager.createNotificationChannel(
                NotificationChannel(
                    com.ashudialer.app.telecom.CallNotificationHelper.CHANNEL_ID,
                    "Incoming calls",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Shows the incoming call screen and ringtone controls"
                    setSound(null, null) // ringtone itself is played separately, not via notification sound
                    enableVibration(false) // vibration handled separately too, see PixelInCallService.vibrateForIncomingCall
                    setBypassDnd(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )

            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ONGOING_CALL,
                    "Ongoing call",


                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows the active call notification"
                    setSound(null, null)
                    enableVibration(false)
                }
            )

            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_CALLBACK_REMINDERS,
                    "Callback reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Reminds you to call someone back at the time you picked"
                }
            )

            // Missing until now: this app logged missed calls into its own
            // call-log table but never actually told the person about them
            // with a notification, the way every stock/other dialer does.
            // On MIUI specifically that gap gets silently filled by MIUI's
            // own system dialer's missed-call notification even while this
            // app correctly holds the default-dialer role - from the
            // person's side that looks identical to "my app isn't really
            // the default dialer", when the real issue was just a missing
            // notification here.
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_MISSED_CALL,
                    "Missed calls",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifies you about calls you missed"
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
        }
    }

    companion object {
        const val CHANNEL_ONGOING_CALL = "ongoing_call_channel"
        const val CHANNEL_CALLBACK_REMINDERS = "callback_reminder_channel"
        const val CHANNEL_MISSED_CALL = "missed_call_channel"
    }
}
