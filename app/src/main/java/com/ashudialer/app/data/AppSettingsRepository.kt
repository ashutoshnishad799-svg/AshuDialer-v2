package com.ashudialer.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "pixel_dialer_settings")

data class AppSettings(
    // Off by default: this app cannot reliably capture the other side of a
    // call on a normal, non-rooted phone (see RecordingGuideScreen). Showing
    // the record button out of the box, before the person has read that
    // explanation, sets an expectation the app usually can't meet. The
    // person now has to deliberately turn this on from Settings, which is
    // exactly where the guide/explanation lives.
    val callRecordingEnabled: Boolean = false,
    val autoRecordAll: Boolean = false,
    val announceRecording: Boolean = false,
    val cloudBackupEnabled: Boolean = false,

    // Sub-toggles under callRecordingEnabled, for WhatsApp/Telegram VoIP call recording
    // specifically (see AppCallNotificationListenerService in the :appcalls module). Off by
    // default even once callRecordingEnabled is on: this path needs its own Shizuku grant and a
    // separate Notification Access permission, so it shouldn't silently activate the first time
    // someone turns on native call recording.
    val recordWhatsAppCallsEnabled: Boolean = false,
    val recordTelegramCallsEnabled: Boolean = false,

    val myPhoneNumber: String = "",


    val ledFlashForAlerts: Boolean = false,
    val spamProtectionEnabled: Boolean = true,
    val silenceUnknownCallers: Boolean = false,
    val flagInternationalNumbers: Boolean = true,

    val vibrateOnButtonPress: Boolean = true,


    val alwaysFullScreenIncoming: Boolean = false,

    val keepCallsInNotifications: Boolean = false,

    val backEndsCall: Boolean = false,

    val disableProximitySensor: Boolean = false,


    val showContactThumbnails: Boolean = true,
    val showPhoneNumbers: Boolean = false,
    val useRelativeDate: Boolean = true,


    val showSearchBar: Boolean = true,

    val fontSizeIndex: Int = 1,

    // Which visual style the full-screen incoming-call UI uses. "aurora" is
    // the existing default (soft multi-hue glow behind frosted glass, per-
    // theme backdrop) - kept as the default so nobody's incoming-call screen
    // changes just from updating the app. "orbit" and "pulse" are two
    // additional liquid-glass styles with a different mood/animation
    // character each; see IncomingCallScreen.kt for what each one looks
    // like. Stored as a string (not an enum) for the same DataStore-
    // friendliness reason fontSizeIndex is an Int rather than a sealed type.
    val incomingCallStyle: String = "aurora"
)

class AppSettingsRepository(private val context: Context) {

    private val keyCallRecording = booleanPreferencesKey("call_recording_enabled")
    private val keyAutoRecordAll = booleanPreferencesKey("auto_record_all")
    private val keyAnnounceRecording = booleanPreferencesKey("announce_recording")
    private val keyCloudBackup = booleanPreferencesKey("cloud_backup_enabled")
    private val keyRecordWhatsAppCalls = booleanPreferencesKey("record_whatsapp_calls_enabled")
    private val keyRecordTelegramCalls = booleanPreferencesKey("record_telegram_calls_enabled")
    private val keyMyPhoneNumber = stringPreferencesKey("my_phone_number")
    private val keyLedFlash = booleanPreferencesKey("led_flash_for_alerts")
    private val keySpamProtection = booleanPreferencesKey("spam_protection_enabled")
    private val keySilenceUnknown = booleanPreferencesKey("silence_unknown_callers")
    private val keyFlagInternational = booleanPreferencesKey("flag_international_numbers")
    private val keyVibrateOnButton = booleanPreferencesKey("vibrate_on_button_press")
    private val keyFullScreenIncoming = booleanPreferencesKey("always_full_screen_incoming")
    private val keyKeepCallsInNotifications = booleanPreferencesKey("keep_calls_in_notifications")
    private val keyBackEndsCall = booleanPreferencesKey("back_ends_call")
    private val keyDisableProximity = booleanPreferencesKey("disable_proximity_sensor")
    private val keyShowThumbnails = booleanPreferencesKey("show_contact_thumbnails")
    private val keyShowPhoneNumbers = booleanPreferencesKey("show_phone_numbers")
    private val keyRelativeDate = booleanPreferencesKey("use_relative_date")
    private val keyShowSearchBar = booleanPreferencesKey("show_search_bar")
    private val keyFontSizeIndex = androidx.datastore.preferences.core.intPreferencesKey("font_size_index")
    private val keyIncomingCallStyle = stringPreferencesKey("incoming_call_style")

    /**
     * Ticks whenever any call-recording preference changes. SharedPreferences writes made directly
     * by the recording screens don't touch DataStore, so without this the Settings / in-call screens
     * would keep showing a stale master switch until the next unrelated DataStore write.
     */
    private val recordingPrefsTicks: Flow<Long> = kotlinx.coroutines.flow.callbackFlow {
        val sp = context.getSharedPreferences("ashu_call_recording_prefs", Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(System.nanoTime())
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        trySend(0L)
        awaitClose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val settingsFlow: Flow<AppSettings> = kotlinx.coroutines.flow.combine(
        context.settingsDataStore.data,
        recordingPrefsTicks
    ) { prefs, _ -> prefs }.map { prefs ->
        AppSettings(
            callRecordingEnabled = recordingPrefs.callRecordingEnabled,
            autoRecordAll = recordingPrefs.autoRecordIncoming || recordingPrefs.autoRecordOutgoing,
            announceRecording = prefs[keyAnnounceRecording] ?: false,
            cloudBackupEnabled = prefs[keyCloudBackup] ?: false,
            recordWhatsAppCallsEnabled = recordingPrefs.recordWhatsApp,
            recordTelegramCallsEnabled = recordingPrefs.recordTelegram,
            myPhoneNumber = prefs[keyMyPhoneNumber] ?: "",
            ledFlashForAlerts = prefs[keyLedFlash] ?: false,
            spamProtectionEnabled = prefs[keySpamProtection] ?: true,
            silenceUnknownCallers = prefs[keySilenceUnknown] ?: false,
            flagInternationalNumbers = prefs[keyFlagInternational] ?: true,
            vibrateOnButtonPress = prefs[keyVibrateOnButton] ?: true,
            alwaysFullScreenIncoming = prefs[keyFullScreenIncoming] ?: false,
            keepCallsInNotifications = prefs[keyKeepCallsInNotifications] ?: false,
            backEndsCall = prefs[keyBackEndsCall] ?: false,
            disableProximitySensor = prefs[keyDisableProximity] ?: false,
            showContactThumbnails = prefs[keyShowThumbnails] ?: true,
            showPhoneNumbers = prefs[keyShowPhoneNumbers] ?: false,
            useRelativeDate = prefs[keyRelativeDate] ?: true,
            showSearchBar = prefs[keyShowSearchBar] ?: true,
            fontSizeIndex = prefs[keyFontSizeIndex] ?: 1,
            incomingCallStyle = prefs[keyIncomingCallStyle] ?: "aurora",
        )
    }

    // The recorder itself (phone-state receiver + foreground service in :appcalls) reads its
    // settings synchronously from RecordingPrefs, because it can't await a DataStore read at the
    // instant a call changes state. These four are still exposed here so the Settings screen and
    // the in-call screen (which observe settingsFlow) keep working - every write is mirrored into
    // RecordingPrefs so the two can never disagree.
    private val recordingPrefs get() = com.ashudialer.app.appcalls.recording.RecordingPrefs(context)

    suspend fun setCallRecordingEnabled(enabled: Boolean) {
        recordingPrefs.callRecordingEnabled = enabled
        context.settingsDataStore.edit { it[keyCallRecording] = enabled }
    }

    suspend fun setRecordWhatsAppCallsEnabled(enabled: Boolean) {
        recordingPrefs.recordWhatsApp = enabled
        context.settingsDataStore.edit { it[keyRecordWhatsAppCalls] = enabled }
    }

    suspend fun setRecordTelegramCallsEnabled(enabled: Boolean) {
        recordingPrefs.recordTelegram = enabled
        context.settingsDataStore.edit { it[keyRecordTelegramCalls] = enabled }
    }

    suspend fun setAutoRecordAll(enabled: Boolean) {
        recordingPrefs.autoRecordIncoming = enabled
        recordingPrefs.autoRecordOutgoing = enabled
        context.settingsDataStore.edit { it[keyAutoRecordAll] = enabled }
    }

    suspend fun setAnnounceRecording(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyAnnounceRecording] = enabled }
    }

    suspend fun setCloudBackupEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyCloudBackup] = enabled }
    }

    suspend fun setMyPhoneNumber(number: String) {
        context.settingsDataStore.edit { it[keyMyPhoneNumber] = number }
    }

    suspend fun setLedFlashForAlerts(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyLedFlash] = enabled }
    }

    suspend fun setSpamProtectionEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[keySpamProtection] = enabled }
    }

    suspend fun setSilenceUnknownCallers(enabled: Boolean) {
        context.settingsDataStore.edit { it[keySilenceUnknown] = enabled }
    }

    suspend fun setFlagInternationalNumbers(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyFlagInternational] = enabled }
    }

    suspend fun setVibrateOnButtonPress(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyVibrateOnButton] = enabled }
    }

    suspend fun setAlwaysFullScreenIncoming(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyFullScreenIncoming] = enabled }
    }

    suspend fun setKeepCallsInNotifications(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyKeepCallsInNotifications] = enabled }
    }

    suspend fun setBackEndsCall(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyBackEndsCall] = enabled }
    }

    suspend fun setDisableProximitySensor(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyDisableProximity] = enabled }
    }

    suspend fun setShowContactThumbnails(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyShowThumbnails] = enabled }
    }

    suspend fun setShowPhoneNumbers(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyShowPhoneNumbers] = enabled }
    }

    suspend fun setUseRelativeDate(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyRelativeDate] = enabled }
    }

    suspend fun setShowSearchBar(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyShowSearchBar] = enabled }
    }

    suspend fun setFontSizeIndex(index: Int) {
        context.settingsDataStore.edit { it[keyFontSizeIndex] = index }
    }

    suspend fun setIncomingCallStyle(style: String) {
        context.settingsDataStore.edit { it[keyIncomingCallStyle] = style }
    }
}
