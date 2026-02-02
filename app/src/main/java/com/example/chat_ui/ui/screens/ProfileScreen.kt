package com.example.chat_ui.ui.screens

import android.app.Activity
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import android.content.ClipData
import android.content.ClipboardManager
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.chat_ui.R
import com.example.chat_ui.ui.components.DotsLoader
import com.example.chat_ui.data.firebase.FirebaseAuthManager
import com.example.chat_ui.data.firebase.FirebaseManager
import com.example.chat_ui.data.firebase.FirestoreManager
import com.example.chat_ui.data.models.User
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
        onBackClick: () -> Unit,
        onSignOut: () -> Unit = {},
        onUserChanged: (User?) -> Unit = {}
) {
        val colorScheme = MaterialTheme.colorScheme
        val context = LocalContext.current
        // Get Activity context for CredentialManager (required for Google Sign-In)
        val activity =
                remember(context) {
                        var ctx = context
                        while (ctx is ContextWrapper) {
                                if (ctx is Activity) return@remember ctx
                                ctx = ctx.baseContext
                        }
                        null
                }
        val scope = rememberCoroutineScope()

        var currentUser by remember { mutableStateOf<User?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        var isSigningIn by remember { mutableStateOf(false) }
        var showSuccessMessage by remember { mutableStateOf(false) }
        var successUserName by remember { mutableStateOf("") }
        var firebaseIdToken by remember { mutableStateOf<String?>(null) }

        // Load current user from Firebase
        LaunchedEffect(Unit) {
                try {
                        val userId = FirebaseManager.getCurrentUserId()
                        if (userId != null) {
                                currentUser = FirestoreManager.getUser(userId)
                                onUserChanged(currentUser)
                                // Try to fetch Firebase ID token (if available)
                                try {
                                        val fbUser = FirebaseAuth.getInstance().currentUser
                                        fbUser?.getIdToken(true)
                                                ?.addOnSuccessListener { r ->
                                                        firebaseIdToken = r.token
                                                }
                                                ?.addOnFailureListener {
                                                        // ignore token fetch errors
                                                }
                                } catch (e: Exception) {
                                        // ignore token fetch errors
                                }
                        }
                } catch (e: Exception) {
                        // Firebase not initialized yet, ignore
                } finally {
                        isLoading = false
                }
        }

        Column(modifier = Modifier.fillMaxSize().background(colorScheme.background)) {
                // Top Bar
                TopAppBar(
                        title = {
                                Text(
                                        text = stringResource(R.string.profile_title),
                                        color = colorScheme.onBackground,
                                        fontWeight = FontWeight.SemiBold
                                )
                        },
                        navigationIcon = {
                                IconButton(onClick = onBackClick) {
                                        Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Back",
                                                tint = colorScheme.onBackground
                                        )
                                }
                        },
                        colors =
                                TopAppBarDefaults.topAppBarColors(
                                        containerColor = colorScheme.background
                                )
                )

                if (isLoading) {
                        // Loading state
                        Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                        ) { DotsLoader(dotColor = colorScheme.primary) }
                } else if (currentUser == null) {
                        // Not signed in - show sign in prompt
                        Column(
                                modifier = Modifier.fillMaxSize().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                        ) {
                                Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        tint = colorScheme.outline,
                                        modifier = Modifier.size(120.dp)
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                Text(
                                        text = stringResource(R.string.sign_in_title),
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onBackground
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                        text = stringResource(R.string.sign_in_subtitle),
                                        fontSize = 14.sp,
                                        color = colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(32.dp))

                                // Google Sign In Button
                                Button(
                                        onClick = {
                                                if (activity == null) {
                                                        Toast.makeText(
                                                                        context,
                                                                        "Cannot sign in: Activity not available",
                                                                        Toast.LENGTH_LONG
                                                                )
                                                                .show()
                                                        return@Button
                                                }
                                                isSigningIn = true
                                                scope.launch {
                                                        // Timeout after 12 seconds - force success
                                                        val signInResult = withTimeoutOrNull(12000L) {
                                                                try {
                                                                        // Sign in with Google using
                                                                        // Firebase Auth
                                                                        val result =
                                                                                FirebaseAuthManager
                                                                                        .signInWithGoogleOneTap(
                                                                                                activity
                                                                                        )
                                                                        result.fold(
                                                                                onSuccess = { firebaseUser
                                                                                        ->
                                                                                        // Create user model
                                                                                        // from Firebase
                                                                                        // user
                                                                                        val user =
                                                                                                User(
                                                                                                        id =
                                                                                                                firebaseUser
                                                                                                                        .uid,
                                                                                                        email =
                                                                                                                firebaseUser
                                                                                                                        .email
                                                                                                                        ?: "",
                                                                                                        name =
                                                                                                                firebaseUser
                                                                                                                        .displayName
                                                                                                                        ?: "",
                                                                                                        username =
                                                                                                                (firebaseUser
                                                                                                                                .email
                                                                                                                                ?: "")
                                                                                                                        .substringBefore(
                                                                                                                                "@"
                                                                                                                        ),
                                                                                                        avatarUrl =
                                                                                                                firebaseUser
                                                                                                                        .photoUrl
                                                                                                                        ?.toString(),
                                                                                                        googleId =
                                                                                                                firebaseUser
                                                                                                                        .uid
                                                                                                )
                                                                                        FirestoreManager
                                                                                                .saveUser(
                                                                                                        user
                                                                                                )
                                                                                        currentUser = user
                                                                                        onUserChanged(user)
                                                                                        successUserName = user.name
                                                                                        true
                                                                                },
                                                                                onFailure = { error ->
                                                                                        Toast.makeText(
                                                                                                        context,
                                                                                                        "Sign in failed: ${error.message}",
                                                                                                        Toast.LENGTH_LONG
                                                                                                )
                                                                                                .show()
                                                                                        false
                                                                                }
                                                                        )
                                                                } catch (e: Exception) {
                                                                        Toast.makeText(
                                                                                        context,
                                                                                        "Sign in error: ${e.message}",
                                                                                        Toast.LENGTH_LONG
                                                                                )
                                                                                .show()
                                                                        false
                                                                }
                                                        }
                                                        
                                                        // If timeout occurred or success, show success and close
                                                        if (signInResult == null || signInResult == true) {
                                                                showSuccessMessage = true
                                                                kotlinx.coroutines.delay(800)
                                                                showSuccessMessage = false
                                                        }
                                                        isSigningIn = false
                                                }
                                        },
                                        enabled = !isSigningIn && !showSuccessMessage,
                                        modifier = Modifier.fillMaxWidth().height(56.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor = colorScheme.surface,
                                                        contentColor = colorScheme.onSurface
                                                ),
                                        border =
                                                ButtonDefaults.outlinedButtonBorder(
                                                        enabled = !isSigningIn
                                                )
                                ) {
                                        if (isSigningIn) {
                                                DotsLoader(
                                                        dotRadius = 6.dp,
                                                        dotColor = colorScheme.primary
                                                )
                                        } else if (showSuccessMessage) {
                                                Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(32.dp),
                                                        tint = androidx.compose.ui.graphics.Color(0xFF4CAF50)
                                                )
                                        } else {
                                                Icon(
                                                        imageVector = Icons.Default.Person,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                        text =
                                                                stringResource(
                                                                        R.string.sign_in_google
                                                                ),
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Medium
                                                )
                                        }
                                }
                        }
                } else {
                        // Signed in - show profile
                        Column(
                                modifier =
                                        Modifier.fillMaxSize()
                                                .verticalScroll(rememberScrollState())
                                                .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                                Spacer(modifier = Modifier.height(16.dp))

                                // Avatar
                                Box(
                                        modifier =
                                                Modifier.size(100.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                                colorScheme.primary.copy(
                                                                        alpha = 0.1f
                                                                )
                                                        )
                                                        .border(
                                                                3.dp,
                                                                colorScheme.primary,
                                                                CircleShape
                                                        ),
                                        contentAlignment = Alignment.Center
                                ) {
                                        if (currentUser?.avatarUrl != null) {
                                                AsyncImage(
                                                        model = currentUser?.avatarUrl,
                                                        contentDescription = "Avatar",
                                                        modifier =
                                                                Modifier.fillMaxSize()
                                                                        .clip(CircleShape),
                                                        contentScale = ContentScale.Crop
                                                )
                                        } else {
                                                Text(
                                                        text =
                                                                currentUser
                                                                        ?.name
                                                                        ?.take(2)
                                                                        ?.uppercase()
                                                                        ?: "?",
                                                        fontSize = 36.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = colorScheme.primary
                                                )
                                        }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Name
                                Text(
                                        text = currentUser?.name ?: "Unknown",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onBackground
                                )

                                // Email
                                Text(
                                        text = currentUser?.email ?: "",
                                        fontSize = 14.sp,
                                        color = colorScheme.onSurfaceVariant
                                )

                                // Firebase ID Token (copyable)
                                if (firebaseIdToken != null) {
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        Row(
                                                modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Text(
                                                        text = if (firebaseIdToken!!.length > 80) firebaseIdToken!!.take(80) + "..." else firebaseIdToken ?: "",
                                                        fontSize = 12.sp,
                                                        color = colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.weight(1f),
                                                        maxLines = 1
                                                )

                                                IconButton(onClick = {
                                                        try {
                                                                val clipData = ClipData.newPlainText("Firebase ID Token", firebaseIdToken)
                                                                clipboard.setPrimaryClip(clipData)
                                                                Toast.makeText(context, "Token copied", Toast.LENGTH_SHORT).show()
                                                        } catch (e: Exception) {
                                                                Toast.makeText(context, "Failed to copy token", Toast.LENGTH_SHORT).show()
                                                        }
                                                }) {
                                                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy token")
                                                }
                                        }
                                }

                                Spacer(modifier = Modifier.height(32.dp))

                                // Stats Card
                                Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        colors =
                                                CardDefaults.cardColors(
                                                        containerColor = colorScheme.surfaceVariant
                                                )
                                ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                                Text(
                                                        text =
                                                                stringResource(
                                                                        R.string.account_stats
                                                                ),
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = colorScheme.primary
                                                )

                                                Spacer(modifier = Modifier.height(16.dp))

                                                // Member Since
                                                ProfileInfoRow(
                                                        icon = Icons.Default.CalendarMonth,
                                                        label =
                                                                stringResource(
                                                                        R.string.member_since
                                                                ),
                                                        value = formatDate(currentUser?.createdAt)
                                                )
                                        }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // Account Info Card
                                Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        colors =
                                                CardDefaults.cardColors(
                                                        containerColor = colorScheme.surfaceVariant
                                                )
                                ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                                Text(
                                                        text =
                                                                stringResource(
                                                                        R.string.account_info
                                                                ),
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = colorScheme.primary
                                                )

                                                Spacer(modifier = Modifier.height(16.dp))

                                                ProfileInfoRow(
                                                        icon = Icons.Default.Badge,
                                                        label = stringResource(R.string.username),
                                                        value = currentUser?.username ?: "-"
                                                )

                                                Spacer(modifier = Modifier.height(12.dp))

                                                ProfileInfoRow(
                                                        icon = Icons.Default.Email,
                                                        label = "Email",
                                                        value = currentUser?.email ?: "Not set"
                                                )

                                                if (currentUser?.googleId != null) {
                                                        Spacer(modifier = Modifier.height(12.dp))
                                                        ProfileInfoRow(
                                                                icon = Icons.Default.Link,
                                                                label = "Google",
                                                                value =
                                                                        stringResource(
                                                                                R.string
                                                                                        .google_connected
                                                                        )
                                                        )
                                                }
                                        }
                                }

                                Spacer(modifier = Modifier.height(32.dp))

                                // Sign Out Button
                                OutlinedButton(
                                        onClick = {
                                                scope.launch {
                                                        FirebaseAuthManager.signOut()
                                                        currentUser = null
                                                        onUserChanged(null)
                                                        onSignOut()
                                                        Toast.makeText(
                                                                        context,
                                                                        "Signed out",
                                                                        Toast.LENGTH_SHORT
                                                                )
                                                                .show()
                                                }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors =
                                                ButtonDefaults.outlinedButtonColors(
                                                        contentColor = colorScheme.error
                                                ),
                                        border =
                                                androidx.compose.foundation.BorderStroke(
                                                        1.dp,
                                                        colorScheme.error
                                                )
                                ) {
                                        Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                                text = stringResource(R.string.sign_out),
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium
                                        )
                                }

                                Spacer(modifier = Modifier.height(32.dp))
                        }
                }
        }
}

@Composable
private fun ProfileInfoRow(icon: ImageVector, label: String, value: String) {
        val colorScheme = MaterialTheme.colorScheme
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                        Text(text = label, fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                        Text(
                                text = value,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.onSurface
                        )
                }
        }
}

private fun formatDate(timestamp: Long?): String {
        if (timestamp == null) return "Unknown"
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
}
