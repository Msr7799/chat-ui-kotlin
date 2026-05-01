package com.example.chat_ui.data.firebase

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Firebase Realtime Database Manager */
object FirebaseDatabaseManager {
    private const val TAG = "FirebaseDatabaseManager"
    
    // Error callback for UI notification
    var onError: ((String) -> Unit)? = null

    /** Save user profile */
    suspend fun saveUserProfile(userId: String, email: String, name: String, photoUrl: String?): Boolean {
        return try {
            val userRef = FirebaseManager.database.getReference("users/$userId")
            val userData =
                    mapOf(
                            "uid" to userId,
                            "email" to email,
                            "name" to name,
                            "photoUrl" to photoUrl,
                            "createdAt" to System.currentTimeMillis(),
                            "updatedAt" to System.currentTimeMillis()
                    )
            userRef.setValue(userData).await()
            Log.i(TAG, "User profile saved: $email")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save user profile: ${e.message}", e)
            onError?.invoke("Failed to save profile: ${e.message}")
            false
        }
    }

    /** Get user profile as Flow */
    fun getUserProfile(userId: String): Flow<Map<String, Any>?> = callbackFlow {
        val userRef = FirebaseManager.database.getReference("users/$userId")

        val listener =
                object : ValueEventListener {
                    @Suppress("UNCHECKED_CAST")
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val userData = snapshot.value as? Map<String, Any>
                        trySend(userData)
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "Failed to load user profile: ${error.message}")
                        trySend(null)
                    }
                }

        userRef.addValueEventListener(listener)

        awaitClose { userRef.removeEventListener(listener) }
    }

    /** Save conversation */
    suspend fun saveConversation(
            conversationId: String,
            title: String,
            model: String,
            messages: List<Map<String, Any>>
    ): Boolean {
        return try {
            // Ensure user is signed in (anonymously if needed)
            var userId = FirebaseManager.getCurrentUserId()
            if (userId == null) {
                // Sign in anonymously if no user
                try {
                    val result = FirebaseManager.auth.signInAnonymously().await()
                    userId = result.user?.uid
                    Log.i(TAG, "Signed in anonymously for conversation save: $userId")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sign in anonymously: ${e.message}", e)
                    onError?.invoke("Authentication failed")
                    return false
                }
            }

            if (userId == null) {
                onError?.invoke("User not authenticated")
                return false
            }
            val conversationRef =
                    FirebaseManager.database.getReference("conversations/$userId/$conversationId")

            val conversationData =
                    mapOf(
                            "id" to conversationId,
                            "title" to title,
                            "model" to model,
                            "messages" to messages,
                            "createdAt" to System.currentTimeMillis(),
                            "updatedAt" to System.currentTimeMillis()
                    )

            conversationRef.setValue(conversationData).await()
            Log.i(TAG, "Conversation saved: $title")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save conversation: ${e.message}", e)
            onError?.invoke("Failed to save conversation: ${e.message}")
            false
        }
    }

    /** Get conversations as Flow */
    fun getConversations(limit: Int = 20): Flow<List<Map<String, Any>>> = callbackFlow {
        var userId = FirebaseManager.getCurrentUserId()

        // If no user, try to sign in anonymously
        if (userId == null) {
            try {
                val result = FirebaseManager.auth.signInAnonymously().await()
                userId = result.user?.uid
                Log.i(TAG, "Signed in anonymously for conversations fetch: $userId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sign in anonymously: ${e.message}", e)
                trySend(emptyList())
                close()
                return@callbackFlow
            }
        }

        if (userId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val conversationsRef =
                FirebaseManager.database
                        .getReference("conversations/$userId")
                        .orderByChild("updatedAt")
                        .limitToLast(limit)

        val listener =
                object : ValueEventListener {
                    @Suppress("UNCHECKED_CAST")
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val conversations = mutableListOf<Map<String, Any>>()
                        snapshot.children.forEach { child ->
                            (child.value as? Map<String, Any>)?.let { conversations.add(it) }
                        }
                        trySend(conversations.reversed()) // Most recent first
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "Failed to load conversations: ${error.message}")
                        trySend(emptyList())
                    }
                }

        conversationsRef.addValueEventListener(listener)

        awaitClose { conversationsRef.removeEventListener(listener) }
    }

    /** Delete conversation */
    suspend fun deleteConversation(conversationId: String): Boolean {
        return try {
            val userId = FirebaseManager.getCurrentUserId()
            if (userId == null) {
                onError?.invoke("User not authenticated")
                return false
            }
            val conversationRef =
                    FirebaseManager.database.getReference("conversations/$userId/$conversationId")

            conversationRef.removeValue().await()
            Log.i(TAG, "Conversation deleted: $conversationId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete conversation: ${e.message}", e)
            onError?.invoke("Failed to delete conversation: ${e.message}")
            false
        }
    }

    /** Save generated image */
    suspend fun saveGeneratedImage(
            imageId: String,
            prompt: String,
            imageUrl: String,
            model: String,
            cloudinaryPublicId: String? = null,
            width: Int? = null,
            height: Int? = null
    ): Boolean {
        return try {
            var userId = FirebaseManager.getCurrentUserId()
            if (userId == null) {
                try {
                    val result = FirebaseManager.auth.signInAnonymously().await()
                    userId = result.user?.uid
                    Log.i(TAG, "Signed in anonymously for generated image save: $userId")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sign in anonymously for image save: ${e.message}", e)
                    onError?.invoke("Authentication failed")
                    return false
                }
            }

            if (userId == null) {
                onError?.invoke("User not authenticated")
                return false
            }
            val imageRef = FirebaseManager.database.getReference("images/$userId/$imageId")

            val imageData =
                    mapOf(
                            "id" to imageId,
                            "prompt" to prompt,
                            "imageUrl" to imageUrl,
                            "model" to model,
                            "cloudinaryPublicId" to cloudinaryPublicId,
                            "width" to width,
                            "height" to height,
                            "createdAt" to System.currentTimeMillis()
                    )

            imageRef.setValue(imageData).await()
            Log.i(TAG, "Generated image saved")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save generated image: ${e.message}", e)
            onError?.invoke("Failed to save image: ${e.message}")
            false
        }
    }

    /** Get generated images as Flow */
    fun getGeneratedImages(limit: Int = 60): Flow<List<Map<String, Any>>> = callbackFlow {
        val userId = FirebaseManager.getCurrentUserId()
        if (userId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val imagesRef =
                FirebaseManager.database
                        .getReference("images/$userId")
                        .orderByChild("createdAt")
                        .limitToLast(limit)

        val listener =
                object : ValueEventListener {
                    @Suppress("UNCHECKED_CAST")
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val images = mutableListOf<Map<String, Any>>()
                        snapshot.children.forEach { child ->
                            (child.value as? Map<String, Any>)?.let { images.add(it) }
                        }
                        trySend(images.reversed()) // Most recent first
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "Failed to load images: ${error.message}")
                        trySend(emptyList())
                    }
                }

        imagesRef.addValueEventListener(listener)

        awaitClose { imagesRef.removeEventListener(listener) }
    }


    /** Load one full conversation only when the user opens it. */
    suspend fun getConversation(conversationId: String): Map<String, Any>? {
        return try {
            val userId = FirebaseManager.getCurrentUserId() ?: return null
            val snapshot = FirebaseManager.database
                    .getReference("conversations/$userId/$conversationId")
                    .get()
                    .await()
            @Suppress("UNCHECKED_CAST")
            snapshot.value as? Map<String, Any>
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load conversation $conversationId: ${e.message}", e)
            null
        }
    }

    /** Delete generated image metadata from Realtime Database. */
    suspend fun deleteGeneratedImage(imageId: String): Boolean {
        return try {
            val userId = FirebaseManager.getCurrentUserId()
            if (userId == null) {
                onError?.invoke("User not authenticated")
                return false
            }
            FirebaseManager.database.getReference("images/$userId/$imageId").removeValue().await()
            Log.i(TAG, "Generated image deleted from Realtime Database: $imageId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete generated image: ${e.message}", e)
            onError?.invoke("Failed to delete image: ${e.message}")
            false
        }
    }
}
