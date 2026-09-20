// Settings model adapted from ShizuCallRecorder's AppPreferences (github.com/kitsumed/ShizuCallRecorder), GPLv3+.
package com.ashudialer.app.appcalls.recording

import android.content.Context
import androidx.core.content.edit
import com.ashudialer.app.appcalls.scrcpy.ScrcpyAudioCodec
import com.ashudialer.app.appcalls.scrcpy.ScrcpyAudioSource

/**
 * All call-recording settings, stored in plain SharedPreferences.
 *
 * Why SharedPreferences and not DataStore: these are read synchronously from a BroadcastReceiver
 * and a foreground service at the exact moment a call changes state, where a suspending read is
 * too slow / awkward. Ever Dialer's recorder module uses the same approach for the same reason.
 */
class RecordingPrefs(private val context: Context) {

    enum class IgnoreContactsMode(val key: String) {
        NONE("none"), ALL("all"), SELECTED("selected");

        companion object {
            fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: NONE
        }
    }

    enum class StorageMode(val key: String) {
        /** Music/Ashu Dialer via MediaStore - visible to file managers and the Recordings screen. */
        PUBLIC_MUSIC("public_music"),
        /** App-private storage - other apps and file managers can't see these files. */
        PRIVATE("private");

        companion object {
            fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: PUBLIC_MUSIC
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---- Master switch + what to record -------------------------------------------------
    var callRecordingEnabled: Boolean
        get() = prefs.getBoolean("call_recording_enabled", false)
        set(v) = prefs.edit { putBoolean("call_recording_enabled", v) }

    // DEFAULT IS false ON PURPOSE. Turning the master "Call recording" switch on
    // must only make manual recording available (the Record button on the call
    // screen). It must NOT silently start recording every call - that has to be a
    // separate, visible opt-in ("Auto-record all calls").
    var autoRecordIncoming: Boolean
        get() = prefs.getBoolean("auto_record_incoming", false)
        set(v) = prefs.edit { putBoolean("auto_record_incoming", v) }

    var autoRecordOutgoing: Boolean
        get() = prefs.getBoolean("auto_record_outgoing", false)
        set(v) = prefs.edit { putBoolean("auto_record_outgoing", v) }

    /** Wait until the call is actually answered before recording (skips ringing / unanswered calls). */
    var recordOnAnswerOnly: Boolean
        get() = prefs.getBoolean("record_on_answer", true)
        set(v) = prefs.edit { putBoolean("record_on_answer", v) }

    var recordWhatsApp: Boolean
        get() = prefs.getBoolean("record_whatsapp", false)
        set(v) = prefs.edit { putBoolean("record_whatsapp", v) }

    var recordTelegram: Boolean
        get() = prefs.getBoolean("record_telegram", false)
        set(v) = prefs.edit { putBoolean("record_telegram", v) }

    val anyAppCallRecordingEnabled: Boolean get() = recordWhatsApp || recordTelegram

    // ---- Filters ------------------------------------------------------------------------
    var ignoreAnonymousIncoming: Boolean
        get() = prefs.getBoolean("ignore_anonymous_incoming", false)
        set(v) = prefs.edit { putBoolean("ignore_anonymous_incoming", v) }

    var ignoreCrossCountryIncoming: Boolean
        get() = prefs.getBoolean("ignore_cross_country_incoming", false)
        set(v) = prefs.edit { putBoolean("ignore_cross_country_incoming", v) }

    var ignoreCrossCountryOutgoing: Boolean
        get() = prefs.getBoolean("ignore_cross_country_outgoing", false)
        set(v) = prefs.edit { putBoolean("ignore_cross_country_outgoing", v) }

    var ignoreContactsModeIncoming: IgnoreContactsMode
        get() = IgnoreContactsMode.fromKey(prefs.getString("ignore_contacts_mode_incoming", null))
        set(v) = prefs.edit { putString("ignore_contacts_mode_incoming", v.key) }

    var ignoreContactsModeOutgoing: IgnoreContactsMode
        get() = IgnoreContactsMode.fromKey(prefs.getString("ignore_contacts_mode_outgoing", null))
        set(v) = prefs.edit { putString("ignore_contacts_mode_outgoing", v.key) }

    var ignoredNumbersIncoming: Set<String>
        get() = prefs.getStringSet("ignored_numbers_incoming", emptySet())?.toSet().orEmpty()
        set(v) = prefs.edit { putStringSet("ignored_numbers_incoming", v) }

    var ignoredNumbersOutgoing: Set<String>
        get() = prefs.getStringSet("ignored_numbers_outgoing", emptySet())?.toSet().orEmpty()
        set(v) = prefs.edit { putStringSet("ignored_numbers_outgoing", v) }

    // ---- Audio --------------------------------------------------------------------------
    var audioSource: ScrcpyAudioSource
        get() = ScrcpyAudioSource.fromKeyOrDefault(prefs.getString("audio_source", null))
        set(v) = prefs.edit { putString("audio_source", v.cliKey) }

    var audioCodec: ScrcpyAudioCodec
        get() = ScrcpyAudioCodec.fromKeyOrDefault(prefs.getString("audio_codec", null))
        set(v) = prefs.edit { putString("audio_codec", v.cliKey) }

    /** 0 means "use the codec's own default". */
    var audioBitRate: Int
        get() = prefs.getInt("audio_bitrate", 0)
        set(v) = prefs.edit { putInt("audio_bitrate", v) }

    // ---- Storage / naming ---------------------------------------------------------------
    var storageMode: StorageMode
        get() = StorageMode.fromKey(prefs.getString("storage_mode", null))
        set(v) = prefs.edit { putString("storage_mode", v.key) }

    var fileNameTemplate: String
        get() = prefs.getString("file_name_template", DEFAULT_FILE_NAME_TEMPLATE) ?: DEFAULT_FILE_NAME_TEMPLATE
        set(v) = prefs.edit { putString("file_name_template", v) }

    // ---- Notifications / feedback -------------------------------------------------------
    var showRecordingNotification: Boolean
        get() = prefs.getBoolean("show_recording_notification", true)
        set(v) = prefs.edit { putBoolean("show_recording_notification", v) }

    // Off by default: an "Open / Share / Delete" notification after EVERY call is
    // noise for anyone who auto-records. Still one toggle away in Recording settings.
    var postRecordingActionsNotification: Boolean
        get() = prefs.getBoolean("post_recording_notification", false)
        set(v) = prefs.edit { putBoolean("post_recording_notification", v) }

    var vibrateOnStartStop: Boolean
        get() = prefs.getBoolean("vibrate_on_start_stop", true)
        set(v) = prefs.edit { putBoolean("vibrate_on_start_stop", v) }

    // Off by default: the "Recording started / saved" pop-up appeared on every call.
    var showToasts: Boolean
        get() = prefs.getBoolean("show_toasts", false)
        set(v) = prefs.edit { putBoolean("show_toasts", v) }

    // ---- Shizuku management -------------------------------------------------------------
    /** Let the app start/stop Shizuku itself using Shizuku's "start via intent" auth key. */
    var shizukuAutoManage: Boolean
        get() = prefs.getBoolean("shizuku_auto_manage", false)
        set(v) = prefs.edit { putBoolean("shizuku_auto_manage", v) }

    var shizukuStartOnRecordOnly: Boolean
        get() = prefs.getBoolean("shizuku_start_on_record", false)
        set(v) = prefs.edit { putBoolean("shizuku_start_on_record", v) }

    var shizukuKeepAlive: Boolean
        get() = prefs.getBoolean("shizuku_keep_alive", false)
        set(v) = prefs.edit { putBoolean("shizuku_keep_alive", v) }

    var shizukuAuthKey: String
        get() = prefs.getString("shizuku_auth_key", "") ?: ""
        set(v) = prefs.edit { putString("shizuku_auth_key", v.trim()) }

    // ---- Auto-delete --------------------------------------------------------------------
    var autoDeleteByTimeEnabled: Boolean
        get() = prefs.getBoolean("auto_delete_time_enabled", false)
        set(v) = prefs.edit { putBoolean("auto_delete_time_enabled", v) }

    var autoDeleteByTimeValue: Int
        get() = prefs.getInt("auto_delete_time_value", 7)
        set(v) = prefs.edit { putInt("auto_delete_time_value", v.coerceAtLeast(1)) }

    /** "hours" or "days". */
    var autoDeleteByTimeUnit: String
        get() = prefs.getString("auto_delete_time_unit", "days") ?: "days"
        set(v) = prefs.edit { putString("auto_delete_time_unit", v) }

    var autoDeleteBySpaceEnabled: Boolean
        get() = prefs.getBoolean("auto_delete_space_enabled", false)
        set(v) = prefs.edit { putBoolean("auto_delete_space_enabled", v) }

    var autoDeleteBySpaceValue: Int
        get() = prefs.getInt("auto_delete_space_value", 500)
        set(v) = prefs.edit { putInt("auto_delete_space_value", v.coerceAtLeast(1)) }

    /** "mb" or "gb". */
    var autoDeleteBySpaceUnit: String
        get() = prefs.getString("auto_delete_space_unit", "mb") ?: "mb"
        set(v) = prefs.edit { putString("auto_delete_space_unit", v) }

    // ---- Setup / onboarding -------------------------------------------------------------
    var setupGuideSeen: Boolean
        get() = prefs.getBoolean("setup_guide_seen", false)
        set(v) = prefs.edit { putBoolean("setup_guide_seen", v) }

    companion object {
        private const val PREFS_NAME = "ashu_call_recording_prefs"
        const val DEFAULT_FILE_NAME_TEMPLATE = "{contact_name}_{date}_{direction}"
    }
}
