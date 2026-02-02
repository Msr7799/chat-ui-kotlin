package com.example.chat_ui.data.firebase

import android.util.Log
import com.example.chat_ui.data.models.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore Manager - Replaces DatabaseManager
 *
 * Collections:
 * - users
 * - conversations
 * - generatedImages
 * - settings
 * - assistants
 * - sessions
 * - reports
 * - sharedConversations
 * - config
 */
object FirestoreManager {
    private const val TAG = "FirestoreManager"

    private val firestore: FirebaseFirestore by lazy { Firebase.firestore }

    // Collection names
    object Collections {
        const val USERS = "users"
        const val CONVERSATIONS = "conversations"
        const val GENERATED_IMAGES = "generated_images"
        const val SETTINGS = "settings"
        const val ASSISTANTS = "assistants"
        const val SESSIONS = "sessions"
        const val REPORTS = "reports"
        const val SHARED_CONVERSATIONS = "sharedConversations"
        const val CONFIG = "config"
    }

    // ==================== CONVERSATIONS ====================

    suspend fun saveConversation(conversation: Conversation): Boolean {
        return try {
            val userId = FirebaseManager.getCurrentUserId() ?: return false
            val data =
                    mapOf(
                            "id" to conversation.id,
                            "userId" to userId,
                            "model" to conversation.model,
                            "title" to conversation.title,
                            "messages" to
                                    conversation.messages.map { msg ->
                                        mapOf(
                                                "id" to msg.id,
                                                "from" to msg.from,
                                                "content" to msg.content,
                                                "reasoning" to msg.reasoning,
                                                "score" to msg.score,
                                                "interrupted" to msg.interrupted,
                                                "createdAt" to msg.createdAt,
                                                "updatedAt" to msg.updatedAt
                                        )
                                    },
                            "preprompt" to conversation.preprompt,
                            "assistantId" to conversation.assistantId,
                            "createdAt" to conversation.createdAt,
                            "updatedAt" to System.currentTimeMillis()
                    )

            firestore
                    .collection(Collections.CONVERSATIONS)
                    .document(conversation.id)
                    .set(data, SetOptions.merge())
                    .await()

            Log.i(TAG, "Conversation saved: ${conversation.title}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save conversation: ${e.message}", e)
            false
        }
    }

    fun getConversationsFlow(): Flow<List<Conversation>> = callbackFlow {
        val userId = FirebaseManager.getCurrentUserId()
        if (userId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener =
                firestore
                        .collection(Collections.CONVERSATIONS)
                        .whereEqualTo("userId", userId)
                        .orderBy("updatedAt", Query.Direction.DESCENDING)
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                Log.e(TAG, "Error loading conversations: ${error.message}")
                                trySend(emptyList())
                                return@addSnapshotListener
                            }

                            @Suppress("UNCHECKED_CAST")
                            val conversations =
                                    snapshot?.documents?.mapNotNull { doc ->
                                        try {
                                            val data = doc.data ?: return@mapNotNull null
                                            val messages =
                                                    (data["messages"] as? List<Map<String, Any>>)
                                                            ?.map { msgData ->
                                                                Message(
                                                                        id =
                                                                                msgData["id"] as?
                                                                                        String
                                                                                        ?: "",
                                                                        from =
                                                                                msgData["from"] as?
                                                                                        String
                                                                                        ?: "user",
                                                                        content =
                                                                                msgData[
                                                                                        "content"] as?
                                                                                        String
                                                                                        ?: "",
                                                                        reasoning =
                                                                                msgData[
                                                                                        "reasoning"] as?
                                                                                        String,
                                                                        score =
                                                                                (msgData[
                                                                                                "score"] as?
                                                                                                Long)
                                                                                        ?.toInt()
                                                                                        ?: 0,
                                                                        interrupted =
                                                                                msgData[
                                                                                        "interrupted"] as?
                                                                                        Boolean
                                                                                        ?: false,
                                                                        createdAt =
                                                                                msgData[
                                                                                        "createdAt"] as?
                                                                                        Long
                                                                                        ?: 0,
                                                                        updatedAt =
                                                                                msgData[
                                                                                        "updatedAt"] as?
                                                                                        Long
                                                                                        ?: 0
                                                                )
                                                            }
                                                            ?: emptyList()

                                            Conversation(
                                                    id = data["id"] as? String ?: doc.id,
                                                    userId = data["userId"] as? String,
                                                    model = data["model"] as? String ?: "",
                                                    title = data["title"] as? String ?: "",
                                                    messages = messages,
                                                    preprompt = data["preprompt"] as? String,
                                                    assistantId = data["assistantId"] as? String,
                                                    createdAt = data["createdAt"] as? Long ?: 0,
                                                    updatedAt = data["updatedAt"] as? Long ?: 0
                                            )
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error parsing conversation: ${e.message}")
                                            null
                                        }
                                    }
                                            ?: emptyList()

                            trySend(conversations)
                        }

        awaitClose { listener.remove() }
    }

    suspend fun getConversation(conversationId: String): Conversation? {
        return try {
            val doc =
                    firestore
                            .collection(Collections.CONVERSATIONS)
                            .document(conversationId)
                            .get()
                            .await()

            if (!doc.exists()) return null

            val data = doc.data ?: return null
            @Suppress("UNCHECKED_CAST")
            val messages =
                    (data["messages"] as? List<Map<String, Any>>)?.map { msgData ->
                        Message(
                                id = msgData["id"] as? String ?: "",
                                from = msgData["from"] as? String ?: "user",
                                content = msgData["content"] as? String ?: "",
                                reasoning = msgData["reasoning"] as? String,
                                score = (msgData["score"] as? Long)?.toInt() ?: 0,
                                interrupted = msgData["interrupted"] as? Boolean ?: false,
                                createdAt = msgData["createdAt"] as? Long ?: 0,
                                updatedAt = msgData["updatedAt"] as? Long ?: 0
                        )
                    }
                            ?: emptyList()

            Conversation(
                    id = data["id"] as? String ?: doc.id,
                    userId = data["userId"] as? String,
                    model = data["model"] as? String ?: "",
                    title = data["title"] as? String ?: "",
                    messages = messages,
                    preprompt = data["preprompt"] as? String,
                    assistantId = data["assistantId"] as? String,
                    createdAt = data["createdAt"] as? Long ?: 0,
                    updatedAt = data["updatedAt"] as? Long ?: 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get conversation: ${e.message}", e)
            null
        }
    }

    suspend fun deleteConversation(conversationId: String): Boolean {
        return try {
            firestore
                    .collection(Collections.CONVERSATIONS)
                    .document(conversationId)
                    .delete()
                    .await()

            Log.i(TAG, "Conversation deleted: $conversationId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete conversation: ${e.message}", e)
            false
        }
    }

    // ==================== GENERATED IMAGES ====================

    suspend fun saveGeneratedImage(image: GeneratedImage): Boolean {
        return try {
            val userId = FirebaseManager.getCurrentUserId() ?: return false
            val data =
                    mapOf(
                            "id" to image.id,
                            "userId" to userId,
                            "prompt" to image.prompt,
                            "cloudinaryUrl" to image.cloudinaryUrl,
                            "cloudinaryPublicId" to image.cloudinaryPublicId,
                            "firebaseUrl" to image.firebaseUrl,
                            "width" to image.width,
                            "height" to image.height,
                            "modelUsed" to image.modelUsed,
                            "createdAt" to image.createdAt,
                            "updatedAt" to System.currentTimeMillis()
                    )

            firestore.collection(Collections.GENERATED_IMAGES).document(image.id).set(data).await()

            Log.i(TAG, "Image saved: ${image.id}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save image: ${e.message}", e)
            false
        }
    }

    fun getGeneratedImagesFlow(): Flow<List<GeneratedImage>> = callbackFlow {
        val userId = FirebaseManager.getCurrentUserId()
        if (userId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener =
                firestore
                        .collection(Collections.GENERATED_IMAGES)
                        .whereEqualTo("userId", userId)
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                Log.e(TAG, "Error loading images: ${error.message}")
                                trySend(emptyList())
                                return@addSnapshotListener
                            }

                            val images =
                                    snapshot?.documents?.mapNotNull { doc ->
                                        try {
                                            val data = doc.data ?: return@mapNotNull null
                                            GeneratedImage(
                                                    id = data["id"] as? String ?: doc.id,
                                                    userId = data["userId"] as? String,
                                                    prompt = data["prompt"] as? String ?: "",
                                                    cloudinaryUrl = data["cloudinaryUrl"] as? String
                                                                    ?: "",
                                                    cloudinaryPublicId =
                                                            data["cloudinaryPublicId"] as? String
                                                                    ?: "",
                                                    firebaseUrl = data["firebaseUrl"] as? String,
                                                    width = (data["width"] as? Long)?.toInt(),
                                                    height = (data["height"] as? Long)?.toInt(),
                                                    modelUsed = data["modelUsed"] as? String ?: "",
                                                    createdAt = data["createdAt"] as? Long ?: 0,
                                                    updatedAt = data["updatedAt"] as? Long ?: 0
                                            )
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error parsing image: ${e.message}")
                                            null
                                        }
                                    }
                                            ?: emptyList()

                            trySend(images)
                        }

        awaitClose { listener.remove() }
    }

    suspend fun deleteGeneratedImage(imageId: String): Boolean {
        return try {
            firestore.collection(Collections.GENERATED_IMAGES).document(imageId).delete().await()

            Log.i(TAG, "Image deleted: $imageId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete image: ${e.message}", e)
            false
        }
    }

    // ==================== USER ====================

    suspend fun saveUser(user: User): Boolean {
        return try {
            val data =
                    mapOf(
                            "id" to user.id,
                            "email" to user.email,
                            "name" to user.name,
                            "username" to user.username,
                            "avatarUrl" to user.avatarUrl,
                            "googleId" to user.googleId,
                            "createdAt" to user.createdAt,
                            "updatedAt" to System.currentTimeMillis()
                    )

            firestore
                    .collection(Collections.USERS)
                    .document(user.id)
                    .set(data, SetOptions.merge())
                    .await()

            Log.i(TAG, "User saved: ${user.email}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save user: ${e.message}", e)
            false
        }
    }

    suspend fun getUser(userId: String): User? {
        return try {
            val doc = firestore.collection(Collections.USERS).document(userId).get().await()

            if (!doc.exists()) return null

            val data = doc.data ?: return null
            User(
                    id = data["id"] as? String ?: doc.id,
                    email = data["email"] as? String ?: "",
                    name = data["name"] as? String ?: "",
                    username = data["username"] as? String ?: "",
                    avatarUrl = data["avatarUrl"] as? String,
                    googleId = data["googleId"] as? String,
                    createdAt = data["createdAt"] as? Long ?: 0,
                    updatedAt = data["updatedAt"] as? Long ?: 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get user: ${e.message}", e)
            null
        }
    }

    // ==================== SETTINGS ====================

    suspend fun saveSettings(settings: Settings): Boolean {
        return try {
            val userId = FirebaseManager.getCurrentUserId() ?: return false
            val data =
                    mapOf(
                            "id" to userId,
                            "userId" to userId,
                            "theme" to settings.theme,
                            "language" to settings.language,
                            "defaultModel" to settings.defaultModel,
                            "shareConversationsWithModelAuthors" to
                                    settings.shareConversationsWithModelAuthors,
                            "hideEmojiOnSidebar" to settings.hideEmojiOnSidebar,
                            "updatedAt" to System.currentTimeMillis()
                    )

            firestore
                    .collection(Collections.SETTINGS)
                    .document(userId)
                    .set(data, SetOptions.merge())
                    .await()

            Log.i(TAG, "Settings saved")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save settings: ${e.message}", e)
            false
        }
    }

    suspend fun getSettings(): Settings? {
        return try {
            val userId = FirebaseManager.getCurrentUserId() ?: return null
            val doc = firestore.collection(Collections.SETTINGS).document(userId).get().await()

            if (!doc.exists()) return null

            val data = doc.data ?: return null
            Settings(
                    id = data["id"] as? String ?: doc.id,
                    userId = data["userId"] as? String ?: "",
                    theme = data["theme"] as? String ?: "dark",
                    language = data["language"] as? String ?: "ar",
                    defaultModel = data["defaultModel"] as? String ?: "",
                    shareConversationsWithModelAuthors =
                            data["shareConversationsWithModelAuthors"] as? Boolean ?: false,
                    hideEmojiOnSidebar = data["hideEmojiOnSidebar"] as? Boolean ?: false,
                    updatedAt = data["updatedAt"] as? Long ?: 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get settings: ${e.message}", e)
            null
        }
    }

    // ==================== ASSISTANTS ====================

    /** Clear all conversations for current user */
    suspend fun clearAllConversations(): Int {
        return try {
            val userId = FirebaseManager.getCurrentUserId() ?: return 0
            val snapshot =
                    firestore
                            .collection(Collections.CONVERSATIONS)
                            .whereEqualTo("userId", userId)
                            .get()
                            .await()

            var count = 0
            for (doc in snapshot.documents) {
                doc.reference.delete().await()
                count++
            }

            Log.i(TAG, "Cleared $count conversations")
            count
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear conversations: ${e.message}", e)
            0
        }
    }

    fun getAssistantsFlow(): Flow<List<Assistant>> = callbackFlow {
        val listener =
                firestore
                        .collection(Collections.ASSISTANTS)
                        .orderBy("userCount", Query.Direction.DESCENDING)
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                Log.e(TAG, "Error loading assistants: ${error.message}")
                                trySend(emptyList())
                                return@addSnapshotListener
                            }

                            val assistants =
                                    snapshot?.documents?.mapNotNull { doc ->
                                        try {
                                            val data = doc.data ?: return@mapNotNull null
                                            Assistant(
                                                    id = data["id"] as? String ?: doc.id,
                                                    name = data["name"] as? String ?: "",
                                                    description = data["description"] as? String,
                                                    modelId = data["modelId"] as? String ?: "",
                                                    preprompt = data["preprompt"] as? String,
                                                    avatar = data["avatar"] as? String,
                                                    createdById = data["createdById"] as? String,
                                                    createdByName =
                                                            data["createdByName"] as? String,
                                                    userCount =
                                                            (data["userCount"] as? Long)?.toInt()
                                                                    ?: 0,
                                                    featured = data["featured"] as? Boolean
                                                                    ?: false,
                                                    createdAt = data["createdAt"] as? Long ?: 0,
                                                    updatedAt = data["updatedAt"] as? Long ?: 0
                                            )
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error parsing assistant: ${e.message}")
                                            null
                                        }
                                    }
                                            ?: emptyList()

                            trySend(assistants)
                        }

        awaitClose { listener.remove() }
    }
}
