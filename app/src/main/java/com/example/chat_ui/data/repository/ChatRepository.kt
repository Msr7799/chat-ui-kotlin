package com.example.chat_ui.data.repository

import android.content.Context
import android.net.Uri
import com.example.chat_ui.data.Conversation
import com.example.chat_ui.data.cloud.CloudinaryManager
import com.example.chat_ui.data.cloud.CloudinaryUploadResult
import com.example.chat_ui.data.firebase.FirestoreManager
import com.example.chat_ui.data.models.GeneratedImage
import com.example.chat_ui.data.toConversation
import com.example.chat_ui.data.toFirebaseConversation
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Repository for Chat data operations Bridges UI layer with Database and Cloud services */
class ChatRepository(private val context: Context) {

    // ===================================
    // Conversations
    // ===================================

    /** Get all conversations as Flow */
    fun getConversationsFlow(): Flow<List<Conversation>> {
        return FirestoreManager.getConversationsFlow().map { models ->
            models.map { it.toConversation() }
        }
    }

    /** Save a conversation */
    suspend fun saveConversation(conversation: Conversation) {
        val firebaseConversation = conversation.toFirebaseConversation()
        FirestoreManager.saveConversation(firebaseConversation)
    }

    /** Delete a conversation */
    suspend fun deleteConversation(conversationId: String) {
        FirestoreManager.deleteConversation(conversationId)
    }

    // ===================================
    // Image Upload
    // ===================================

    /** Upload image to Cloudinary */
    suspend fun uploadImage(
            imageUri: Uri,
            folder: String? = null,
            tags: List<String>? = null
    ): CloudinaryUploadResult {
        return CloudinaryManager.uploadImage(
                context = context,
                imageUri = imageUri,
                folder = folder,
                tags = tags
        )
    }

    /** Upload image and save to Firebase */
    suspend fun uploadAndSaveImage(
            imageUri: Uri,
            prompt: String,
            modelUsed: String
    ): GeneratedImage {
        val uploadResult = uploadImage(imageUri)

        val image =
                GeneratedImage(
                        id = UUID.randomUUID().toString(),
                        prompt = prompt,
                        cloudinaryUrl = uploadResult.url,
                        cloudinaryPublicId = uploadResult.publicId,
                        width = uploadResult.width,
                        height = uploadResult.height,
                        modelUsed = modelUsed
                )

        FirestoreManager.saveGeneratedImage(image)
        return image
    }

    /** Get generated images as Flow */
    fun getGeneratedImagesFlow(): Flow<List<GeneratedImage>> {
        return FirestoreManager.getGeneratedImagesFlow()
    }
}
