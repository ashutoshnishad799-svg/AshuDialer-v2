package com.ashudialer.app.data

import android.content.Context
import com.ashudialer.app.data.db.LockedNumberDao
import com.ashudialer.app.data.db.LockedNumberEntity
import com.ashudialer.app.data.db.PrivateSpaceDao
import com.ashudialer.app.data.db.PrivateSpaceEntity
import com.ashudialer.app.util.SecureHash
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map

sealed class PrivateSpaceSetupResult {
    data class Success(val backupCode: String) : PrivateSpaceSetupResult()
    data class Failure(val message: String) : PrivateSpaceSetupResult()
}

sealed class PrivateSpaceResetResult {
    object Success : PrivateSpaceResetResult()
    data class Failure(val message: String) : PrivateSpaceResetResult()
}

class PrivateSpaceRepository(
    private val privateSpaceDao: PrivateSpaceDao,
    private val lockedNumberDao: LockedNumberDao,
    private val context: Context
) {

    val isSetUp: Flow<Boolean> = privateSpaceDao.observe().map { it?.isSetUp == true }

    val lockedNumbers: Flow<List<LockedNumberEntity>> = lockedNumberDao.observeAll()

    suspend fun getLockedNumbersSnapshot(): List<LockedNumberEntity> = lockedNumberDao.getAllSnapshot()

    // THE FIX for "a locked number's calls/contact still show up outside
    // Private Space": both isolation toggles default OFF, so a person who
    // never opens Private Space settings sees exactly the same Recents and
    // Contacts they always have - nothing changes behavior until they
    // deliberately turn this on from inside Private Space's own settings
    // (never the main app Settings screen, since these only make sense once
    // Private Space itself is set up).
    //
    // Stored in a small dedicated SharedPreferences file rather than as new
    // columns on PrivateSpaceEntity/AppSettingsRepository's DataStore: this
    // app's Room database deliberately disabled destructive migrations (see
    // AshuDialerDatabase's comment - it used to silently wipe Private
    // Space's password on every schema bump), so adding a real @Database
    // migration for two booleans on a security-sensitive table is a bigger
    // risk than it's worth. SharedPreferences needs no schema/migration at
    // all and is exactly what CallRecorder's own known-silent-source memory
    // already uses for the same reason.
    private val isolationPrefs by lazy {
        context.getSharedPreferences("private_space_isolation", Context.MODE_PRIVATE)
    }
    private val keyHideCallHistory = "hide_locked_numbers_from_recents"
    private val keyHideContacts = "hide_locked_numbers_from_contacts"

    /** When on, a locked number's calls appear ONLY inside Private Space's own call history, not in the main Recents list. */
    val hideLockedCallHistoryFromRecents: Flow<Boolean> = callbackFlow {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == keyHideCallHistory) trySend(prefs.getBoolean(keyHideCallHistory, false))
        }
        trySend(isolationPrefs.getBoolean(keyHideCallHistory, false))
        isolationPrefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { isolationPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /** When on, a locked number's saved contact appears ONLY inside Private Space's own contact list, not in the main Contacts list. */
    val hideLockedContactsFromContactsList: Flow<Boolean> = callbackFlow {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == keyHideContacts) trySend(prefs.getBoolean(keyHideContacts, false))
        }
        trySend(isolationPrefs.getBoolean(keyHideContacts, false))
        isolationPrefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { isolationPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun setHideLockedCallHistoryFromRecents(enabled: Boolean) {
        isolationPrefs.edit().putBoolean(keyHideCallHistory, enabled).apply()
    }

    fun setHideLockedContactsFromContactsList(enabled: Boolean) {
        isolationPrefs.edit().putBoolean(keyHideContacts, enabled).apply()
    }

    /**
     * First-time setup: hashes and stores the chosen password plus a freshly
     * generated backup code, returning the plaintext backup code exactly
     * once so the setup screen can show it to the person to write down -
     * it's never retrievable again after this call, since only its hash
     * persists.
     */
    suspend fun setup(password: String): PrivateSpaceSetupResult {
        if (password.length < 4) {
            return PrivateSpaceSetupResult.Failure("Password must be at least 4 characters")
        }
        val passwordSalt = SecureHash.generateSalt()
        val passwordHash = SecureHash.hash(password, passwordSalt)

        val backupCode = SecureHash.generateBackupCode()
        val backupCodeSalt = SecureHash.generateSalt()
        val backupCodeHash = SecureHash.hash(backupCode, backupCodeSalt)

        privateSpaceDao.save(
            PrivateSpaceEntity(
                isSetUp = true,
                passwordSalt = passwordSalt,
                passwordHash = passwordHash,
                backupCodeSalt = backupCodeSalt,
                backupCodeHash = backupCodeHash
            )
        )
        return PrivateSpaceSetupResult.Success(backupCode)
    }

    suspend fun verifyPassword(attempt: String): Boolean {
        val config = privateSpaceDao.getSnapshot() ?: return false
        if (!config.isSetUp) return false
        return SecureHash.verify(attempt, config.passwordSalt, config.passwordHash)
    }

    /**
     * Recovery path for a forgotten password: verifying the backup code
     * doesn't just unlock this one session, it resets the password to a new
     * one the person chooses right there, and issues a brand new backup
     * code (the old one is single-use - reusing it after a reset would mean
     * anyone who ever saw the old code could reset the password again
     * indefinitely).
     */
    suspend fun resetWithBackupCode(backupCodeAttempt: String, newPassword: String): PrivateSpaceResetResult {
        val config = privateSpaceDao.getSnapshot()
            ?: return PrivateSpaceResetResult.Failure("Private Space isn't set up yet")
        if (!config.isSetUp) return PrivateSpaceResetResult.Failure("Private Space isn't set up yet")

        val codeMatches = SecureHash.verify(backupCodeAttempt.trim(), config.backupCodeSalt, config.backupCodeHash)
        if (!codeMatches) return PrivateSpaceResetResult.Failure("That backup code doesn't match")

        if (newPassword.length < 4) {
            return PrivateSpaceResetResult.Failure("Password must be at least 4 characters")
        }

        val newPasswordSalt = SecureHash.generateSalt()
        val newPasswordHash = SecureHash.hash(newPassword, newPasswordSalt)
        val newBackupCode = SecureHash.generateBackupCode()
        val newBackupCodeSalt = SecureHash.generateSalt()
        val newBackupCodeHash = SecureHash.hash(newBackupCode, newBackupCodeSalt)

        privateSpaceDao.save(
            config.copy(
                passwordSalt = newPasswordSalt,
                passwordHash = newPasswordHash,
                backupCodeSalt = newBackupCodeSalt,
                backupCodeHash = newBackupCodeHash
            )
        )
        return PrivateSpaceResetResult.Success
    }

    /**
     * Normal password change: the person already knows their current
     * password (unlike resetWithBackupCode's forgot-password path) and just
     * wants to set a new one. Deliberately doesn't touch the backup code -
     * that's still valid and unchanged, since regenerating it here would
     * silently invalidate a code the person may have already written down,
     * with no forgotten-password reason forcing that.
     */
    suspend fun changePassword(currentPassword: String, newPassword: String): PrivateSpaceResetResult {
        val config = privateSpaceDao.getSnapshot()
            ?: return PrivateSpaceResetResult.Failure("Private Space isn't set up yet")
        if (!config.isSetUp) return PrivateSpaceResetResult.Failure("Private Space isn't set up yet")

        val currentMatches = SecureHash.verify(currentPassword, config.passwordSalt, config.passwordHash)
        if (!currentMatches) return PrivateSpaceResetResult.Failure("Current password is incorrect")

        if (newPassword.length < 4) {
            return PrivateSpaceResetResult.Failure("New password must be at least 4 characters")
        }

        val newPasswordSalt = SecureHash.generateSalt()
        val newPasswordHash = SecureHash.hash(newPassword, newPasswordSalt)

        privateSpaceDao.save(config.copy(passwordSalt = newPasswordSalt, passwordHash = newPasswordHash))
        return PrivateSpaceResetResult.Success
    }

    /**
     * Full wipe: clears the lock configuration (isSetUp goes back to false,
     * so the next visit shows first-time setup again) and every locked
     * number. Used from "Reset Private Space" in its own settings, a
     * deliberately separate, harder-to-reach action from a normal
     * password change.
     */
    suspend fun resetEverything() {
        privateSpaceDao.clear()
        lockedNumberDao.getAllSnapshot().forEach { lockedNumberDao.delete(it) }
    }

    suspend fun lockNumber(phoneNumber: String, label: String = "") {
        lockedNumberDao.insert(LockedNumberEntity(phoneNumber = phoneNumber, displayLabel = label))
    }

    suspend fun unlockNumber(phoneNumber: String) {
        lockedNumberDao.deleteByNumber(phoneNumber)
    }

    suspend fun isNumberLocked(phoneNumber: String): Boolean {
        val target = com.ashudialer.app.util.normalizePhoneNumberForMatch(phoneNumber)
        if (target.isEmpty()) return false
        return lockedNumberDao.getAllSnapshot().any {
            com.ashudialer.app.util.phoneNumbersMatch(it.phoneNumber, phoneNumber)
        }
    }
}
