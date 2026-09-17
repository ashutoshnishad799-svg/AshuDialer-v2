package com.ashudialer.app.data

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

data class SignedInUser(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?
)

sealed class SignInResult {
    data class Success(val user: SignedInUser) : SignInResult()
    data class Failure(val message: String) : SignInResult()
}


class AuthRepository(private val context: Context) {


    private val firebaseAuth: FirebaseAuth? = try {
        if (FirebaseApp.getApps(context).isNotEmpty()) FirebaseAuth.getInstance() else null
    } catch (e: IllegalStateException) {
        Log.w("AuthRepository", "Firebase not configured — sign-in disabled. Add google-services.json.", e)
        null
    }

    private val googleSignInClient: GoogleSignInClient? by lazy {
        if (firebaseAuth == null) return@lazy null
        val webId = webClientId()
        if (webId.isEmpty()) return@lazy null
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webId)
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, options)
    }


    private fun webClientId(): String {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (resId != 0) context.getString(resId) else ""
    }

    val currentUser: Flow<SignedInUser?> = firebaseAuth?.let { auth ->
        callbackFlow {
            val listener = FirebaseAuth.AuthStateListener { a ->
                trySend(a.currentUser?.toSignedInUser())
            }
            auth.addAuthStateListener(listener)
            awaitClose { auth.removeAuthStateListener(listener) }
        }
    } ?: flowOf(null)

    // THE DEEP FIX for "video call always shows Coming soon": the entire
    // rest of the video-calling stack (WebRtcCallManager,
    // VideoCallSignalingRepository, VideoCallListenerService,
    // VideoCallActivity) was already fully built and working - it was
    // gated behind isSignedIn(), and until now the ONLY way to become
    // signed in was a manual Google Sign-In flow the person had to find
    // and complete from More → Account before video calling would ever
    // turn on. No stock dialer requires that: on a real phone, video
    // calling "just works" the moment you have a number.
    //
    // ensureSignedIn() gives every install of this app its own silent,
    // anonymous Firebase identity - no Google account, no UI, no user
    // action - the very first time it's needed. An anonymous FirebaseUser
    // still has a real, stable uid (it persists across app restarts once
    // created - Firebase writes it to local storage - so this only
    // actually calls the network once per install, not once per launch),
    // which is all currentUserUidOrNull()/isSignedIn() below, and every
    // caller of them (VideoCallActivity, VideoCallListenerService,
    // AshuDialerApp.watchVideoCallingAvailability), ever actually needed -
    // none of them read displayName/email/photoUrl, they only need a uid
    // to key signaling documents by. The optional Google Sign-In flow
    // below is left fully intact for anyone who wants a recognizable name
    // on their account/backup - it simply upgrades the same
    // FirebaseAuth session (linkWithCredential preserves the existing
    // anonymous uid rather than swapping to a new one, so any in-flight
    // calls or state keyed by uid aren't disrupted by signing in for real
    // partway through the app's lifetime).
    //
    // Failure here (no network, Firebase misconfigured, Anonymous
    // provider disabled in the Firebase console) is deliberately silent
    // and non-blocking: video calling simply stays unavailable exactly as
    // it already did before this existed, and every other part of the
    // dialer (regular calls, contacts, everything else) is completely
    // unaffected either way.
    suspend fun ensureSignedIn(): SignedInUser? {
        val auth = firebaseAuth ?: return null
        auth.currentUser?.let { return it.toSignedInUser() }
        return try {
            val result = auth.signInAnonymously().await()
            result.user?.toSignedInUser()
        } catch (e: Exception) {
            Log.w("AuthRepository", "Anonymous sign-in failed — video calling stays unavailable until network/Firebase is reachable.", e)
            null
        }
    }

    fun isSignedIn(): Boolean = firebaseAuth?.currentUser != null


    fun currentUserUidOrNull(): String? = firebaseAuth?.currentUser?.uid


    fun signInIntent(): Intent? = googleSignInClient?.signInIntent

    suspend fun handleSignInResult(data: Intent?): SignInResult {
        val auth = firebaseAuth
            ?: return SignInResult.Failure("Sign-in isn't set up yet.")
        return try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data).await()
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            // Upgrading from the silent anonymous session (see
            // ensureSignedIn above) to a real Google account, rather than
            // signing in fresh, so the uid stays the same and anything
            // already keyed by it (in-flight video call signaling,
            // phone_directory entries) keeps working uninterrupted. Falls
            // back to a normal credential sign-in if there was no
            // anonymous user to upgrade from (e.g. ensureSignedIn was
            // never called, or failed) or if linking itself fails for any
            // other reason (e.g. this Google account is already linked to
            // a different Firebase user elsewhere).
            val existingAnonymousUser = auth.currentUser?.takeIf { it.isAnonymous }
            val authResult = if (existingAnonymousUser != null) {
                try {
                    existingAnonymousUser.linkWithCredential(credential).await()
                } catch (e: Exception) {
                    Log.w("AuthRepository", "Linking anonymous session to Google account failed, falling back to fresh sign-in", e)
                    auth.signInWithCredential(credential).await()
                }
            } else {
                auth.signInWithCredential(credential).await()
            }
            val user = authResult.user?.toSignedInUser()
                ?: return SignInResult.Failure("Sign-in succeeded but no user was returned.")
            SignInResult.Success(user)
        } catch (e: Exception) {
            SignInResult.Failure(e.message ?: "Sign-in failed.")
        }
    }

    suspend fun signOut() {
        firebaseAuth?.signOut()
        try {
            googleSignInClient?.signOut()?.await()
        } catch (e: Exception) {

        }
    }

    suspend fun deleteAccount(): Result<Unit> {
        val user = firebaseAuth?.currentUser ?: return Result.failure(IllegalStateException("No signed-in user"))
        return try {
            user.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun FirebaseUser.toSignedInUser() = SignedInUser(
        uid = uid,
        displayName = displayName,
        email = email,
        photoUrl = photoUrl?.toString()
    )
}
