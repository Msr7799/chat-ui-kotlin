package com.example.chat_ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat_ui.api.ChatApiClient
import com.example.chat_ui.api.ChatStreamingClient
import com.example.chat_ui.api.ImageGenerationApiClient
import com.example.chat_ui.api.LlmRouter
import com.example.chat_ui.api.ModelsApiClient
import com.example.chat_ui.api.StreamEvent
import com.example.chat_ui.config.ConfigManager
import com.example.chat_ui.data.Attachment
import com.example.chat_ui.data.Conversation
import com.example.chat_ui.data.Message
import com.example.chat_ui.data.MessageFile
import com.example.chat_ui.data.firebase.FirebaseDatabaseManager
import com.example.chat_ui.mcp.MCPManager
import com.example.chat_ui.mcp.MCPToolExecutor
import com.example.chat_ui.utils.FileAttachmentManager
import com.example.chat_ui.utils.ImageProcessor
import com.example.chat_ui.utils.MessagePreparer
import java.util.UUID
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ViewModel for Chat functionality Manages chat state and API calls Now persists conversations to
 * MongoDB Realm
 */
class ChatViewModel : ViewModel() {

    private val apiClient = ChatApiClient.getInstance()
    private val imageGenClient = ImageGenerationApiClient()
    private val TAG = "ChatViewModel"

    // UI State - synced with database
    var conversations by mutableStateOf<List<Conversation>>(emptyList())
        private set

    var currentConversation by mutableStateOf<Conversation?>(null)
        private set

    var messages by mutableStateOf<List<Message>>(emptyList())
        private set

    // Default to "omni" for smart routing
    var selectedModelId by
            mutableStateOf(ConfigManager.get(ConfigManager.Keys.PUBLIC_LLM_ROUTER_ALIAS_ID, "omni"))
        private set

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    // Available models fetched from API
    var availableModels by mutableStateOf<List<ModelsApiClient.FetchedModel>>(emptyList())
        private set

    var isLoadingModels by mutableStateOf(false)
        private set

    // Attachments for current message (UI display)
    var attachments by mutableStateOf<List<Attachment>>(emptyList())
        private set
    
    // Files for multimodal API (images, documents)
    var pendingFiles by mutableStateOf<List<MessageFile>>(emptyList())
        private set

    var isUploadingAttachment by mutableStateOf(false)
        private set
    
    // Model multimodal capability
    var isModelMultimodal by mutableStateOf(true) // Most modern models support vision
        private set

    // Enable/disable streaming
    var useStreaming by mutableStateOf(true)
        private set
    
    // MCP Tools state
    var mcpToolsEnabled by mutableStateOf(true)
        private set
    
    var isExecutingTool by mutableStateOf(false)
        private set
    
    // Image Generation state
    var isGeneratingImage by mutableStateOf(false)
        private set
    
    var imageGenerationError by mutableStateOf<String?>(null)
        private set
    
    // Job for tracking streaming so it can be cancelled
    private var streamingJob: kotlinx.coroutines.Job? = null
    
    // Job for tracking Firebase listeners
    private var firebaseListenerJob: kotlinx.coroutines.Job? = null

    init {
        // Load selected model from config
        selectedModelId = ConfigManager.get(ConfigManager.Keys.PUBLIC_LLM_ROUTER_ALIAS_ID, "omni")
        // Fetch available models on init
        fetchModels()
        // Load conversations from database
        loadConversationsFromDatabase()
    }
    
    override fun onCleared() {
        super.onCleared()
        // Cancel all ongoing operations
        streamingJob?.cancel()
        firebaseListenerJob?.cancel()
        Log.d(TAG, "ViewModel cleared - all jobs cancelled")
    }

    /** Load conversations from Firebase Realtime Database */
    @Suppress("UNCHECKED_CAST")
    private fun loadConversationsFromDatabase() {
        // Cancel previous listener if exists
        firebaseListenerJob?.cancel()
        
        firebaseListenerJob = viewModelScope.launch {
            try {
                FirebaseDatabaseManager.getConversations().collectLatest { dbConversations ->
                    conversations =
                            dbConversations.map { convData ->
                                val messages =
                                        (convData["messages"] as? List<Map<String, Any>>)?.map {
                                                msgData ->
                                            Message(
                                                    id = msgData["id"] as? String ?: "",
                                                    content = msgData["content"] as? String ?: "",
                                                    isUser = msgData["isUser"] as? Boolean ?: false,
                                                    timestamp =
                                                            (msgData["timestamp"] as? Number)
                                                                    ?.toLong()
                                                                    ?: 0L
                                            )
                                        }
                                                ?: emptyList()

                                Conversation(
                                        id = convData["id"] as? String ?: "",
                                        title = convData["title"] as? String ?: "",
                                        messages = messages,
                                        timestamp = (convData["updatedAt"] as? Number)?.toLong()
                                                        ?: 0L,
                                        model = convData["model"] as? String ?: "omni"
                                )
                            }
                    Log.i(TAG, "Loaded ${conversations.size} conversations from Realtime Database")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.d(TAG, "Firebase listener cancelled")
                throw e // Re-throw to properly cancel the coroutine
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load conversations: ${e.message}", e)
            }
        }
    }

    /** Fetch available models from HuggingFace Router */
    fun fetchModels() {
        viewModelScope.launch {
            isLoadingModels = true
            try {
                val models = ModelsApiClient.getAllModels()
                availableModels = models
                
                // Reload selected model from config after fetching
                selectedModelId = ConfigManager.get(ConfigManager.Keys.PUBLIC_LLM_ROUTER_ALIAS_ID, "omni")
            } catch (e: Exception) {
                error = "Failed to fetch models: ${e.message}"
            } finally {
                isLoadingModels = false
            }
        }
    }

    /** Check if using Omni router */
    private fun isOmniRouter(): Boolean {
        val aliasId = ConfigManager.get(ConfigManager.Keys.PUBLIC_LLM_ROUTER_ALIAS_ID, "omni")
        return selectedModelId == aliasId || selectedModelId.isBlank()
    }

    /** Select a conversation */
    fun selectConversation(conversation: Conversation) {
        currentConversation = conversation
        messages = conversation.messages
    }

    /** Start a new chat */
    fun newChat() {
        currentConversation = null
        messages = emptyList()
        error = null
    }

    /** Delete a conversation (from memory and Realtime Database) */
    fun deleteConversation(conversation: Conversation) {
        viewModelScope.launch {
            try {
                // Delete from Realtime Database
                FirebaseDatabaseManager.deleteConversation(conversation.id)
                Log.i(TAG, "Deleted conversation ${conversation.id} from Realtime Database")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete conversation: ${e.message}", e)
            }
        }
        // Update local state
        conversations = conversations.filter { it.id != conversation.id }
        if (currentConversation?.id == conversation.id) {
            newChat()
        }
    }

    /** Select a model */
    fun selectModel(modelId: String) {
        Log.d(TAG, "Selecting model: $modelId")
        selectedModelId = modelId
        // Save to config immediately
        ConfigManager.set(ConfigManager.Keys.PUBLIC_LLM_ROUTER_ALIAS_ID, modelId)
        // Auto-detect if model supports vision based on model name
        isModelMultimodal = isVisionModel(modelId)
        Log.d(TAG, "Model selected and saved: $modelId (multimodal: $isModelMultimodal)")
    }
    
    /**
     * Check if model supports vision/multimodal based on model name patterns
     * Based on HuggingFace models that support vision
     */
    private fun isVisionModel(modelId: String): Boolean {
        val lowerModelId = modelId.lowercase()
        return VISION_MODEL_PATTERNS.any { pattern -> 
            lowerModelId.contains(pattern) 
        }
    }
    
    companion object {
        // Vision model name patterns (HuggingFace models)
        private val VISION_MODEL_PATTERNS = listOf(
            "-vl",          // Qwen3-VL, Qwen2.5-VL, ERNIE-4.5-VL
            "-vl-",         // Qwen3-VL-8B
            "vl-",          // VL models
            "-vision",      // command-a-vision, aya-vision
            "vision-",      // vision models
            "4.5v",         // GLM-4.5V
            "4.6v",         // GLM-4.6V
            "glm-4v",       // GLM-4V variants
            "pixtral",      // Pixtral models
            "llava",        // LLaVA models
            "cogvlm",       // CogVLM models
            "internvl",     // InternVL models
            "qwen-vl",      // Qwen-VL
            "minicpm-v"     // MiniCPM-V
        )
    }

    /** Toggle streaming mode */
    fun toggleStreaming() {
        useStreaming = !useStreaming
    }
    
    /** Toggle MCP tools */
    fun toggleMCPTools() {
        mcpToolsEnabled = !mcpToolsEnabled
    }
    
    /** Check if MCP tools are available */
    fun hasMCPTools(): Boolean = MCPManager.getTotalToolCount() > 0

    /** Send a message and get AI response */
    fun sendMessage(messageText: String) {
        if (useStreaming) {
            sendMessageStreaming(messageText)
        } else {
            sendMessageNonStreaming(messageText)
        }
    }

    /** Send message with streaming (real-time tokens) */
    private fun sendMessageStreaming(messageText: String) {
        if (messageText.isBlank() && attachments.isEmpty() && pendingFiles.isEmpty()) return

        // Capture files before clearing
        val messageFiles = pendingFiles.toList()
        
        // Add user message with attachments and files
        val userMessage =
                Message(
                        id = System.currentTimeMillis().toString(),
                        content = messageText,
                        isUser = true,
                        timestamp = System.currentTimeMillis(),
                        attachments = attachments.toList(),
                        files = messageFiles
                )
        messages = messages + userMessage
        clearAttachments()
        clearPendingFiles()

        // Create or update conversation
        if (currentConversation == null) {
            val newId = UUID.randomUUID().toString()
            val newConversation =
                    Conversation(
                            id = newId,
                            title =
                                    messageText.take(30) +
                                            if (messageText.length > 30) "..." else "",
                            messages = messages,
                            timestamp = System.currentTimeMillis(),
                            model = selectedModelId
                    )
            currentConversation = newConversation
            conversations = listOf(newConversation) + conversations
            saveConversationToDatabase(newConversation)
        }

        // Add empty assistant message for streaming
        val assistantMessageId = System.currentTimeMillis().toString()
        var assistantMessage =
                Message(
                        id = assistantMessageId,
                        content = "",
                        isUser = false,
                        timestamp = System.currentTimeMillis(),
                        model = selectedModelId
                )
        messages = messages + assistantMessage

        // Check provider configuration
        val providerConfig = ConfigManager.getProviderConfig()
        if (!providerConfig.isValid()) {
            addSimulatedResponse()
            return
        }

        isLoading = true
        error = null

        streamingJob = viewModelScope.launch {
            try {
                // Build message history with files (exclude the empty assistant message)
                val apiMessages =
                        messages.dropLast(1).map { msg ->
                            MessagePreparer.ChatMessage(
                                    role = if (msg.isUser) "user" else "assistant",
                                    content = msg.content,
                                    files = msg.files
                            )
                        }
                
                // Check if we have multimodal content (images or PDFs)
                val hasMultimodalContent = MessagePreparer.hasMultimodalContent(apiMessages)
                
                // Check if MCP tools are available
                val hasMcpTools = mcpToolsEnabled && MCPManager.hasConnectedServers() && MCPManager.getTotalToolCount() > 0

                // Get MCP tools if enabled
                val mcpTools = if (hasMcpTools) {
                    MCPManager.getToolsForLLM().also {
                        Log.i(TAG, "Sending ${it.size} MCP tools to API")
                    }
                } else {
                    null
                }

                // Use LLM Router if Omni - BUT NOT for Google AI Studio Direct
                val providerIsGoogleDirect = providerConfig.provider == com.example.chat_ui.data.ApiProvider.GOOGLE_AI_STUDIO
                val modelToUse =
                        if (isOmniRouter() && !providerIsGoogleDirect) {
                            val legacyMessages = apiMessages.map { 
                                ChatApiClient.ChatMessage(it.role, it.content) 
                            }
                            LlmRouter.selectModel(legacyMessages, hasMultimodalContent, hasMcpTools)
                        } else {
                            selectedModelId
                        }

                Log.i(TAG, "Starting stream with model: $modelToUse, multimodal: $hasMultimodalContent, tools: ${mcpTools?.size ?: 0}")

                // Collect stream events with multimodal support
                ChatStreamingClient.chatCompletionStreamWithFiles(
                    messages = apiMessages,
                    model = modelToUse,
                    isMultimodal = isModelMultimodal && hasMultimodalContent,
                    tools = mcpTools
                ).collect { event ->
                    when (event) {
                        is StreamEvent.Token -> {
                            // Append token to assistant message
                            assistantMessage =
                                    assistantMessage.copy(
                                            content = assistantMessage.content + event.text
                                    )
                            // Update messages list
                            messages = messages.dropLast(1) + assistantMessage
                        }
                        is StreamEvent.Complete -> {
                            Log.i(
                                    TAG,
                                    "Stream complete. Full text length: ${event.fullText.length}"
                            )
                            
                            // Check for MCP tool calls in the response
                            if (mcpToolsEnabled && MCPToolExecutor.containsToolCall(event.fullText)) {
                                handleToolCalls(event.fullText, assistantMessageId)
                            } else {
                                isLoading = false
                                updateCurrentConversation()
                            }
                        }
                        is StreamEvent.Error -> {
                            Log.e(TAG, "Stream error: ${event.error}")
                            error = event.error
                            isLoading = false
                            // Replace empty message with error
                            val errorMessage =
                                    Message(
                                            id = assistantMessageId,
                                            content = "⚠️ Error: ${event.error}",
                                            isUser = false,
                                            timestamp = System.currentTimeMillis()
                                    )
                            messages = messages.dropLast(1) + errorMessage
                            updateCurrentConversation()
                        }
                        is StreamEvent.RouterMetadata -> {}
                        is StreamEvent.Status -> {}
                        is StreamEvent.KeepAlive -> {
                            // Ignore keep-alive
                        }
                        is StreamEvent.ToolCall -> {}
                        is StreamEvent.ToolCallsComplete -> {
                            Log.i(TAG, "Tool calls complete, executing tools...")
                            // Handle tool execution
                            if (mcpToolsEnabled) {
                                handleToolCalls(event.fullText, assistantMessageId)
                            } else {
                                isLoading = false
                                updateCurrentConversation()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Streaming error: ${e.message}", e)
                error = e.message
                isLoading = false
                val errorMessage =
                        Message(
                                id = assistantMessageId,
                                content = "⚠️ Error: ${e.message}",
                                isUser = false,
                                timestamp = System.currentTimeMillis()
                        )
                messages = messages.dropLast(1) + errorMessage
                updateCurrentConversation()
            }
        }
    }

    /** Send message without streaming (fallback) */
    private fun sendMessageNonStreaming(messageText: String) {
        if (messageText.isBlank() && attachments.isEmpty()) return

        // Add user message with attachments
        val userMessage =
                Message(
                        id = System.currentTimeMillis().toString(),
                        content = messageText,
                        isUser = true,
                        timestamp = System.currentTimeMillis(),
                        attachments = attachments.toList()
                )
        messages = messages + userMessage

        // Clear attachments after adding to message
        clearAttachments()

        // Create or update conversation
        if (currentConversation == null) {
            // Generate new UUID for new conversation
            val newId = UUID.randomUUID().toString()
            val newConversation =
                    Conversation(
                            id = newId,
                            title =
                                    messageText.take(30) +
                                            if (messageText.length > 30) "..." else "",
                            messages = messages,
                            timestamp = System.currentTimeMillis(),
                            model = selectedModelId
                    )
            currentConversation = newConversation
            conversations = listOf(newConversation) + conversations
            // Save new conversation to Firebase
            saveConversationToDatabase(newConversation)
        } else {
            updateCurrentConversation()
        }

        // Check if provider is configured
        val providerConfig = ConfigManager.getProviderConfig()
        if (!providerConfig.isValid()) {
            // Simulated response if no provider configured
            addSimulatedResponse()
            return
        }

        // Call API
        isLoading = true
        error = null

        viewModelScope.launch {
            try {
                // Build message history for API
                val apiMessages =
                        messages.map { msg ->
                            ChatApiClient.ChatMessage(
                                    role = if (msg.isUser) "user" else "assistant",
                                    content = msg.content
                            )
                        }

                // Check for images in messages
                val hasImages = messages.any { msg -> msg.files.any { it.isImage() } }
                
                // Check if MCP tools are available
                val hasMcpTools = mcpToolsEnabled && MCPManager.hasConnectedServers() && MCPManager.getTotalToolCount() > 0
                
                // Use LLM Router to select best model if using Omni
                val modelToUse =
                        if (isOmniRouter()) {
                            LlmRouter.selectModel(apiMessages, hasImages, hasMcpTools)
                        } else {
                            selectedModelId
                        }

                when (val result =
                                apiClient.chatCompletion(messages = apiMessages, model = modelToUse)
                ) {
                    is ChatApiClient.ApiResult.Success -> {
                        val aiMessage =
                                Message(
                                        id =
                                                result.data.id.ifEmpty {
                                                    System.currentTimeMillis().toString()
                                                },
                                        content = result.data.content,
                                        isUser = false,
                                        timestamp = System.currentTimeMillis()
                                )
                        messages = messages + aiMessage
                        updateCurrentConversation()
                    }
                    is ChatApiClient.ApiResult.Error -> {
                        error = result.message
                        // Add error message as AI response
                        val errorMessage =
                                Message(
                                        id = System.currentTimeMillis().toString(),
                                        content =
                                                "⚠️ Error: ${result.message}\n\nPlease check your API settings.",
                                        isUser = false,
                                        timestamp = System.currentTimeMillis()
                                )
                        messages = messages + errorMessage
                        updateCurrentConversation()
                    }
                }
            } catch (e: Exception) {
                error = e.message
                val errorMessage =
                        Message(
                                id = System.currentTimeMillis().toString(),
                                content = "⚠️ Error: ${e.message}",
                                isUser = false,
                                timestamp = System.currentTimeMillis()
                        )
                messages = messages + errorMessage
                updateCurrentConversation()
            } finally {
                isLoading = false
            }
        }
    }

    /** Retry last message */
    fun retryLastMessage() {
        val lastUserMessage = messages.lastOrNull { it.isUser }
        if (lastUserMessage != null) {
            // Remove last AI response if exists
            val lastMessage = messages.lastOrNull()
            if (lastMessage != null && !lastMessage.isUser) {
                messages = messages.dropLast(1)
            }
            // Re-send
            sendMessage(lastUserMessage.content)
        }
    }
    
    /** Stop the current generation */
    fun stopGeneration() {
        // Cancel the HTTP streaming connection directly
        ChatStreamingClient.cancelCurrentStream()
        
        // Also cancel the coroutine job
        streamingJob?.cancel()
        streamingJob = null
        isLoading = false
        
        // Save whatever was generated so far
        updateCurrentConversation()
    }

    private fun addSimulatedResponse() {
        viewModelScope.launch {
            isLoading = true
            kotlinx.coroutines.delay(1000) // Simulate network delay

            val aiResponse =
                    Message(
                            id = System.currentTimeMillis().toString(),
                            content =
                                    buildString {
                                        appendLine("👋 مرحباً! أنا نموذج **$selectedModelId**.")
                                        appendLine()
                                        appendLine("⚠️ **لم يتم تكوين API Provider**")
                                        appendLine()
                                        appendLine("لتفعيل الردود الحقيقية:")
                                        appendLine("1. اذهب إلى **Settings** ⚙️")
                                        appendLine("2. اختر **API Settings**")
                                        appendLine("3. اختر **API Provider** (HuggingFace أو Google)")
                                        appendLine("4. أكمل الإعداد المطلوب")
                                        appendLine()
                                        appendLine("المزودين المدعومين:")
                                        appendLine("- **HuggingFace** (114+ نموذج)")
                                        appendLine("- **Google Vertex AI** (Gemini models)")
                                    },
                            isUser = false,
                            timestamp = System.currentTimeMillis()
                    )
            messages = messages + aiResponse
            updateCurrentConversation()
            isLoading = false
        }
    }

    /** Update current conversation and save to database */
    private fun updateCurrentConversation() {
        currentConversation = currentConversation?.copy(messages = messages)
        currentConversation?.let { conv ->
            conversations = conversations.map { if (it.id == conv.id) conv else it }
            // Save to database
            saveConversationToDatabase(conv)
        }
    }

    /** Save conversation to Firebase Realtime Database */
    private fun saveConversationToDatabase(conversation: Conversation) {
        viewModelScope.launch {
            try {
                // Convert messages to map format for Realtime Database
                val messagesData =
                        conversation.messages.map { msg ->
                            mapOf(
                                    "id" to msg.id,
                                    "content" to msg.content,
                                    "isUser" to msg.isUser,
                                    "timestamp" to msg.timestamp
                            )
                        }

                FirebaseDatabaseManager.saveConversation(
                        conversationId = conversation.id,
                        title = conversation.title,
                        model = conversation.model,
                        messages = messagesData
                )
                Log.i(TAG, "Saved conversation ${conversation.id} to Realtime Database")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save conversation: ${e.message}", e)
            }
        }
    }

    /** Upload attachment (image or file) */
    fun uploadAttachment(context: Context, uri: Uri) {
        viewModelScope.launch {
            isUploadingAttachment = true
            try {
                FileAttachmentManager.uploadFile(context, uri)
                        .onSuccess { attachment ->
                            attachments = attachments + attachment
                            Log.i(TAG, "Attachment uploaded: ${attachment.name}")
                        }
                        .onFailure { uploadError ->
                            error = "Upload failed: ${uploadError.message}"
                            Log.e(TAG, "Upload failed", uploadError)
                        }
            } finally {
                isUploadingAttachment = false
            }
        }
    }

    /** Remove attachment */
    fun removeAttachment(attachment: Attachment) {
        attachments = attachments.filter { it.id != attachment.id }
    }

    /** Clear all attachments */
    private fun clearAttachments() {
        attachments = emptyList()
    }
    
    /** Clear pending files */
    private fun clearPendingFiles() {
        pendingFiles = emptyList()
    }
    
    /** Add a file for multimodal API */
    fun addPendingFile(file: MessageFile) {
        pendingFiles = pendingFiles + file
        Log.i(TAG, "Added pending file: ${file.name} (${file.mime})")
    }
    
    /** Remove a pending file */
    fun removePendingFile(file: MessageFile) {
        pendingFiles = pendingFiles.filter { it.name != file.name || it.value != file.value }
    }
    
    /** Add image file from Uri (with processing) */
    fun addImageFromUri(context: Context, uri: Uri, fileName: String, mimeType: String) {
        viewModelScope.launch {
            isUploadingAttachment = true
            try {
                val processedFile = ImageProcessor.processImageFromUri(
                    context = context,
                    uri = uri,
                    fileName = fileName,
                    mimeType = mimeType
                )
                
                if (processedFile != null) {
                    pendingFiles = pendingFiles + processedFile
                    Log.i(TAG, "Image processed and added: ${processedFile.name}")
                } else {
                    error = "Failed to process image"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add image: ${e.message}", e)
                error = "Failed to add image: ${e.message}"
            } finally {
                isUploadingAttachment = false
            }
        }
    }
    
    /** Add text file from Uri */
    fun addTextFileFromUri(context: Context, uri: Uri, fileName: String, mimeType: String) {
        viewModelScope.launch {
            isUploadingAttachment = true
            try {
                val file = MessageFile.fromUri(context, uri, fileName, mimeType)
                if (file != null) {
                    pendingFiles = pendingFiles + file
                    Log.i(TAG, "Text file added: ${file.name}")
                } else {
                    error = "Failed to read file"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add file: ${e.message}", e)
                error = "Failed to add file: ${e.message}"
            } finally {
                isUploadingAttachment = false
            }
        }
    }
    
    /** Check if pending files contain images */
    fun hasPendingImages(): Boolean = pendingFiles.any { it.isImage() }
    
    /** Get pending files count */
    fun getPendingFilesCount(): Int = pendingFiles.size

    /** Clear error */
    fun clearError() {
        error = null
    }
    
    /** Handle MCP tool calls from LLM response */
    private fun handleToolCalls(responseContent: String, originalMessageId: String) {
        viewModelScope.launch {
            isExecutingTool = true
            Log.i(TAG, "Detected tool calls in response, executing...")
            
            try {
                // Execute all tool calls
                val results = MCPToolExecutor.executeAllToolCalls(responseContent)
                
                if (results.isNotEmpty()) {
                    // Format results
                    val formattedResults = MCPToolExecutor.formatToolResults(results)
                    
                    Log.i(TAG, "Tool execution complete. Results: ${formattedResults.take(200)}...")
                    
                    // Send results back to LLM for final answer
                    sendToolResultsToLLM(responseContent, formattedResults, originalMessageId)
                } else {
                    Log.w(TAG, "No tool results received")
                    isExecutingTool = false
                    isLoading = false
                    updateCurrentConversation()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Tool execution failed: ${e.message}", e)
                error = "Tool execution failed: ${e.message}"
                isExecutingTool = false
                isLoading = false
            }
        }
    }
    
    /** Send tool results back to LLM for final answer */
    private fun sendToolResultsToLLM(originalResponse: String, toolResults: String, messageId: String) {
        viewModelScope.launch {
            try {
                // Build messages including tool results
                val apiMessages = messages.dropLast(1).map { msg ->
                    MessagePreparer.ChatMessage(
                        role = if (msg.isUser) "user" else "assistant",
                        content = msg.content,
                        files = msg.files
                    )
                }
                
                // Add assistant's tool call response
                val toolCallMessage = MessagePreparer.ChatMessage(
                    role = "assistant",
                    content = originalResponse
                )
                
                // Add tool results as user message
                val toolResultMessage = MessagePreparer.ChatMessage(
                    role = "user",
                    content = "Tool Results:\n$toolResults\n\nPlease provide a helpful answer based on these results."
                )
                
                val fullMessages = apiMessages + toolCallMessage + toolResultMessage
                
                // Get model to use
                val modelToUse = if (isOmniRouter()) {
                    val legacyMessages = fullMessages.map { ChatApiClient.ChatMessage(it.role, it.content) }
                    LlmRouter.selectModel(legacyMessages, false, false)
                } else {
                    selectedModelId
                }
                
                // Update the assistant message to show "Processing..."
                val currentMsg = messages.find { it.id == messageId }
                if (currentMsg != null) {
                    messages = messages.map { 
                        if (it.id == messageId) it.copy(content = originalResponse + "\n\n⏳ جاري معالجة النتائج...") 
                        else it 
                    }
                }
                
                // Wrap tool call and results in collapsible tags
                val toolCallWithResults = "$originalResponse\n<search_results>\n$toolResults\n</search_results>\n\n"
                
                // Stream the continuation without tools (to avoid loop)
                var continuationText = ""
                ChatStreamingClient.chatCompletionStreamWithFiles(
                    messages = fullMessages,
                    model = modelToUse,
                    isMultimodal = false,
                    tools = null // Don't send tools for continuation
                ).collect { event ->
                    when (event) {
                        is StreamEvent.Token -> {
                            continuationText += event.text
                            // Update: tool_call + search_results in fold, then model response outside
                            messages = messages.map { 
                                if (it.id == messageId) it.copy(content = toolCallWithResults + continuationText) 
                                else it 
                            }
                        }
                        is StreamEvent.Complete -> {
                            Log.i(TAG, "Tool continuation complete")
                            // Final update: tool_call + search_results in fold, then model response
                            messages = messages.map { 
                                if (it.id == messageId) it.copy(content = toolCallWithResults + continuationText) 
                                else it 
                            }
                            isExecutingTool = false
                            isLoading = false
                            updateCurrentConversation()
                        }
                        is StreamEvent.Error -> {
                            Log.e(TAG, "Continuation error: ${event.error}")
                            // Still show results in fold even if continuation fails
                            messages = messages.map { 
                                if (it.id == messageId) it.copy(content = toolCallWithResults + "⚠️ خطأ في المتابعة") 
                                else it 
                            }
                            isExecutingTool = false
                            isLoading = false
                            updateCurrentConversation()
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send tool results to LLM: ${e.message}", e)
                isExecutingTool = false
                isLoading = false
            }
        }
    }
    
    /** Get MCP tools count */
    fun getMCPToolsCount(): Int = MCPManager.getTotalToolCount()
    
    /** Get connected MCP servers count */
    fun getConnectedServersCount(): Int = MCPManager.getConnectedServerCount()
    
    // ===================================
    // Alternatives Support
    // ===================================
    
    /**
     * Change the displayed alternative for a message
     */
    fun changeAlternative(messageId: String, newIndex: Int) {
        messages = messages.map { msg ->
            if (msg.id == messageId && !msg.isUser) {
                val validIndex = newIndex.coerceIn(0, msg.getAlternativesCount() - 1)
                msg.copy(currentAlternativeIndex = validIndex)
            } else {
                msg
            }
        }
        updateCurrentConversation()
    }
    
    /**
     * Regenerate the last assistant message - deletes old response and generates new one
     */
    fun regenerateLastMessage() {
        // Find the last assistant message
        val lastAssistantIndex = messages.indexOfLast { !it.isUser }
        if (lastAssistantIndex < 0) return
        
        // Remove the last assistant message
        messages = messages.take(lastAssistantIndex)
        updateCurrentConversation()
        
        // Create new empty assistant message for streaming
        var assistantMessage = Message(
            id = UUID.randomUUID().toString(),
            content = "",
            isUser = false,
            timestamp = System.currentTimeMillis()
        )
        messages = messages + assistantMessage
        
        // Start regeneration
        isLoading = true
        error = null
        
        viewModelScope.launch {
            try {
                val apiKey = ConfigManager.openAiApiKey
                if (apiKey.isBlank()) {
                    // Simulated response for demo
                    assistantMessage = assistantMessage.copy(
                        content = "This is a regenerated response (simulated)."
                    )
                    messages = messages.dropLast(1) + assistantMessage
                    updateCurrentConversation()
                    return@launch
                }
                
                // Build message history (without the new empty assistant message)
                val apiMessages = messages.dropLast(1).map { msg ->
                    MessagePreparer.ChatMessage(
                        role = if (msg.isUser) "user" else "assistant",
                        content = msg.content,
                        files = msg.files
                    )
                }
                
                // Check for multimodal content
                val hasMultimodal = MessagePreparer.hasMultimodalContent(apiMessages)
                
                // Check if MCP tools are available
                val hasMcpTools = mcpToolsEnabled && MCPManager.hasConnectedServers() && MCPManager.getTotalToolCount() > 0
                
                // Use LLM Router if Omni - pass hasImages and hasTools for model selection
                val modelToUse = if (isOmniRouter()) {
                    val legacyMessages = apiMessages.map { 
                        ChatApiClient.ChatMessage(it.role, it.content) 
                    }
                    LlmRouter.selectModel(legacyMessages, hasMultimodal, hasMcpTools)
                } else {
                    selectedModelId
                }
                
                // Stream new response
                ChatStreamingClient.chatCompletionStreamWithFiles(
                    messages = apiMessages,
                    model = modelToUse,
                    isMultimodal = hasMultimodal
                ).collect { event ->
                    when (event) {
                        is StreamEvent.Token -> {
                            assistantMessage = assistantMessage.copy(
                                content = assistantMessage.content + event.text
                            )
                            messages = messages.dropLast(1) + assistantMessage
                        }
                        is StreamEvent.Complete -> {
                            updateCurrentConversation()
                        }
                        is StreamEvent.Error -> {
                            error = event.error
                        }
                        else -> { /* KeepAlive, RouterMetadata, Status - ignore */ }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Regeneration failed: ${e.message}", e)
                error = "Regeneration failed: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
    
    /**
     * Add a new alternative to a message
     */
    private fun addAlternativeToMessage(messageId: String, newContent: String) {
        messages = messages.map { msg ->
            if (msg.id == messageId && !msg.isUser) {
                val currentAlternatives = if (msg.alternatives.isEmpty()) {
                    listOf(msg.content) // First alternative is the original content
                } else {
                    msg.alternatives
                }
                val newAlternatives = currentAlternatives + newContent
                msg.copy(
                    alternatives = newAlternatives,
                    currentAlternativeIndex = newAlternatives.lastIndex // Show the new one
                )
            } else {
                msg
            }
        }
        updateCurrentConversation()
    }
    
    // ===================================
    // Image Generation in Chat
    // ===================================
    
    /**
     * Generate image from prompt in chat context
     */
    fun generateImageInChat(
        prompt: String,
        modelId: String = "google/imagen-4.0-generate-001",
        context: Context,
        saveToGallery: Boolean = true
    ) {
        if (prompt.isBlank()) {
            imageGenerationError = "Prompt cannot be empty"
            return
        }
        
        isGeneratingImage = true
        imageGenerationError = null
        
        // Add user message with prompt
        val userMessage = Message(
            id = UUID.randomUUID().toString(),
            content = "🎨 Generate image: $prompt",
            isUser = true,
            timestamp = System.currentTimeMillis()
        )
        messages = messages + userMessage
        
        // Add assistant message placeholder
        var assistantMessage = Message(
            id = UUID.randomUUID().toString(),
            content = "Generating image...",
            isUser = false,
            timestamp = System.currentTimeMillis()
        )
        messages = messages + assistantMessage
        
        viewModelScope.launch {
            try {
                val result = imageGenClient.generateImage(
                    prompt = prompt,
                    modelId = modelId,
                    context = context,
                    saveToGallery = saveToGallery
                )
                
                when (result) {
                    is ImageGenerationApiClient.ImageGenResult.Success -> {
                        val imageUrls = result.images.mapNotNull { img -> img.cloudinaryUrl }
                        
                        assistantMessage = assistantMessage.copy(
                            content = "Generated ${imageUrls.size} image(s):",
                            generatedImages = imageUrls
                        )
                        messages = messages.dropLast(1) + assistantMessage
                        updateCurrentConversation()
                    }
                    is ImageGenerationApiClient.ImageGenResult.Error -> {
                        assistantMessage = assistantMessage.copy(
                            content = "❌ Error: ${result.message}"
                        )
                        messages = messages.dropLast(1) + assistantMessage
                        imageGenerationError = result.message
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Image generation failed: ${e.message}", e)
                assistantMessage = assistantMessage.copy(
                    content = "❌ Failed to generate image: ${e.message}"
                )
                messages = messages.dropLast(1) + assistantMessage
                imageGenerationError = e.message
            } finally {
                isGeneratingImage = false
                updateCurrentConversation()
            }
        }
    }
    
    /**
     * Clear image generation error
     */
    fun clearImageGenerationError() {
        imageGenerationError = null
    }
}
