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

    fun isSignedIn(): Boolean = firebaseAuth?.currentUser != null


    fun currentUserUidOrNull(): String? = firebaseAuth?.currentUser?.uid


    fun signInIntent(): Intent? = googleSignInClient?.signInIntent

    suspend fun handleSignInResult(data: Intent?): SignInResult {
        val auth = firebaseAuth
            ?: return SignInResult.Failure("Sign-in isn't set up yet.")
        return try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data).await()
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
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
