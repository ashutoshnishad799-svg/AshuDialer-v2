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
    // Recents: fold every call to the same number on the same calendar day into one
    // row (with a count), even when other numbers were called in between. Off by
    // default so existing behaviour is unchanged; opt in from Settings.
    val groupRecentsByDay: Boolean = false,


    val showSearchBar: Boolean = true,

    val fontSizeIndex: Int = 1,

    // Which look the full-screen incoming-call UI uses: "clean" (default), "center" or "swipe"
    // (see IncomingCallScreen.kt). A value saved by an older build ("aurora", "orbit", "pulse",
    // "classic", "hyper", "ios") is mapped to the nearest current look by
    // IncomingCallStyles.normalize(), so nobody ends up with a broken screen after updating.
    // Stored as a string (not an enum) for the same DataStore-friendliness reason fontSizeIndex is
    // an Int rather than a sealed type.
    val incomingCallStyle: String = "swipe",

    // On a dark theme, draw the incoming-call screen as dark frosted glass. Off = it stays white /
    // light exactly like the reference designs, on every theme.
    val incomingCallGlass: Boolean = true,

    // Dual-SIM outgoing calls. Empty = always show the SIM picker before
    // dialing when there's no per-number routing rule (SimRoutingScreen)
    // for that number and more than one SIM is available - previously
    // there was no picker at all on this path (see placeCallDirect's
    // fallback to `handle = null`, i.e. whatever SIM the OS silently
    // picks), which is the actual bug this setting's picker fixes. A
    // non-empty value is a PhoneAccountHandle.id: skip the picker and
    // always use that SIM for any number with no specific rule, for
    // someone who'd rather set-and-forget than be asked every call.
    val defaultSimAccountId: String = "",

    // Ask for confirmation before every outgoing call placed with more
    // than one SIM available (distinct from defaultSimAccountId's picker,
    // which only appears when there's a genuine choice to make - this can
    // stay on even once a default SIM is set, for a "confirm which SIM,
    // every time" workflow rather than "pick once, never ask again").
    val confirmSimBeforeCall: Boolean = true,

    // Visual weight of buttons/pills across the dialpad, call screen and
    // action buttons - "flat" keeps today's minimal look (no shadow), "raised"
    // adds a subtle drop shadow + slightly stronger border for a more
    // tactile, pressable look for anyone who wants buttons to look more
    // like physical buttons and less like flat labels.
    val buttonDepth: String = "flat",

    // Rotate the gradient ring around the caller avatar during in-call screens.
    // Enabled by default; users can freeze it from Settings.
    val animateCallAvatarRing: Boolean = true
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
    private val keyGroupRecentsByDay = booleanPreferencesKey("group_recents_by_day")
    private val keyShowSearchBar = booleanPreferencesKey("show_search_bar")
    private val keyFontSizeIndex = androidx.datastore.preferences.core.intPreferencesKey("font_size_index")
    private val keyIncomingCallStyle = stringPreferencesKey("incoming_call_style")
    private val keyIncomingCallGlass = booleanPreferencesKey("incoming_call_glass")
    private val keyDefaultSimAccountId = stringPreferencesKey("default_sim_account_id")
    private val keyConfirmSimBeforeCall = booleanPreferencesKey("confirm_sim_before_call")
    private val keyButtonDepth = stringPreferencesKey("button_depth")
    private val keyAnimateCallAvatarRing = booleanPreferencesKey("animate_call_avatar_ring")

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
            groupRecentsByDay = prefs[keyGroupRecentsByDay] ?: false,
            showSearchBar = prefs[keyShowSearchBar] ?: true,
            fontSizeIndex = prefs[keyFontSizeIndex] ?: 1,
            incomingCallStyle = com.ashudialer.app.ui.screens.IncomingCallStyles.normalize(prefs[keyIncomingCallStyle]),
            incomingCallGlass = prefs[keyIncomingCallGlass] ?: true,
            defaultSimAccountId = prefs[keyDefaultSimAccountId] ?: "",
            confirmSimBeforeCall = prefs[keyConfirmSimBeforeCall] ?: true,
            buttonDepth = prefs[keyButtonDepth] ?: "flat",
            animateCallAvatarRing = prefs[keyAnimateCallAvatarRing] ?: true,
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

    suspend fun setGroupRecentsByDay(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyGroupRecentsByDay] = enabled }
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

    suspend fun setIncomingCallGlass(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyIncomingCallGlass] = enabled }
    }

    suspend fun setDefaultSimAccountId(id: String) {
        context.settingsDataStore.edit { it[keyDefaultSimAccountId] = id }
    }

    suspend fun setConfirmSimBeforeCall(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyConfirmSimBeforeCall] = enabled }
    }

    suspend fun setButtonDepth(depth: String) {
        context.settingsDataStore.edit { it[keyButtonDepth] = depth }
    }

    suspend fun setAnimateCallAvatarRing(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyAnimateCallAvatarRing] = enabled }
    }
}
