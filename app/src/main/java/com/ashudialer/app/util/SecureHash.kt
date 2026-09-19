package com.ashudialer.app.util

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted PBKDF2 hashing for Private Space's password/PIN and its backup
 * recovery code. Neither is ever stored as plaintext - only a random salt
 * and the resulting hash are persisted (see PrivateSpaceEntity), and
 * verification re-derives the hash from the attempt and compares.
 *
 * PBKDF2 rather than a single unsalted SHA-256 pass specifically because a
 * short PIN (this is a phone unlock code, not a full password - people will
 * often set 4-6 digits) has a tiny keyspace; a plain hash of a 6-digit PIN
 * can be brute-forced near-instantly if the hash ever leaks (e.g. a rooted
 * device, a backup extraction). 120,000 iterations makes each guess
 * meaningfully expensive without making legitimate unlock attempts feel
 * slow (tens of milliseconds on a modern phone).
 */
object SecureHash {
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256

    fun generateSalt(): String {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun hash(secret: String, saltBase64: String): String {
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val spec = PBEKeySpec(secret.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hashBytes = factory.generateSecret(spec).encoded
        return Base64.encodeToString(hashBytes, Base64.NO_WRAP)
    }

    /**
     * Constant-time-ish comparison isn't critical here (this isn't a
     * networked auth check where timing attacks are practical - it's a
     * local on-device comparison), but using a simple equality on the
     * derived hash is still correct: two different secrets need
     * astronomically unlikely luck to produce the same PBKDF2 output, so an
     * exact match on the hash is exactly as reliable as matching the
     * original secret would be.
     */
    fun verify(attempt: String, saltBase64: String, expectedHash: String): Boolean {
        return hash(attempt, saltBase64) == expectedHash
    }

    /**
     * A human-typeable backup/recovery code - grouped digits (e.g.
     * "4829-1957-3062") rather than a long random string, since this is
     * something the person needs to be able to write down or read back to
     * themselves if they forget their Private Space password. Generated
     * once at setup and shown exactly once (see PrivateSpaceSetupScreen) -
     * only its hash is ever persisted, same as the password.
     */
    fun generateBackupCode(): String {
        val random = SecureRandom()
        val groups = (1..3).map { (0..9999).let { random.nextInt(10_000) }.toString().padStart(4, '0') }
        return groups.joinToString("-")
    }
}
