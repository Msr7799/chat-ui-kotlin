package com.example.chat_ui.utils

import android.util.Log
import com.example.chat_ui.data.MessageFile
import kotlinx.serialization.json.*

/**
 * MessagePreparer - Prepare messages with files for OpenAI-compatible APIs
 * 
 * Similar to: src/lib/server/textGeneration/utils/prepareFiles.ts in chat-ui
 * 
 * Features:
 * - Process images for multimodal content
 * - Inject text files as document content
 * - Format messages for OpenAI chat completions API
 */
object MessagePreparer {
    private const val TAG = "MessagePreparer"
    
    /**
     * Message with optional files for API
     */
    data class ChatMessage(
        val role: String, // "user", "assistant", "system"
        val content: String,
        val files: List<MessageFile> = emptyList()
    )
    
    /**
     * Prepare messages with files for OpenAI API
     * 
     * @param messages List of chat messages with optional files
     * @param isMultimodal Whether the model supports multimodal (vision) input
     * @param imageProcessor Optional processor options for images
     * @return JSON array ready for API request
     */
    suspend fun prepareMessagesWithFiles(
        messages: List<ChatMessage>,
        isMultimodal: Boolean,
        imageProcessor: ImageProcessor.ProcessorOptions = ImageProcessor.DEFAULT_OPTIONS
    ): JsonArray {
        return buildJsonArray {
            for (message in messages) {
                val preparedMessage = prepareMessage(message, isMultimodal, imageProcessor)
                add(preparedMessage)
            }
        }
    }
    
    /**
     * Prepare a single message with its files
     */
    private suspend fun prepareMessage(
        message: ChatMessage,
        isMultimodal: Boolean,
        imageProcessor: ImageProcessor.ProcessorOptions
    ): JsonObject {
        // If no files, return simple message
        if (message.files.isEmpty()) {
            return buildJsonObject {
                put("role", message.role)
                put("content", message.content)
            }
        }
        
        // Only process files for user messages
        if (message.role != "user") {
            return buildJsonObject {
                put("role", message.role)
                put("content", message.content)
            }
        }
        
        // Separate image, text, and PDF files
        val imageFiles = message.files.filter { it.isImage() }
        val pdfFiles = message.files.filter { it.isPdf() }
        val textFiles = message.files.filter { it.isTextFile() && !it.isPdf() }
        
        // Process images if multimodal
        val imageParts = if (isMultimodal && imageFiles.isNotEmpty()) {
            imageFiles.mapNotNull { file ->
                try {
                    val processedFile = ImageProcessor.processImage(file, imageProcessor)
                    buildJsonObject {
                        put("type", "image_url")
                        putJsonObject("image_url") {
                            put("url", "data:${processedFile.mime};base64,${processedFile.value}")
                            put("detail", "auto")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process image: ${e.message}", e)
                    null
                }
            }
        } else {
            emptyList()
        }
        
        // Process text files - inject as document content
        val textContent = if (textFiles.isNotEmpty()) {
            textFiles.mapNotNull { file ->
                file.getTextContent()?.let { content ->
                    """<document name="${file.name}" type="${file.mime}">
$content
</document>"""
                }
            }.joinToString("\n\n")
        } else {
            ""
        }
        
        // Process PDF files - add as base64 for models that support it
        val pdfParts = if (isMultimodal && pdfFiles.isNotEmpty()) {
            pdfFiles.map { file ->
                buildJsonObject {
                    put("type", "file")
                    putJsonObject("file") {
                        put("filename", file.name)
                        put("file_data", "data:${file.mime};base64,${file.value}")
                    }
                }
            }
        } else {
            // For non-multimodal, add PDF info as text notice
            emptyList()
        }
        
        // Add PDF notice for non-multimodal models
        val pdfNotice = if (!isMultimodal && pdfFiles.isNotEmpty()) {
            pdfFiles.joinToString("\n") { file ->
                "[PDF file attached: ${file.name} - This model may not be able to read PDF content directly. Please use a vision-enabled model like Qwen-VL, GLM-4.5V, or aya-vision.]"
            }
        } else {
            ""
        }
        
        // Combine text content with message
        val messageText = buildString {
            if (textContent.isNotEmpty()) {
                append(textContent)
                append("\n\n")
            }
            if (pdfNotice.isNotEmpty()) {
                append(pdfNotice)
                append("\n\n")
            }
            append(message.content)
        }
        
        // Build final message
        val hasMultimodalContent = (imageParts.isNotEmpty() || pdfParts.isNotEmpty()) && isMultimodal
        
        return if (hasMultimodalContent) {
            // Multimodal format with content array
            buildJsonObject {
                put("role", message.role)
                putJsonArray("content") {
                    // Add text part first
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", messageText)
                    })
                    // Add image parts
                    imageParts.forEach { add(it) }
                    // Add PDF parts (for models that support file attachments)
                    pdfParts.forEach { add(it) }
                }
            }
        } else {
            // Simple text format
            buildJsonObject {
                put("role", message.role)
                put("content", messageText)
            }
        }
    }
    
    /**
     * Convert legacy format messages to ChatMessage format
     */
    fun fromLegacyMessages(
        messages: List<com.example.chat_ui.api.ChatApiClient.ChatMessage>,
        filesMap: Map<String, List<MessageFile>> = emptyMap()
    ): List<ChatMessage> {
        return messages.mapIndexed { index, msg ->
            ChatMessage(
                role = msg.role,
                content = msg.content,
                files = filesMap[index.toString()] ?: emptyList()
            )
        }
    }
    
    /**
     * Check if any message has files
     */
    fun hasFiles(messages: List<ChatMessage>): Boolean {
        return messages.any { it.files.isNotEmpty() }
    }
    
    /**
     * Check if any message has images
     */
    fun hasImages(messages: List<ChatMessage>): Boolean {
        return messages.any { msg -> msg.files.any { it.isImage() } }
    }
    
    /**
     * Check if any message has PDF files
     */
    fun hasPdfs(messages: List<ChatMessage>): Boolean {
        return messages.any { msg -> msg.files.any { it.isPdf() } }
    }
    
    /**
     * Check if any message has multimodal content (images or PDFs)
     */
    fun hasMultimodalContent(messages: List<ChatMessage>): Boolean {
        return hasImages(messages) || hasPdfs(messages)
    }
    
    /**
     * Get total file count
     */
    fun getTotalFileCount(messages: List<ChatMessage>): Int {
        return messages.sumOf { it.files.size }
    }
    
    /**
     * Prepare messages for Google AI Studio/Gemini API format
     * Gemini expects: {"role": "user", "parts": [{"text": "..."}]}
     * NOT: {"role": "user", "content": "..."}
     */
    suspend fun prepareMessagesForGemini(
        messages: List<ChatMessage>,
        isMultimodal: Boolean,
        imageProcessor: ImageProcessor.ProcessorOptions = ImageProcessor.DEFAULT_OPTIONS
    ): JsonArray {
        return buildJsonArray {
            for (message in messages) {
                // Convert role: "assistant" -> "model" for Gemini
                val geminiRole = if (message.role == "assistant") "model" else message.role
                
                // Process files
                val imageFiles = message.files.filter { it.isImage() }
                val textFiles = message.files.filter { it.isTextFile() && !it.isPdf() }
                
                // Inject text files as document content
                val textContent = if (textFiles.isNotEmpty()) {
                    textFiles.mapNotNull { file ->
                        file.getTextContent()?.let { content ->
                            """<document name="${file.name}" type="${file.mime}">
$content
</document>"""
                        }
                    }.joinToString("\n\n")
                } else ""
                
                val messageText = buildString {
                    if (textContent.isNotEmpty()) {
                        append(textContent)
                        append("\n\n")
                    }
                    append(message.content)
                }
                
                // Build Gemini message
                add(buildJsonObject {
                    put("role", geminiRole)
                    putJsonArray("parts") {
                        // Text part
                        add(buildJsonObject {
                            put("text", messageText)
                        })
                        
                        // Image parts (inline_data format for Gemini)
                        if (isMultimodal && imageFiles.isNotEmpty()) {
                            imageFiles.forEach { file ->
                                try {
                                    val processedFile = kotlinx.coroutines.runBlocking {
                                        ImageProcessor.processImage(file, imageProcessor)
                                    }
                                    add(buildJsonObject {
                                        putJsonObject("inline_data") {
                                            put("mime_type", processedFile.mime)
                                            put("data", processedFile.value)
                                        }
                                    })
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to process image for Gemini: ${e.message}", e)
                                }
                            }
                        }
                    }
                })
            }
        }
    }
}
