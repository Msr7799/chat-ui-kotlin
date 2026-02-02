package com.example.chat_ui.mcp

import android.util.Log
import com.example.chat_ui.config.ConfigManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/**
 * MCP Client - Implements Model Context Protocol over SSE/HTTP
 * Based on the official MCP SDK protocol
 */
class MCPClient(
    private val serverConfig: MCPServerConfig
) {
    private val TAG = "MCPClient"
    private val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
    }
    
    private val requestId = AtomicInteger(1)
    private var sessionId: String? = null
    private var initialized = false
    
    /**
     * Get authorization headers for MCP requests
     * Adds default token if no Authorization header is present
     */
    private fun getAuthHeaders(): Map<String, String> {
        val headers = serverConfig.headers.toMutableMap()
        
        // Add default token if no Authorization header present
        if (!MCPUtils.hasAuthHeader(headers)) {
            try {
                val token = ConfigManager.get(ConfigManager.Keys.OPENAI_API_KEY, "")
                if (MCPUtils.hasNonEmptyToken(token)) {
                    headers["Authorization"] = "Bearer $token"
                    Log.d(TAG, "✓ Added auth token for: ${serverConfig.url}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get token: ${e.message}", e)
            }
        }
        
        return headers
    }
    
    /**
     * Initialize MCP session
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (initialized) return@withContext true
        
        try {
            val initRequest = buildJsonObject {
                put("jsonrpc", JsonPrimitive("2.0"))
                put("id", JsonPrimitive(requestId.getAndIncrement()))
                put("method", JsonPrimitive("initialize"))
                put("params", buildJsonObject {
                    put("protocolVersion", JsonPrimitive("2024-11-05"))
                    put("capabilities", buildJsonObject {
                        put("tools", buildJsonObject {})
                    })
                    put("clientInfo", buildJsonObject {
                        put("name", JsonPrimitive("ChatUI-Android"))
                        put("version", JsonPrimitive("1.0.0"))
                    })
                })
            }
            
            val response = sendJsonRpcRequest(initRequest)
            
            if (response != null && response.containsKey("result")) {
                Log.i(TAG, "MCP initialized for ${serverConfig.name}")
                
                // Send initialized notification
                val notifyRequest = buildJsonObject {
                    put("jsonrpc", JsonPrimitive("2.0"))
                    put("method", JsonPrimitive("notifications/initialized"))
                }
                sendJsonRpcRequest(notifyRequest, expectResponse = false)
                
                initialized = true
                return@withContext true
            }
            
            Log.e(TAG, "Initialize failed: $response")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Initialize error: ${e.message}", e)
            false
        }
    }
    
    /**
     * List available tools from MCP server
     */
    suspend fun listTools(): List<MCPTool> = withContext(Dispatchers.IO) {
        if (!initialized && !initialize()) {
            Log.w(TAG, "Failed to initialize before listing tools")
            return@withContext emptyList()
        }
        
        try {
            val request = buildJsonObject {
                put("jsonrpc", JsonPrimitive("2.0"))
                put("id", JsonPrimitive(requestId.getAndIncrement()))
                put("method", JsonPrimitive("tools/list"))
                put("params", buildJsonObject {})
            }
            
            Log.d(TAG, "Sending tools/list request to ${serverConfig.name}")
            val response = sendJsonRpcRequest(request)
            
            if (response != null) {
                Log.d(TAG, "Got response: ${response.toString().take(200)}")
                val result = response["result"]?.jsonObject
                val toolsArray = result?.get("tools")?.jsonArray
                
                if (toolsArray == null) {
                    Log.w(TAG, "No tools array in response for ${serverConfig.name}. Response: ${response.toString().take(300)}")
                    return@withContext emptyList()
                }
                
                Log.i(TAG, "Found ${toolsArray.size} tools for ${serverConfig.name}")
                
                return@withContext toolsArray.mapNotNull { toolJson ->
                    try {
                        MCPTool(
                            name = toolJson.jsonObject["name"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                            description = toolJson.jsonObject["description"]?.jsonPrimitive?.contentOrNull,
                            inputSchema = toolJson.jsonObject["inputSchema"],
                            serverId = serverConfig.id
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse tool: ${e.message}")
                        null
                    }
                }
            }
            
            Log.w(TAG, "No response from tools/list for ${serverConfig.name}")
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "List tools error for ${serverConfig.name}: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Call a tool on the MCP server
     */
    suspend fun callTool(name: String, arguments: Map<String, JsonElement>): MCPToolResult = withContext(Dispatchers.IO) {
        if (!initialized && !initialize()) {
            return@withContext MCPToolResult("Server not initialized", true, name)
        }
        
        try {
            val request = buildJsonObject {
                put("jsonrpc", JsonPrimitive("2.0"))
                put("id", JsonPrimitive(requestId.getAndIncrement()))
                put("method", JsonPrimitive("tools/call"))
                put("params", buildJsonObject {
                    put("name", JsonPrimitive(name))
                    put("arguments", buildJsonObject {
                        arguments.forEach { (key, value) ->
                            put(key, value)
                        }
                    })
                })
            }
            
            val response = sendJsonRpcRequest(request)
            
            if (response != null) {
                // Check for error
                val error = response["error"]?.jsonObject
                if (error != null) {
                    val errorMsg = error["message"]?.jsonPrimitive?.contentOrNull ?: "Unknown error"
                    return@withContext MCPToolResult(errorMsg, true, name)
                }
                
                val result = response["result"]?.jsonObject
                val content = result?.get("content")?.let { contentElement ->
                    when {
                        contentElement is JsonArray -> {
                            contentElement.joinToString("\n") { item ->
                                item.jsonObject["text"]?.jsonPrimitive?.contentOrNull 
                                    ?: item.toString()
                            }
                        }
                        contentElement is JsonPrimitive -> contentElement.contentOrNull ?: ""
                        else -> contentElement.toString()
                    }
                } ?: "No content"
                
                val isError = result?.get("isError")?.jsonPrimitive?.booleanOrNull ?: false
                return@withContext MCPToolResult(content, isError, name)
            }
            
            MCPToolResult("No response from server", true, name)
        } catch (e: Exception) {
            Log.e(TAG, "Call tool error: ${e.message}", e)
            MCPToolResult("Tool call failed: ${e.message}", true, name)
        }
    }
    
    /**
     * Send JSON-RPC request to MCP server
     * Tries HTTP POST first, falls back to SSE if needed
     */
    private suspend fun sendJsonRpcRequest(
        request: JsonObject,
        expectResponse: Boolean = true
    ): JsonObject? = withContext(Dispatchers.IO) {
        val headers = getAuthHeaders()
        
        // Try HTTP POST first (Streamable HTTP transport)
        try {
            val result = sendHttpPost(request, headers)
            if (result != null) return@withContext result
        } catch (e: Exception) {
            Log.d(TAG, "HTTP POST failed, trying SSE: ${e.message}")
        }
        
        // Try SSE transport as fallback
        if (expectResponse) {
            try {
                return@withContext sendSseRequest(request, headers)
            } catch (e: Exception) {
                Log.e(TAG, "SSE request failed: ${e.message}")
            }
        }
        
        null
    }
    
    /**
     * Send request via HTTP POST (Streamable HTTP transport)
     */
    private fun sendHttpPost(request: JsonObject, headers: Map<String, String>): JsonObject? {
        val url = URL(serverConfig.url)
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.doInput = true
            connection.connectTimeout = 30000 // 30 seconds
            connection.readTimeout = 30000 // 30 seconds
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json, text/event-stream")
            connection.setRequestProperty("User-Agent", "ChatUI-Android/1.0")
            
            headers.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }
            
            // Send request
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(request.toString())
                writer.flush()
            }
            
            val responseCode = connection.responseCode
            
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                val contentType = connection.contentType ?: ""
                
                BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    val response = StringBuilder()
                    
                    if (contentType.contains("text/event-stream")) {
                        // Parse SSE response
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            if (line!!.startsWith("data:")) {
                                val data = line!!.substring(5).trim()
                                if (data.isNotEmpty() && data != "[DONE]") {
                                    return try {
                                        json.parseToJsonElement(data).jsonObject
                                    } catch (e: Exception) {
                                        null
                                    }
                                }
                            }
                        }
                    } else {
                        // Parse JSON response
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            response.append(line)
                        }
                        
                        if (response.isNotEmpty()) {
                            return try {
                                val responseStr = response.toString()
                                Log.d(TAG, "HTTP Response (${responseStr.length} chars): ${responseStr.take(500)}")
                                json.parseToJsonElement(responseStr).jsonObject
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to parse response: ${e.message}\nResponse: ${response.toString().take(200)}")
                                null
                            }
                        }
                    }
                }
            } else {
                val errorStream = connection.errorStream
                if (errorStream != null) {
                    val error = BufferedReader(InputStreamReader(errorStream)).readText()
                    Log.e(TAG, "HTTP error $responseCode: $error")
                }
                throw Exception("HTTP $responseCode")
            }
        } finally {
            connection.disconnect()
        }
        
        return null
    }
    
    /**
     * Send request via SSE (Server-Sent Events)
     */
    private fun sendSseRequest(request: JsonObject, headers: Map<String, String>): JsonObject? {
        // For SSE, we need to establish a connection and listen for events
        val url = URL(serverConfig.url)
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.doInput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "text/event-stream")
            connection.setRequestProperty("Cache-Control", "no-cache")
            
            headers.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }
            
            // Send request
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(request.toString())
                writer.flush()
            }
            
            val responseCode = connection.responseCode
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        if (line!!.startsWith("data:")) {
                            val data = line!!.substring(5).trim()
                            if (data.isNotEmpty() && data != "[DONE]") {
                                return try {
                                    val jsonResponse = json.parseToJsonElement(data).jsonObject
                                    // Check if this is the response we're looking for
                                    if (jsonResponse.containsKey("result") || jsonResponse.containsKey("error")) {
                                        return jsonResponse
                                    }
                                    null
                                } catch (e: Exception) {
                                    null
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        
        return null
    }
    
    /**
     * Close the client
     */
    fun close() {
        initialized = false
        sessionId = null
    }
}
