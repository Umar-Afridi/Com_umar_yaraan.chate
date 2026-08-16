package com.example.data

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Authenticated(
        val user: FirebaseUser,
        val idToken: String? = null,
        val email: String? = null,
        val password: String? = null,
        val authType: String = "google" // "google" or "email"
    ) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthRepository(private val context: Context) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val prefs: SharedPreferences =
        context.getSharedPreferences("yaraan_auth_prefs", Context.MODE_PRIVATE)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    companion object {
        private const val TAG = "AuthRepository"
        // Server Client ID from google-services.json (client_type: 3)
        const val WEB_CLIENT_ID = "740464208491-r63hohlm9o2lvc40f8gffitrbe6pceq8.apps.googleusercontent.com"
        const val PREF_PRIVACY_ACCEPTED = "pref_privacy_accepted"
        const val PREF_USER_LOGGED_IN = "pref_user_logged_in"
        const val PREF_AUTH_TYPE = "pref_auth_type"
        const val PREF_SAVED_EMAIL = "pref_saved_email"
        const val PREF_SAVED_PASSWORD = "pref_saved_password"
    }

    init {
        checkInitialAuth()
    }

    fun isPrivacyPolicyAccepted(): Boolean {
        return prefs.getBoolean(PREF_PRIVACY_ACCEPTED, true)
    }

    fun setPrivacyPolicyAccepted(accepted: Boolean) {
        prefs.edit().putBoolean(PREF_PRIVACY_ACCEPTED, accepted).apply()
    }

    private fun checkInitialAuth() {
        val currentUser = auth.currentUser
        val isUserLoggedIn = prefs.getBoolean(PREF_USER_LOGGED_IN, false)
        if (currentUser != null && isUserLoggedIn) {
            val authType = prefs.getString(PREF_AUTH_TYPE, "google") ?: "google"
            val email = prefs.getString(PREF_SAVED_EMAIL, null)
            val password = prefs.getString(PREF_SAVED_PASSWORD, null)
            _authState.value = AuthState.Authenticated(
                user = currentUser,
                email = email,
                password = password,
                authType = authType
            )
        } else {
            _authState.value = AuthState.Idle
        }
    }

    suspend fun signInWithGoogle(activity: Activity): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        try {
            _authState.value = AuthState.Loading

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(WEB_CLIENT_ID)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val credentialManager = CredentialManager.create(activity)
            val result = credentialManager.getCredential(activity, request)
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
            val idToken = googleIdTokenCredential.idToken

            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(firebaseCredential).await()
            val user = authResult.user ?: throw Exception("Firebase user is null after Google sign in")

            prefs.edit()
                .putBoolean(PREF_USER_LOGGED_IN, true)
                .putString(PREF_AUTH_TYPE, "google")
                .apply()

            _authState.value = AuthState.Authenticated(
                user = user,
                idToken = idToken,
                authType = "google"
            )

            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign In failed", e)
            val errorMsg = when {
                e.message?.contains("canceled", ignoreCase = true) == true -> "Sign in was cancelled"
                e.message?.contains("network", ignoreCase = true) == true -> "Network error. Please check your internet connection."
                else -> e.localizedMessage ?: "Failed to sign in with Google"
            }
            _authState.value = AuthState.Error(errorMsg)
            Result.failure(Exception(errorMsg))
        }
    }

    suspend fun signInWithEmail(email: String, pass: String): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        try {
            _authState.value = AuthState.Loading
            val authResult = auth.signInWithEmailAndPassword(email.trim(), pass).await()
            val user = authResult.user ?: throw Exception("User is null")

            prefs.edit()
                .putBoolean(PREF_USER_LOGGED_IN, true)
                .putString(PREF_AUTH_TYPE, "email")
                .putString(PREF_SAVED_EMAIL, email.trim())
                .putString(PREF_SAVED_PASSWORD, pass)
                .apply()

            _authState.value = AuthState.Authenticated(
                user = user,
                email = email.trim(),
                password = pass,
                authType = "email"
            )

            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Email Sign In failed", e)
            val friendlyError = when {
                e.message?.contains("invalid-credential") == true || e.message?.contains("wrong-password") == true || e.message?.contains("user-not-found") == true ->
                    "Incorrect email or password. Please try again."
                e.message?.contains("network") == true ->
                    "Network error. Please check your internet connection."
                else -> e.localizedMessage ?: "Email login failed."
            }
            _authState.value = AuthState.Error(friendlyError)
            Result.failure(Exception(friendlyError))
        }
    }

    suspend fun signUpWithEmail(username: String, email: String, pass: String): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        try {
            _authState.value = AuthState.Loading
            val authResult = auth.createUserWithEmailAndPassword(email.trim(), pass).await()
            val user = authResult.user ?: throw Exception("User is null")

            // Update display name
            try {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(username.trim())
                    .build()
                user.updateProfile(profileUpdates).await()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update user profile", e)
            }

            prefs.edit()
                .putBoolean(PREF_USER_LOGGED_IN, true)
                .putString(PREF_AUTH_TYPE, "email")
                .putString(PREF_SAVED_EMAIL, email.trim())
                .putString(PREF_SAVED_PASSWORD, pass)
                .apply()

            _authState.value = AuthState.Authenticated(
                user = user,
                email = email.trim(),
                password = pass,
                authType = "email"
            )

            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Sign Up failed", e)
            val friendlyError = when {
                e.message?.contains("email-already-in-use") == true ->
                    "This email is already registered. Please login instead."
                e.message?.contains("weak-password") == true ->
                    "Password is too weak. Must be at least 6 characters."
                e.message?.contains("invalid-email") == true ->
                    "Please enter a valid email address."
                else -> e.localizedMessage ?: "Registration failed."
            }
            _authState.value = AuthState.Error(friendlyError)
            Result.failure(Exception(friendlyError))
        }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            auth.sendPasswordResetEmail(email.trim()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Password reset failed", e)
            val msg = when {
                e.message?.contains("user-not-found") == true -> "No account found with this email."
                e.message?.contains("invalid-email") == true -> "Invalid email address format."
                else -> e.localizedMessage ?: "Failed to send password reset email."
            }
            Result.failure(Exception(msg))
        }
    }

    fun signOut() {
        auth.signOut()
        prefs.edit()
            .putBoolean(PREF_USER_LOGGED_IN, false)
            .remove(PREF_SAVED_EMAIL)
            .remove(PREF_SAVED_PASSWORD)
            .apply()
        _authState.value = AuthState.Idle
    }

    fun clearError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.Idle
        }
    }
}
