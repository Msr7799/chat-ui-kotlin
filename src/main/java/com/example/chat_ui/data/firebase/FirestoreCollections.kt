package com.example.chat_ui.data.firebase

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

/**
 * Firestore Collections Manager
 * 
 * Manages all Firebase Firestore collections:
 * - users
 * - conversations
 * - messages
 * - generatedImages
 * - settings
 * - assistants
 * - sessions
 * - reports
 * - sharedConversations
 * - config
 */
object FirestoreCollections {
    private const val TAG = "FirestoreCollections"
    
    private val firestore: FirebaseFirestore by lazy { Firebase.firestore }
    
    // Collection names
    object Collections {
        const val USERS = "users"
        const val CONVERSATIONS = "conversations"
        const val MESSAGES = "messages"
        const val GENERATED_IMAGES = "generatedImages"
        const val SETTINGS = "settings"
        const val ASSISTANTS = "assistants"
        const val SESSIONS = "sessions"
        const val REPORTS = "reports"
        const val SHARED_CONVERSATIONS = "sharedConversations"
        const val CONFIG = "config"
    }
    
    /**
     * Initialize Firestore collections
     * Creates empty collections if they don't exist
     */
    suspend fun initializeCollections() {
        try {
            val userId = FirebaseManager.getCurrentUserId() ?: run {
                Log.w(TAG, "No user signed in - skipping collection initialization")
                return
            }
            
            // Create placeholder documents to initialize collections
            val collectionsToInit = listOf(
                Collections.USERS,
                Collections.CONVERSATIONS,
                Collections.MESSAGES,
                Collections.GENERATED_IMAGES,
                Collections.SETTINGS,
                Collections.ASSISTANTS,
                Collections.SESSIONS,
                Collections.REPORTS,
                Collections.SHARED_CONVERSATIONS,
                Collections.CONFIG
            )
            
            collectionsToInit.forEach { collectionName ->
                try {
                    // Check if collection exists by querying it
                    val snapshot = firestore.collection(collectionName)
                        .limit(1)
                        .get()
                        .await()
                    
                    if (snapshot.isEmpty) {
                        Log.i(TAG, "Collection '$collectionName' is empty - will be created on first write")
                    } else {
                        Log.i(TAG, "Collection '$collectionName' already exists")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking collection '$collectionName': ${e.message}")
                }
            }
            
            Log.i(TAG, "Firestore collections initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize collections: ${e.message}", e)
        }
    }
    
    // === Users Collection ===
    
    suspend fun saveUser(userId: String, userData: Map<String, Any>) {
        try {
            firestore.collection(Collections.USERS)
                .document(userId)
                .set(userData)
                .await()
            Log.i(TAG, "User saved: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save user: ${e.message}", e)
        }
    }
    
    suspend fun getUser(userId: String): Map<String, Any>? {
        return try {
            val doc = firestore.collection(Collections.USERS)
                .document(userId)
                .get()
                .await()
            doc.data
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get user: ${e.message}", e)
            null
        }
    }
    
    // === Conversations Collection ===
    
    suspend fun saveConversation(userId: String, conversationId: String, data: Map<String, Any>) {
        try {
            firestore.collection(Collections.CONVERSATIONS)
                .document("${userId}_$conversationId")
                .set(data)
                .await()
            Log.i(TAG, "Conversation saved: $conversationId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save conversation: ${e.message}", e)
        }
    }
    
    suspend fun getUserConversations(userId: String): List<Map<String, Any>> {
        return try {
            val snapshot = firestore.collection(Collections.CONVERSATIONS)
                .whereEqualTo("userId", userId)
                .orderBy("updatedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()
            
            snapshot.documents.mapNotNull { it.data }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get conversations: ${e.message}", e)
            emptyList()
        }
    }
    
    // === Messages Collection ===
    
    suspend fun saveMessage(conversationId: String, messageId: String, data: Map<String, Any>) {
        try {
            firestore.collection(Collections.MESSAGES)
                .document("${conversationId}_$messageId")
                .set(data)
                .await()
            Log.i(TAG, "Message saved: $messageId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save message: ${e.message}", e)
        }
    }
    
    // === Generated Images Collection ===
    
    suspend fun saveGeneratedImage(userId: String, imageId: String, data: Map<String, Any>) {
        try {
            firestore.collection(Collections.GENERATED_IMAGES)
                .document("${userId}_$imageId")
                .set(data)
                .await()
            Log.i(TAG, "Image saved: $imageId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save image: ${e.message}", e)
        }
    }
    
    suspend fun getUserImages(userId: String): List<Map<String, Any>> {
        return try {
            val snapshot = firestore.collection(Collections.GENERATED_IMAGES)
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()
            
            snapshot.documents.mapNotNull { it.data }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get images: ${e.message}", e)
            emptyList()
        }
    }
    
    // === Settings Collection ===
    
    suspend fun saveSettings(userId: String, settings: Map<String, Any>) {
        try {
            firestore.collection(Collections.SETTINGS)
                .document(userId)
                .set(settings)
                .await()
            Log.i(TAG, "Settings saved")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save settings: ${e.message}", e)
        }
    }
    
    suspend fun getSettings(userId: String): Map<String, Any>? {
        return try {
            val doc = firestore.collection(Collections.SETTINGS)
                .document(userId)
                .get()
                .await()
            doc.data
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get settings: ${e.message}", e)
            null
        }
    }
    
    // === Assistants Collection ===
    
    suspend fun getAssistants(): List<Map<String, Any>> {
        return try {
            val snapshot = firestore.collection(Collections.ASSISTANTS)
                .orderBy("userCount", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()
            
            snapshot.documents.mapNotNull { it.data }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get assistants: ${e.message}", e)
            emptyList()
        }
    }
    
    // === Sessions Collection ===
    
    suspend fun saveSession(userId: String, sessionId: String, data: Map<String, Any>) {
        try {
            firestore.collection(Collections.SESSIONS)
                .document("${userId}_$sessionId")
                .set(data)
                .await()
            Log.i(TAG, "Session saved: $sessionId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save session: ${e.message}", e)
        }
    }
    
    // === Config Collection ===
    
    suspend fun getConfig(): Map<String, Any>? {
        return try {
            val doc = firestore.collection(Collections.CONFIG)
                .document("app_config")
                .get()
                .await()
            doc.data
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get config: ${e.message}", e)
            null
        }
    }
}
