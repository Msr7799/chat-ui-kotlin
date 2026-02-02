package com.example.chat_ui.utils

import com.example.chat_ui.data.firebase.FirebaseManager
import kotlinx.coroutines.tasks.await

/**
 * Firebase Authentication Helper
 * Provides utility functions for Firebase Auth token retrieval
 */
object FirebaseAuthHelper {
    
    /**
     * Get Firebase ID Token for the current user
     * Required for Veo Backend API authentication
     * 
     * @param forceRefresh If true, forces token refresh from server
     * @return Firebase ID Token string, or null if user not authenticated
     */
    suspend fun getFirebaseIdToken(forceRefresh: Boolean = false): String? {
        return try {
            val currentUser = FirebaseManager.auth.currentUser
            if (currentUser != null) {
                val tokenResult = currentUser.getIdToken(forceRefresh).await()
                tokenResult.token
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Check if user is authenticated and can get ID token
     */
    suspend fun isUserAuthenticated(): Boolean {
        return getFirebaseIdToken() != null
    }
    
    /**
     * Get current user UID
     */
    fun getCurrentUserUid(): String? {
        return FirebaseManager.auth.currentUser?.uid
    }
    
    /**
     * Get current user email
     */
    fun getCurrentUserEmail(): String? {
        return FirebaseManager.auth.currentUser?.email
    }
}
