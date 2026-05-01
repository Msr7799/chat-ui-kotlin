package com.example.chat_ui.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * MCP Server Configuration
 * Similar to Chat UI's server config types
 */
@Serializable
data class MCPServerConfig(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val type: MCPTransportType = MCPTransportType.SSE,
    val headers: Map<String, String> = emptyMap()
)

/**
 * Transport type for MCP connection
 */
@Serializable
enum class MCPTransportType {
    SSE,        // Server-Sent Events (HTTP)
    WEBSOCKET,  // WebSocket
    STDIO       // Standard I/O (for local servers)
}

/**
 * MCP Tool definition
 * Represents a tool exposed by an MCP server
 */
@Serializable
data class MCPTool(
    val name: String,
    val description: String? = null,
    val inputSchema: JsonElement? = null,
    val serverId: String = ""
)

/**
 * MCP Tool Call Request
 */
@Serializable
data class MCPToolCall(
    val toolName: String,
    val arguments: Map<String, JsonElement> = emptyMap(),
    val serverId: String
)

/**
 * MCP Tool Call Result
 */
@Serializable
data class MCPToolResult(
    val content: String,
    val isError: Boolean = false,
    val toolName: String = ""
)

data class MCPToolExecutionTrace(
    val toolName: String,
    val serverId: String,
    val serverName: String,
    val input: Map<String, JsonElement>,
    val result: MCPToolResult,
    val durationMs: Long
)

/**
 * MCP Server Connection State
 */
enum class MCPConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    STANDBY,
    ERROR,
}

/**
 * MCP Server Status
 */
data class MCPServerStatus(
    val serverId: String,
    val serverName: String,
    val state: MCPConnectionState,
    val error: String? = null,
    val toolCount: Int = 0
)

/**
 * Sanitize tool name to match OpenAI requirements: ^[a-zA-Z0-9_-]{1,64}$
 * Same as chat-ui's sanitizeName function
 */
fun sanitizeToolName(name: String): String {
    return name.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(64)
}

/**
 * MCP Tool Mapping - Maps sanitized function name to original tool
 */
data class MCPToolMapping(
    val fnName: String,        // Sanitized name sent to LLM
    val serverName: String,    // Server that owns this tool
    val originalName: String,  // Original tool name
    val serverId: String
)

/**
 * Convert MCP tools to LLM-compatible format (OpenAI function calling)
 * With name sanitization and collision handling
 * Returns JsonArray for direct serialization
 */
fun List<MCPTool>.toLLMToolsWithMapping(): Pair<kotlinx.serialization.json.JsonArray, Map<String, MCPToolMapping>> {
    val tools = mutableListOf<kotlinx.serialization.json.JsonElement>()
    val mapping = mutableMapOf<String, MCPToolMapping>()
    val seenNames = mutableSetOf<String>()
    
    for (tool in this) {
        var sanitizedName = sanitizeToolName(tool.name)
        
        // Handle collision - add server suffix
        if (sanitizedName in seenNames) {
            val serverSuffix = sanitizeToolName(tool.serverId).take(20)
            var candidate = "${sanitizedName}_$serverSuffix".take(64)
            
            // If still collision, add numeric suffix
            if (candidate in seenNames) {
                var i = 2
                while ("${candidate}_$i".take(64) in seenNames && i < 10) {
                    i++
                }
                candidate = "${candidate}_$i".take(64)
            }
            sanitizedName = candidate
        }
        
        seenNames.add(sanitizedName)
        
        // Parse inputSchema properly for OpenAI
        val parameters = try {
            tool.inputSchema?.let { schema ->
                kotlinx.serialization.json.Json.parseToJsonElement(schema.toString())
            } ?: kotlinx.serialization.json.JsonObject(emptyMap())
        } catch (e: Exception) {
            kotlinx.serialization.json.JsonObject(mapOf(
                "type" to kotlinx.serialization.json.JsonPrimitive("object"),
                "properties" to kotlinx.serialization.json.JsonObject(emptyMap())
            ))
        }
        
        // Build tool as JsonObject
        val toolJson = kotlinx.serialization.json.buildJsonObject {
            put("type", kotlinx.serialization.json.JsonPrimitive("function"))
            put("function", kotlinx.serialization.json.buildJsonObject {
                put("name", kotlinx.serialization.json.JsonPrimitive(sanitizedName))
                put("description", kotlinx.serialization.json.JsonPrimitive(tool.description ?: "No description"))
                put("parameters", parameters)
            })
        }
        tools.add(toolJson)
        
        mapping[sanitizedName] = MCPToolMapping(
            fnName = sanitizedName,
            serverName = tool.serverId,
            originalName = tool.name,
            serverId = tool.serverId
        )
    }
    
    return Pair(kotlinx.serialization.json.JsonArray(tools), mapping)
}

/**
 * Convert MCP tools to LLM-compatible format (OpenAI function calling)
 * Simple version without mapping (backwards compatible)
 */
fun List<MCPTool>.toLLMTools(): kotlinx.serialization.json.JsonArray {
    return toLLMToolsWithMapping().first
}
