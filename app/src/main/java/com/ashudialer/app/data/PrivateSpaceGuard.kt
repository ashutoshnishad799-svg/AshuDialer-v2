package com.ashudialer.app.data

import android.content.Context
import com.ashudialer.app.util.SecureHash

/**
 * Two things Private Space was missing:
 *
 *  1. A numeric PIN that unlocks it (in addition to the password, never instead of it), and
 *  2. A lockout, so a wrong guess costs time. Before this, the unlock screen counted failed attempts
 *     only to decide whether to show the "Forgot password?" link; nothing ever stopped a person from
 *     trying again immediately, forever - and the backup-code recovery screen had no limit either.
 *
 * WHERE IT IS STORED. Not in the Room table: that database deliberately has no destructive
 * migration (a schema bump used to wipe the Private Space password), so adding columns to the
 * security table is a bigger risk than it is worth. A small private SharedPreferences file needs no
 * schema at all, the same reason the isolation toggles live in one (see PrivateSpaceRepository).
 * The PIN is hashed with the same salted PBKDF2 as the password (SecureHash) and never stored as
 * digits.
 *
 * WHAT THE LOCKOUT DOES AND DOES NOT PROTECT. It stops guessing through this app. It cannot stop
 * someone who copies the stored hash off a rooted phone and tries guesses on their own computer: a
 * 4-6 digit PIN is small enough that PBKDF2 only slows that down. The lockout is what makes a PIN
 * worth having; it is not a substitute for a device screen lock.
 */
class PrivateSpaceGuard(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("private_space_guard", Context.MODE_PRIVATE)

    /**
     * Identifies the current boot of the phone (Settings.Global.BOOT_COUNT, available since Android 7;
     * this app needs 10+). Together with SystemClock.elapsedRealtime() - a clock that counts up from
     * boot and that the person cannot set - it lets the lockout tell "same boot, so the monotonic
     * clock is trustworthy" from "the phone restarted, so it is not".
     */
    private fun bootId(): Int = try {
        android.provider.Settings.Global.getInt(appContext.contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1)
    } catch (_: Exception) { -1 }

    // ------------------------------------------------------------------------------------------
    // PIN
    // ------------------------------------------------------------------------------------------

    val hasPin: Boolean get() = prefs.getString(KEY_PIN_HASH, null)?.isNotEmpty() == true

    /** Sets (or replaces) the PIN. Returns an error message, or null on success. */
    fun setPin(pin: String): String? {
        if (pin.length !in PIN_MIN..PIN_MAX || !pin.all { it in '0'..'9' }) {
            return "PIN must be $PIN_MIN to $PIN_MAX digits"
        }
        if (isTooObvious(pin)) return "Choose a PIN that is not all the same digit or a simple run like 1234"
        val salt = SecureHash.generateSalt()
        prefs.edit().putString(KEY_PIN_SALT, salt).putString(KEY_PIN_HASH, SecureHash.hash(pin, salt)).apply()
        return null
    }

    fun clearPin() {
        prefs.edit().remove(KEY_PIN_SALT).remove(KEY_PIN_HASH).apply()
    }

    /** True when [attempt] is the PIN. Does not touch the lockout - callers use [recordResult]. */
    fun verifyPin(attempt: String): Boolean {
        val salt = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val hash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return SecureHash.verify(attempt, salt, hash)
    }

    // ------------------------------------------------------------------------------------------
    // Lockout
    // ------------------------------------------------------------------------------------------

    /**
     * Which secret is being guessed. Each has its OWN counter, so failing the PIN cannot be used to lock
     * the owner out of the backup code (or the reverse), and the recovery path is limited separately.
     */
    enum class Target(internal val key: String) { UNLOCK("unlock"), RECOVERY("recovery") }

    /** Milliseconds left before another attempt is allowed; 0 when the person may try now. */
    fun lockedForMs(target: Target, now: Long = System.currentTimeMillis()): Long {
        val wallDeadline = prefs.getLong(target.key + KEY_LOCKED_UNTIL, 0L)
        if (wallDeadline == 0L) return 0L
        // Same boot: use the monotonic deadline. Moving the phone's clock forward (to make a wait "pass")
        // or back has no effect on it.
        val boot = bootId()
        val sameBoot = boot != -1 && prefs.getInt(target.key + KEY_BOOT, -2) == boot
        if (sameBoot) {
            val elapsedDeadline = prefs.getLong(target.key + KEY_LOCKED_UNTIL_ELAPSED, 0L)
            return Lockout.remainingMs(elapsedDeadline, android.os.SystemClock.elapsedRealtime())
        }
        // After a restart the monotonic clock started over, so only the wall clock is left. Known limit:
        // restarting AND moving the clock forward can shorten a wait. Android offers an app no time source
        // that survives a reboot and cannot be set, so this cannot be closed without a server.
        return Lockout.remainingMs(wallDeadline, now)
    }

    /** Wrong attempts since the last success, for showing "3 attempts left". */
    fun failuresInGroup(target: Target): Int = prefs.getInt(target.key + KEY_FAILS, 0) % Lockout.ATTEMPTS_PER_GROUP

    fun attemptsLeftInGroup(target: Target): Int = Lockout.ATTEMPTS_PER_GROUP - failuresInGroup(target)

    /**
     * Records the outcome of one attempt and returns how long the person must now wait (0 = no wait).
     * A success clears everything. The counters and the "locked until" time are persisted, so
     * force-stopping the app or rebooting the phone does not reset them.
     */
    fun recordResult(target: Target, success: Boolean, now: Long = System.currentTimeMillis()): Long {
        val edit = prefs.edit()
        if (success) {
            edit.putInt(target.key + KEY_FAILS, 0).putInt(target.key + KEY_STRIKES, 0).putLong(target.key + KEY_LOCKED_UNTIL, 0L)
                .putLong(target.key + KEY_LOCKED_UNTIL_ELAPSED, 0L).apply()
            return 0L
        }
        val fails = prefs.getInt(target.key + KEY_FAILS, 0) + 1
        var strikes = prefs.getInt(target.key + KEY_STRIKES, 0)
        val reachedGroupEnd = fails % Lockout.ATTEMPTS_PER_GROUP == 0
        var waitNow = 0L
        if (reachedGroupEnd) {
            strikes += 1
            waitNow = Lockout.waitMs(strikes)
            edit.putLong(target.key + KEY_LOCKED_UNTIL, now + waitNow)
                .putLong(target.key + KEY_LOCKED_UNTIL_ELAPSED, android.os.SystemClock.elapsedRealtime() + waitNow)
                .putInt(target.key + KEY_BOOT, bootId())
        }
        edit.putInt(target.key + KEY_FAILS, fails).putInt(target.key + KEY_STRIKES, strikes).apply()
        // Returned straight from what was just decided. SharedPreferences.apply() writes asynchronously,
        // so reading the value back here could still see the previous one.
        return waitNow
    }

    /** "Reset Private Space" wipes everything, including the PIN and both counters. */
    fun resetAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PIN_MIN = 4
        const val PIN_MAX = 6
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_FAILS = "_fails"
        private const val KEY_STRIKES = "_strikes"
        private const val KEY_LOCKED_UNTIL = "_locked_until"
        private const val KEY_LOCKED_UNTIL_ELAPSED = "_locked_until_elapsed"
        private const val KEY_BOOT = "_boot"

        /** All the same digit (0000) or a straight run up or down (1234, 4321) - the first things anyone tries. */
        fun isTooObvious(pin: String): Boolean {
            if (pin.all { it == pin[0] }) return true
            val asc = pin.zipWithNext().all { (a, b) -> b - a == 1 }
            val desc = pin.zipWithNext().all { (a, b) -> a - b == 1 }
            return asc || desc
        }
    }
}

/**
 * The lockout schedule as plain functions of numbers, so it is easy to reason about and to test on its
 * own. After every 5 consecutive wrong attempts the person must wait: 30 s, then 60 s, 2 min, 4 min ...
 * doubling each time and capped at 1 hour. With that schedule trying every 4-digit PIN takes an attacker
 * about 83 days, a 6-digit PIN about 23 years, and the 12-digit backup code far longer than that.
 */
object Lockout {
    const val ATTEMPTS_PER_GROUP = 5
    private const val BASE_WAIT_MS = 30_000L
    private const val MAX_WAIT_MS = 3_600_000L

    /** Wait imposed by the [strike]-th completed group of failures (1 = the first group). */
    fun waitMs(strike: Int): Long {
        if (strike <= 0) return 0L
        // Shift is capped so a large strike count cannot overflow before the min() below applies.
        val shift = (strike - 1).coerceAtMost(20)
        return minOf(BASE_WAIT_MS shl shift, MAX_WAIT_MS)
    }

    fun remainingMs(lockedUntil: Long, now: Long): Long = (lockedUntil - now).coerceAtLeast(0L)

    /** "45 s", "2 min 10 s", "1 h" - what the unlock screen shows while counting down. */
    fun format(ms: Long): String {
        val totalSec = (ms + 999) / 1000
        return when {
            totalSec < 60 -> "$totalSec s"
            totalSec < 3600 -> { val m = totalSec / 60; val s = totalSec % 60; if (s == 0L) "$m min" else "$m min $s s" }
            else -> "${totalSec / 3600} h"
        }
    }
}
