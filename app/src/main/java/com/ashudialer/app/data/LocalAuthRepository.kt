package com.ashudialer.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ashudialer.app.util.SecureHash
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.localAuthDataStore by preferencesDataStore(name = "ashu_dialer_local_auth")

sealed class LocalAuthResult {
    data class Success(val user: SignedInUser) : LocalAuthResult()
    data class Failure(val message: String) : LocalAuthResult()
}

/**
 * On-device email/password sign-in, independent of Firebase/Google
 * Sign-In. AuthRepository's Google path silently no-ops without a
 * google-services.json (see its own comments) - that requires Firebase
 * Console project setup only the app's owner can do. This path needs
 * nothing beyond the app itself: the account "lives" only on this device,
 * verified against a locally salted-and-hashed password, the same
 * SecureHash used for Private Space's password.
 *
 * This is a real trade-off worth being upfront about: a local account
 * doesn't sync across devices or survive an uninstall/reinstall the way a
 * real Firebase-backed account would - it's a genuine sign-in for
 * on-device purposes (personalizing the Account screen, gating
 * local-only features), not a substitute for cloud-backed identity.
 */
class LocalAuthRepository(private val context: Context) {

    private val keyIsRegistered = booleanPreferencesKey("is_registered")
    private val keyUid = stringPreferencesKey("uid")
    private val keyEmail = stringPreferencesKey("email")
    private val keyDisplayName = stringPreferencesKey("display_name")
    private val keyPasswordSalt = stringPreferencesKey("password_salt")
    private val keyPasswordHash = stringPreferencesKey("password_hash")
    private val keySignedIn = booleanPreferencesKey("is_signed_in")

    val currentUser: Flow<SignedInUser?> = context.localAuthDataStore.data.map { prefs ->
        val signedIn = prefs[keySignedIn] ?: false
        val registered = prefs[keyIsRegistered] ?: false
        if (signedIn && registered) {
            SignedInUser(
                uid = prefs[keyUid] ?: "",
                displayName = prefs[keyDisplayName],
                email = prefs[keyEmail],
                photoUrl = null
            )
        } else null
    }

    suspend fun isRegistered(): Boolean =
        context.localAuthDataStore.data.first()[keyIsRegistered] ?: false

    /**
     * Creates the local account. Only one local account exists per device
     * (this is a phone-dialer app's account screen, not a multi-user
     * system) - calling this again after registration fails rather than
     * silently overwriting a possibly-different person's account.
     */
    suspend fun register(email: String, password: String, displayName: String): LocalAuthResult {
        val trimmedEmail = email.trim()
        if (!trimmedEmail.contains("@") || !trimmedEmail.contains(".")) {
            return LocalAuthResult.Failure("Enter a valid email address")
        }
        if (password.length < 6) {
            return LocalAuthResult.Failure("Password must be at least 6 characters")
        }
        val alreadyRegistered = isRegistered()
        if (alreadyRegistered) {
            return LocalAuthResult.Failure("An account already exists on this device. Sign in instead.")
        }

        val salt = SecureHash.generateSalt()
        val hash = SecureHash.hash(password, salt)
        val uid = UUID.randomUUID().toString()
        val name = displayName.trim().ifBlank { trimmedEmail.substringBefore("@") }

        context.localAuthDataStore.edit { prefs ->
            prefs[keyIsRegistered] = true
            prefs[keyUid] = uid
            prefs[keyEmail] = trimmedEmail
            prefs[keyDisplayName] = name
            prefs[keyPasswordSalt] = salt
            prefs[keyPasswordHash] = hash
            prefs[keySignedIn] = true
        }
        return LocalAuthResult.Success(SignedInUser(uid, name, trimmedEmail, null))
    }

    suspend fun signIn(email: String, password: String): LocalAuthResult {
        val prefs = context.localAuthDataStore.data.first()
        val registered = prefs[keyIsRegistered] ?: false
        if (!registered) {
            return LocalAuthResult.Failure("No account found on this device. Create one first.")
        }
        val storedEmail = prefs[keyEmail] ?: ""
        if (!storedEmail.equals(email.trim(), ignoreCase = true)) {
            return LocalAuthResult.Failure("Incorrect email or password")
        }
        val salt = prefs[keyPasswordSalt] ?: ""
        val hash = prefs[keyPasswordHash] ?: ""
        if (!SecureHash.verify(password, salt, hash)) {
            return LocalAuthResult.Failure("Incorrect email or password")
        }

        context.localAuthDataStore.edit { it[keySignedIn] = true }
        return LocalAuthResult.Success(
            SignedInUser(prefs[keyUid] ?: "", prefs[keyDisplayName], storedEmail, null)
        )
    }

    suspend fun signOut() {
        context.localAuthDataStore.edit { it[keySignedIn] = false }
    }

    /** Fully removes the local account, distinct from signOut - matches AuthRepository's deleteAccount semantics. */
    suspend fun deleteAccount() {
        context.localAuthDataStore.edit { it.clear() }
    }
}
