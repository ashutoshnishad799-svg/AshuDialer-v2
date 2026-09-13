package com.ashudialer.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

    // Live captions (speech-to-text of the other person's voice, shown on
    // the call screen) and type-to-talk (typed text spoken into the call
    // via TTS) - see CallCaptionEngine / TypeToTalkEngine. Off by default
    // for the same reason callRecordingEnabled is above: on a normal SIM
    // call, captioning the other person's voice needs the same protected
    // far-end audio access call recording does, which the Normal build
    // cannot reliably get. The two toggles are independent because a
    // person who can hear fine but wants to type instead of speak (or vice
    // versa) is a completely different situation from someone who wants
    // both, and forcing them together would make one half of the feature
    // unusable for either group.
    val liveCaptionsEnabled: Boolean = false,
    val typeToTalkEnabled: Boolean = false,
    // Stores CaptionLanguage.name (e.g. "HINDI") rather than the enum
    // itself, since DataStore only persists primitives - resolved back to
    // the enum wherever it's read, defaulting to ENGLISH_INDIA to match
    // this app's primary userbase rather than plain ENGLISH_US.
    val captionLanguage: String = "ENGLISH_INDIA",

    val fontSizeIndex: Int = 1
)

class AppSettingsRepository(private val context: Context) {

    private val keyCallRecording = booleanPreferencesKey("call_recording_enabled")
    private val keyAutoRecordAll = booleanPreferencesKey("auto_record_all")
    private val keyAnnounceRecording = booleanPreferencesKey("announce_recording")
    private val keyCloudBackup = booleanPreferencesKey("cloud_backup_enabled")
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
    private val keyLiveCaptionsEnabled = booleanPreferencesKey("live_captions_enabled")
    private val keyTypeToTalkEnabled = booleanPreferencesKey("type_to_talk_enabled")
    private val keyCaptionLanguage = stringPreferencesKey("caption_language")

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            callRecordingEnabled = prefs[keyCallRecording] ?: false,
            autoRecordAll = prefs[keyAutoRecordAll] ?: false,
            announceRecording = prefs[keyAnnounceRecording] ?: false,
            cloudBackupEnabled = prefs[keyCloudBackup] ?: false,
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
            liveCaptionsEnabled = prefs[keyLiveCaptionsEnabled] ?: false,
            typeToTalkEnabled = prefs[keyTypeToTalkEnabled] ?: false,
            captionLanguage = prefs[keyCaptionLanguage] ?: "ENGLISH_INDIA"
        )
    }

    suspend fun setCallRecordingEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyCallRecording] = enabled }
    }

    suspend fun setAutoRecordAll(enabled: Boolean) {
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

    suspend fun setLiveCaptionsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyLiveCaptionsEnabled] = enabled }
    }

    suspend fun setTypeToTalkEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyTypeToTalkEnabled] = enabled }
    }

    suspend fun setCaptionLanguage(languageName: String) {
        context.settingsDataStore.edit { it[keyCaptionLanguage] = languageName }
    }
}
