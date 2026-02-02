package com.example.chat_ui.data.models

import java.util.UUID
import kotlinx.serialization.Serializable

/** Firebase-compatible Models All models are simple data classes for Firestore */

// ==================== MESSAGE ====================

@Serializable
data class MessageFile(
        val type: String = "base64", // "hash" | "base64"
        val name: String = "",
        val value: String = "",
        val mime: String = ""
)

@Serializable
data class RouterMetadata(
        val route: String = "",
        val model: String = "",
        val provider: String? = null
)

@Serializable
data class Message(
        val id: String = UUID.randomUUID().toString(),
        val from: String = "user", // "user" | "assistant" | "system"
        val content: String = "",
        val reasoning: String? = null,
        val thinking: String? = null,
        val score: Int = 0,
        val files: List<MessageFile> = emptyList(),
        val generatedImages: List<String> = emptyList(), // Cloudinary URLs
        val interrupted: Boolean = false,
        val routerMetadata: RouterMetadata? = null,
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis()
)

// ==================== CONVERSATION ====================

@Serializable
data class Conversation(
        val id: String = UUID.randomUUID().toString(),
        val sessionId: String? = null,
        val userId: String? = null,
        val model: String = "",
        val title: String = "",
        val rootMessageId: String? = null,
        val messages: List<Message> = emptyList(),
        val fromShareId: String? = null,
        val preprompt: String? = null,
        val assistantId: String? = null,
        val userAgent: String? = null,
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis()
)

// ==================== GENERATED IMAGE ====================

@Serializable
data class GeneratedImage(
        val id: String = UUID.randomUUID().toString(),
        val userId: String? = null,
        val prompt: String = "",
        val cloudinaryUrl: String = "",
        val cloudinaryPublicId: String = "",
        val firebaseUrl: String? = null,
        val width: Int? = null,
        val height: Int? = null,
        val modelUsed: String = "",
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis()
)

// ==================== USER ====================

@Serializable
data class User(
        val id: String = "",
        val email: String = "",
        val name: String = "",
        val username: String = "",
        val avatarUrl: String? = null,
        val googleId: String? = null,
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis()
)

// ==================== SETTINGS ====================

@Serializable
data class Settings(
        val id: String = "",
        val userId: String = "",
        val theme: String = "dark",
        val language: String = "ar",
        val defaultModel: String = "",
        val shareConversationsWithModelAuthors: Boolean = false,
        val hideEmojiOnSidebar: Boolean = false,
        val customPrompts: Map<String, String> = emptyMap(),
        val updatedAt: Long = System.currentTimeMillis()
)

// ==================== ASSISTANT ====================

@Serializable
data class Assistant(
        val id: String = UUID.randomUUID().toString(),
        val name: String = "",
        val description: String? = null,
        val modelId: String = "",
        val preprompt: String? = null,
        val exampleInputs: List<String> = emptyList(),
        val avatar: String? = null,
        val createdById: String? = null,
        val createdByName: String? = null,
        val userCount: Int = 0,
        val featured: Boolean = false,
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis()
)

// ==================== SESSION ====================

@Serializable
data class Session(
        val id: String = UUID.randomUUID().toString(),
        val visitorId: String = "",
        val sessionId: String = "",
        val userId: String? = null,
        val userAgent: String? = null,
        val ip: String? = null,
        val expiresAt: Long = System.currentTimeMillis() + 24 * 60 * 60 * 1000, // 24 hours
        val createdAt: Long = System.currentTimeMillis()
)

// ==================== REPORT ====================

@Serializable
data class Report(
        val id: String = UUID.randomUUID().toString(),
        val createdBy: String = "",
        val contentId: String = "",
        val contentType: String = "", // "conversation" | "assistant"
        val object_: String = "",
        val reason: String? = null,
        val createdAt: Long = System.currentTimeMillis()
)

// ==================== SHARED CONVERSATION ====================

@Serializable
data class SharedConversation(
        val id: String = UUID.randomUUID().toString(),
        val conversationId: String = "",
        val hash: String = "",
        val title: String = "",
        val model: String = "",
        val preprompt: String? = null,
        val assistantId: String? = null,
        val messages: List<Message> = emptyList(),
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis()
)

// ==================== ABORTED GENERATION ====================

@Serializable
data class AbortedGeneration(
        val id: String = UUID.randomUUID().toString(),
        val visitorId: String = "",
        val visitorIdHash: String? = null,
        val visitorIdHashSalt: String? = null,
        val conversationId: String = "",
        val messageId: String = "",
        val reason: String = "userAbort",
        val createdAt: Long = System.currentTimeMillis()
)

// ==================== CONFIG ====================

@Serializable
data class AppConfig(
        val id: String = "app_config",
        val activeModels: List<String> = emptyList(),
        val featuredModels: List<String> = emptyList(),
        val defaultModel: String = "",
        val maintenanceMode: Boolean = false,
        val updatedAt: Long = System.currentTimeMillis()
)

// ==================== CONVERSATION STATS ====================

@Serializable
data class ConversationStats(
        val id: String = UUID.randomUUID().toString(),
        val visitorId: String = "",
        val visitorIdHash: String? = null,
        val conversationId: String = "",
        val date: String = "",
        val updatedAt: Long = System.currentTimeMillis()
)

// ==================== MESSAGE EVENT ====================

@Serializable
data class MessageEvent(
        val id: String = UUID.randomUUID().toString(),
        val visitorId: String = "",
        val visitorIdHash: String? = null,
        val conversationId: String = "",
        val messageId: String = "",
        val createdAt: Long = System.currentTimeMillis()
)
