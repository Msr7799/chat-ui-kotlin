
package com.example.chat_ui.api

/**
 * Stream events for real-time chat responses
 * Matches JavaScript MessageUpdate types
 */
sealed class StreamEvent {
    /**
     * Token received from stream
     */
    data class Token(
        val text: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : StreamEvent()
    
    /**
     * Status update (loading, error, etc.)
     */
    data class Status(
        val message: String,
        val isError: Boolean = false,
        val statusCode: Int? = null
    ) : StreamEvent()
    
    /**
     * Router metadata (route, model, provider)
     */
    data class RouterMetadata(
        val route: String,
        val model: String,
        val provider: String?
    ) : StreamEvent()
    
    /**
     * Final answer received
     */
    data class Complete(
        val fullText: String,
        val interrupted: Boolean = false
    ) : StreamEvent()
    
    /**
     * Error occurred
     */
    data class Error(
        val error: String,
        val statusCode: Int? = null
    ) : StreamEvent()
    
    /**
     * Keep-alive ping
     */
    object KeepAlive : StreamEvent()
    
    /**
     * Tool call request from LLM
     */
    data class ToolCall(
        val index: Int,
        val id: String?,
        val name: String?,
        val arguments: String?
    ) : StreamEvent()
    
    /**
     * All tool calls completed - need to execute tools
     */
    data class ToolCallsComplete(
        val fullText: String
    ) : StreamEvent()
}
