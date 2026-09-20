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

    /** PIN storage and the attempt lockout (see PrivateSpaceGuard). Created here so no constructor has to change. */
    val guard: PrivateSpaceGuard by lazy { PrivateSpaceGuard(context) }

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

    /** Result of one unlock attempt, so the screen can say "wrong" and "wait 30 s" differently. */
    sealed class UnlockResult {
        object Success : UnlockResult()
        data class Wrong(val attemptsLeft: Int, val lockedForMs: Long) : UnlockResult()
        data class Locked(val remainingMs: Long) : UnlockResult()
    }

    /**
     * Unlock with the password OR the numeric PIN (whichever the person typed - a PIN is only tried when
     * the entry is 4-6 digits and a PIN has been set). Both count toward ONE shared lockout, so
     * splitting guesses between the two cannot double the number of tries.
     *
     * The check for "is it locked" comes FIRST and the hash is not even computed while locked, so a
     * locked attempt cannot be used to learn anything or to burn CPU.
     */
    suspend fun unlock(attempt: String): UnlockResult {
        val locked = guard.lockedForMs(PrivateSpaceGuard.Target.UNLOCK)
        if (locked > 0) return UnlockResult.Locked(locked)

        val config = privateSpaceDao.getSnapshot()
        val ok = config != null && config.isSetUp && (
            SecureHash.verify(attempt, config.passwordSalt, config.passwordHash) ||
                (guard.hasPin && attempt.length in PrivateSpaceGuard.PIN_MIN..PrivateSpaceGuard.PIN_MAX &&
                    attempt.all { it in '0'..'9' } && guard.verifyPin(attempt))
            )
        val wait = guard.recordResult(PrivateSpaceGuard.Target.UNLOCK, ok)
        return if (ok) UnlockResult.Success
        else UnlockResult.Wrong(attemptsLeft = guard.attemptsLeftInGroup(PrivateSpaceGuard.Target.UNLOCK), lockedForMs = wait)
    }

    /** Kept for callers that only need a yes/no and never show lockout (none in the UI any more). */
    suspend fun verifyPassword(attempt: String): Boolean = unlock(attempt) is UnlockResult.Success

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

        // Recovery has its OWN lockout counter. Before this, the backup code (12 digits) could be tried without
        // limit, which made recovery the weakest way into Private Space.
        val locked = guard.lockedForMs(PrivateSpaceGuard.Target.RECOVERY)
        if (locked > 0) return PrivateSpaceResetResult.Failure("Too many wrong codes. Try again in ${Lockout.format(locked)}.")

        val codeMatches = SecureHash.verify(backupCodeAttempt.trim(), config.backupCodeSalt, config.backupCodeHash)
        val wait = guard.recordResult(PrivateSpaceGuard.Target.RECOVERY, codeMatches)
        if (!codeMatches) {
            return PrivateSpaceResetResult.Failure(
                if (wait > 0) "Too many wrong codes. Try again in ${Lockout.format(wait)}."
                else "That backup code doesn't match (${guard.attemptsLeftInGroup(PrivateSpaceGuard.Target.RECOVERY)} tries left before a wait)"
            )
        }

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
        // The person proved ownership with the backup code, so any old PIN is dropped too (they may have
        // forgotten it as well) and the unlock lockout is cleared so they can get straight back in.
        guard.clearPin()
        guard.recordResult(PrivateSpaceGuard.Target.UNLOCK, true)
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
        guard.resetAll()
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
