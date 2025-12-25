package com.example.chat_ui.data

import com.example.chat_ui.data.models.Conversation as FirebaseConversation
import com.example.chat_ui.data.models.Message as FirebaseMessage
import java.util.UUID

/** UI Models - Simple data classes for the app Firebase Models are in data.models.Models.kt */
data class Message(
        val id: String = UUID.randomUUID().toString(),
        val content: String,
        val isUser: Boolean,
        val timestamp: Long = System.currentTimeMillis(),
        val attachments: List<Attachment> = emptyList(),
        val files: List<MessageFile> = emptyList(), // For multimodal API (images, documents)
        val generatedImages: List<String> = emptyList(), // Cloudinary URLs of generated images
        val model: String = "", // Model ID used for this message (e.g., "google/gemini-2.0-flash-001")
        // Alternatives support - for regenerating responses
        val alternatives: List<String> = emptyList(), // List of alternative response contents
        val currentAlternativeIndex: Int = 0 // Which alternative is currently shown
) {
    /** Get current display content (considering alternatives) */
    fun getDisplayContent(): String {
        return if (alternatives.isNotEmpty() && currentAlternativeIndex < alternatives.size) {
            alternatives[currentAlternativeIndex]
        } else {
            content
        }
    }
    
    /** Check if this message has alternatives */
    fun hasAlternatives(): Boolean = alternatives.size > 1
    
    /** Get total alternatives count */
    fun getAlternativesCount(): Int = maxOf(1, alternatives.size)
}

data class Attachment(
        val id: String = UUID.randomUUID().toString(),
        val name: String,
        val type: AttachmentType,
        val url: String? = null,
        val mime: String = ""
)

enum class AttachmentType {
    IMAGE,
    FILE,
    AUDIO
}

data class Conversation(
        val id: String = UUID.randomUUID().toString(),
        val title: String,
        val messages: List<Message>,
        val timestamp: Long = System.currentTimeMillis(),
        val model: String = "omni"
)

// ===================================
// Extension functions for Firebase conversion
// ===================================

/** Convert UI Conversation to Firebase Conversation */
fun Conversation.toFirebaseConversation(): FirebaseConversation {
    return FirebaseConversation(
            id = id,
            title = title,
            model = model,
            messages = messages.map { it.toFirebaseMessage() },
            createdAt = timestamp,
            updatedAt = System.currentTimeMillis()
    )
}

/** Convert UI Message to Firebase Message */
fun Message.toFirebaseMessage(): FirebaseMessage {
    return FirebaseMessage(
            id = id,
            from = if (isUser) "user" else "assistant",
            content = content,
            createdAt = timestamp,
            updatedAt = System.currentTimeMillis()
    )
}

/** Convert Firebase Conversation to UI Conversation */
fun FirebaseConversation.toConversation(): Conversation {
    return Conversation(
            id = id,
            title = title,
            messages = messages.map { it.toMessage() },
            timestamp = createdAt,
            model = model
    )
}

/** Convert Firebase Message to UI Message */
fun FirebaseMessage.toMessage(): Message {
    return Message(id = id, content = content, isUser = from == "user", timestamp = createdAt)
}

data class User(
        val id: String,
        val username: String,
        val email: String,
        val avatarUrl: String? = null
)

data class Model(
        val id: String,
        val name: String,
        val description: String,
        val iconUrl: String? = null
)

// Sample data for testing
val sampleConversations =
        listOf(
                Conversation(
                        id = "1",
                        title = "مساعدة في كتابة كود Python",
                        messages =
                                listOf(
                                        Message(
                                                "1",
                                                "كيف أكتب كود Python لقراءة ملف؟",
                                                true,
                                                System.currentTimeMillis() - 3600000
                                        ),
                                        Message(
                                                "2",
                                                "يمكنك استخدام الكود التالي:\n\n```python\nwith open('file.txt', 'r') as f:\n    content = f.read()\n    print(content)\n```",
                                                false,
                                                System.currentTimeMillis() - 3500000
                                        )
                                ),
                        timestamp = System.currentTimeMillis() - 3600000
                ),
                Conversation(
                        id = "2",
                        title = "شرح مفهوم الـ API",
                        messages =
                                listOf(
                                        Message(
                                                "3",
                                                "ما هو الـ API؟",
                                                true,
                                                System.currentTimeMillis() - 7200000
                                        ),
                                        Message(
                                                "4",
                                                "API هو اختصار لـ Application Programming Interface، وهو مجموعة من البروتوكولات والأدوات التي تسمح للتطبيقات المختلفة بالتواصل مع بعضها البعض.",
                                                false,
                                                System.currentTimeMillis() - 7100000
                                        )
                                ),
                        timestamp = System.currentTimeMillis() - 7200000
                ),
                Conversation(
                        id = "3",
                        title = "أفكار لمشروع تطبيق موبايل",
                        messages =
                                listOf(
                                        Message(
                                                "5",
                                                "أريد أفكار لمشروع تطبيق موبايل",
                                                true,
                                                System.currentTimeMillis() - 86400000
                                        )
                                ),
                        timestamp = System.currentTimeMillis() - 86400000
                )
        )
