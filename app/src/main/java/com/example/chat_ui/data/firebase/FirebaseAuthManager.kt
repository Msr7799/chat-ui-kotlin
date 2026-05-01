package com.example.chat_ui.data.firebase

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.example.chat_ui.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Firebase Authentication Manager */
object FirebaseAuthManager {
    private const val TAG = "FirebaseAuthManager"

    private fun toUserFacingAuthException(error: Throwable): Exception {
        val message = error.message.orEmpty()
        return when {
            error is NoCredentialException ->
                Exception(
                    "No Google credential was returned. Add a Google account to the emulator or choose a different account and try again."
                )
            message.contains("Requests from this Android client application", ignoreCase = true) ->
                Exception(
                    "Firebase blocked requests from this Android app. Check Firebase/Google Cloud API key restrictions and confirm the Android app package name plus SHA-1/SHA-256 fingerprints are allowed."
                )
            else -> Exception(message.ifBlank { "Google Sign-In failed" }, error)
        }
    }

    /**
     * Sign in with Google using Credential Manager This shows the Google account picker and signs
     * in to Firebase
     */
    suspend fun signInWithGoogleOneTap(context: Context): Result<FirebaseUser> =
            withContext(Dispatchers.Main) {
                try {
                    val webClientId = context.getString(R.string.default_web_client_id)

                    Log.d(TAG, "Starting Google Sign-In with Web Client ID: $webClientId")
                    val credentialManager = CredentialManager.create(context)

                    // Configure Google Sign-In options
                    val googleIdOption =
                            GetGoogleIdOption.Builder()
                                    .setFilterByAuthorizedAccounts(false)
                                    .setServerClientId(webClientId)
                                    .setAutoSelectEnabled(false)
                                    .build()

                    // Build the credential request
                    val request =
                            GetCredentialRequest.Builder()
                                    .addCredentialOption(googleIdOption)
                                    .build()

                    Log.d(TAG, "Requesting credentials...")
                    // Get credentials (this shows the Google account picker)
                    val result =
                            credentialManager.getCredential(request = request, context = context)

                    Log.d(TAG, "Credential received: ${result.credential.type}")

                    // Handle the result
                    val credential = result.credential
                    if (credential is CustomCredential &&
                                    credential.type ==
                                            GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                    ) {
                        val googleIdTokenCredential =
                                GoogleIdTokenCredential.createFrom(credential.data)
                        val idToken = googleIdTokenCredential.idToken

                        // Sign in to Firebase with the Google ID token
                        signInWithGoogle(idToken)
                    } else {
                        Result.failure(Exception("Unexpected credential type"))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Google Sign-In failed: ${e.message}", e)
                    Result.failure(toUserFacingAuthException(e))
                }
            }

    /** Sign in with Google ID Token */
    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = FirebaseManager.auth.signInWithCredential(credential).await()
            val user = result.user

            if (user != null) {
                Log.i(TAG, "Google Sign-In successful: ${user.email}")
                Result.success(user)
            } else {
                Result.failure(Exception("Sign in failed: No user returned"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed: ${e.message}", e)
            Result.failure(toUserFacingAuthException(e))
        }
    }

    /**
     * Sync the signed-in user profile to Firebase databases.
     *
     * This is intentionally separate from the auth step so slow first-time writes
     * do not make the UI think sign-in itself failed.
     */
    suspend fun syncSignedInUserProfile(user: FirebaseUser): Result<Unit> {
        return try {
            val saved =
                    FirebaseDatabaseManager.saveUserProfile(
                            userId = user.uid,
                            email = user.email ?: "",
                            name = user.displayName ?: "",
                            photoUrl = user.photoUrl?.toString()
                    )

            if (saved) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to save user profile"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "User profile sync failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** Sign in with Email and Password */
    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = FirebaseManager.auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user

            if (user != null) {
                Log.i(TAG, "Email Sign-In successful: ${user.email}")
                Result.success(user)
            } else {
                Result.failure(Exception("Sign in failed: No user returned"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Email Sign-In failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** Create account with Email and Password */
    suspend fun createAccountWithEmail(
            email: String,
            password: String,
            displayName: String
    ): Result<FirebaseUser> {
        return try {
            val result =
                    FirebaseManager.auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user

            if (user != null) {
                Log.i(TAG, "Account created successfully: ${user.email}")

                // Save user to Realtime Database
                FirebaseDatabaseManager.saveUserProfile(
                        userId = user.uid,
                        email = email,
                        name = displayName,
                        photoUrl = null
                )

                Result.success(user)
            } else {
                Result.failure(Exception("Account creation failed: No user returned"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Account creation failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** Send password reset email */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            FirebaseManager.auth.sendPasswordResetEmail(email).await()
            Log.i(TAG, "Password reset email sent to: $email")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send password reset email: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** Sign out */
    fun signOut() {
        FirebaseManager.signOut()
    }
}
