package com.example.chat_ui.ui.components

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.example.chat_ui.R
import com.example.chat_ui.data.firebase.FirebaseAuthManager
import com.example.chat_ui.data.firebase.FirebaseManager
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

/**
 * Google Sign-In Button Component
 * 
 * Beautiful Material Design button for Google authentication
 */
@Composable
fun GoogleSignInButton(
    onSignInSuccess: (email: String, displayName: String) -> Unit,
    onSignInError: (error: String) -> Unit,
    modifier: Modifier = Modifier,
    isSignedIn: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    
    val credentialManager = remember { CredentialManager.create(context) }
    
    val colorScheme = MaterialTheme.colorScheme
    
    if (!isSignedIn) {
        // Sign In Button
        OutlinedButton(
            onClick = {
                isLoading = true
                scope.launch {
                    try {
                        // Get Web Client ID from google-services.json
                        val webClientId = context.getString(R.string.default_web_client_id)
                        
                        val googleIdOption = GetGoogleIdOption.Builder()
                            .setFilterByAuthorizedAccounts(false)
                            .setServerClientId(webClientId)
                            .build()
                        
                        val request = GetCredentialRequest.Builder()
                            .addCredentialOption(googleIdOption)
                            .build()
                        
                        val result = credentialManager.getCredential(
                            request = request,
                            context = context
                        )
                        
                        val credential = result.credential
                        if (credential is CustomCredential &&
                            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                            
                            // Sign in to Firebase with ID token
                            val signInResult = FirebaseAuthManager.signInWithGoogle(idToken)
                            signInResult.onSuccess { user ->
                                scope.launch {
                                    FirebaseAuthManager.syncSignedInUserProfile(user)
                                }
                                isLoading = false
                                onSignInSuccess(user.email ?: "", user.displayName ?: "")
                            }.onFailure { error ->
                                isLoading = false
                                onSignInError(error.message ?: "Sign-in failed")
                            }
                        } else {
                            isLoading = false
                            onSignInError("Invalid credential type")
                        }
                    } catch (e: GetCredentialException) {
                        isLoading = false
                        Log.e("GoogleSignIn", "Sign-in failed", e)
                        onSignInError("Sign-in failed: ${e.message}")
                    } catch (e: Exception) {
                        isLoading = false
                        Log.e("GoogleSignIn", "Unexpected error", e)
                        onSignInError("Error: ${e.message}")
                    }
                }
            },
            modifier = modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = !isLoading,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, colorScheme.outline),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White,
                contentColor = Color.Black
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = colorScheme.primary
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Google Logo (you need to add google_logo.png to drawable)
                    // For now, using text icon
                    Text(
                        text = "G",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4285F4)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Sign in with Google",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )
                }
            }
        }
    } else {
        // Signed In State
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Checkmark
                Text(
                    text = "✓",
                    fontSize = 20.sp,
                    color = colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Signed in as",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = FirebaseManager.auth.currentUser?.email ?: "Unknown",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onSurface
                    )
                }
                TextButton(
                    onClick = {
                        scope.launch {
                            FirebaseAuthManager.signOut()
                            onSignInError("Signed out")
                        }
                    }
                ) {
                    Text("Sign out", fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * Compact Google Sign-In Status
 */
@Composable
fun GoogleSignInStatus(
    modifier: Modifier = Modifier
) {
    val isSignedIn = FirebaseManager.auth.currentUser != null
    val colorScheme = MaterialTheme.colorScheme
    
    if (isSignedIn) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = colorScheme.primaryContainer,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(
                    text = "✓",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    color = colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Column {
                Text(
                    text = "Google Authenticated",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurfaceVariant
                )
                Text(
                    text = FirebaseManager.auth.currentUser?.email ?: "",
                    fontSize = 10.sp,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}
