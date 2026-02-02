package com.example.chat_ui.mcp

import android.content.Context
import android.util.Log
import com.example.chat_ui.config.ConfigManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap

/**
 * MCP Manager - Manages MCP server connections and tool execution
 * Thread-safe implementation with proper resource management
 * Similar to Chat UI's MCP client implementation
 */
object MCPManager {
    private const val TAG = "MCPManager"
    private const val PREFS_NAME = "mcp_servers"
    private const val SERVERS_KEY = "servers_json"
    private const val DEFAULT_SERVERS_INITIALIZED = "default_servers_initialized"
    
    // Tool limits
    const val MAX_TOOLS = 100
    const val WARNING_TOOLS = 60
    
    // Default built-in servers (same as Chat UI JavaScript)
    // All servers are disabled by default - user must enable them
    private val defaultServers = listOf(

        MCPServerConfig(
            id = "exa-web-search",
            name = "Web Search (Exa)",
            url = "https://mcp.exa.ai/mcp",
            type = MCPTransportType.SSE,
            enabled = false,
            headers = emptyMap()
        )
    )
    
    // Active MCP clients - thread-safe ConcurrentHashMap
    private val clients = ConcurrentHashMap<String, MCPClient>()
    
    // Mutex for coordinating client operations
    private val clientsMutex = Mutex()
    
    /**
     * Get HuggingFace API token from ConfigManager
    */
    private val json = Json { 
        ignoreUnknownKeys = true 
        prettyPrint = true
        encodeDefaults = true
    }

    private val _servers = MutableStateFlow<List<MCPServerConfig>>(emptyList())
    val servers: StateFlow<List<MCPServerConfig>> = _servers.asStateFlow()

    private val _serverStatuses = MutableStateFlow<Map<String, MCPServerStatus>>(emptyMap())
    val serverStatuses: StateFlow<Map<String, MCPServerStatus>> = _serverStatuses.asStateFlow()

    private val _tools = MutableStateFlow<List<MCPTool>>(emptyList())
    val tools: StateFlow<List<MCPTool>> = _tools.asStateFlow()
    
    // Tool mapping for name resolution (sanitized name -> original)
    private val _toolMapping = MutableStateFlow<Map<String, MCPToolMapping>>(emptyMap())
    val toolMapping: StateFlow<Map<String, MCPToolMapping>> = _toolMapping.asStateFlow()
    
    // Tool cache with TTL (same as chat-ui: 60 seconds)
    private var toolsCacheTimestamp: Long = 0
    private const val CACHE_TTL_MS = 60_000L

    // Coroutine scope with proper lifecycle management
    private var scope: CoroutineScope? = null
    private val scopeLock = Any()
    
    /**
     * Get or create scope (thread-safe)
     */
    private fun getScope(): CoroutineScope {
        synchronized(scopeLock) {
            if (scope == null || !scope!!.isActive) {
                scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            }
            return scope!!
        }
    }

    /**
     * Initialize MCP Manager with saved servers
     */
    fun init(context: Context) {
        loadServers(context)
        Log.i(TAG, "MCPManager initialized")
    }
    
    /**
     * Cleanup MCP Manager resources
     * Call this when app is being destroyed
     */
    fun cleanup() {
        synchronized(scopeLock) {
            try {
                // Cancel all active connections
                runBlocking {
                    _servers.value.forEach { server ->
                        disconnectFromServer(server.id)
                    }
                }
                
                // Clear all collections
                clients.clear()
                _tools.value = emptyList()
                _serverStatuses.value = emptyMap()
                _toolMapping.value = emptyMap()
                
                // Cancel scope
                scope?.cancel()
                scope = null
                
                Log.i(TAG, "MCPManager cleaned up successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error during cleanup: ${e.message}", e)
            }
        }
    }

    private fun loadServers(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val serversJson = prefs.getString(SERVERS_KEY, null)
            val initialized = prefs.getBoolean(DEFAULT_SERVERS_INITIALIZED, false)
            
            // Load servers but force all to disabled on app start (user must enable manually)
            val loadedServers = if (serversJson != null) {
                json.decodeFromString<List<MCPServerConfig>>(serversJson).map { it.copy(enabled = false) }
            } else {
                emptyList()
            }
            
            // Add default servers if not already initialized
            if (!initialized) {
                val existingIds = loadedServers.map { it.id }
                val newDefaults = defaultServers.filter { it.id !in existingIds }
                
                _servers.value = loadedServers + newDefaults
                
                // Mark as initialized and save
                prefs.edit()
                    .putBoolean(DEFAULT_SERVERS_INITIALIZED, true)
                    .putString(SERVERS_KEY, json.encodeToString(_servers.value))
                    .apply()
                
                Log.i(TAG, "Initialized with ${newDefaults.size} default servers")
            } else {
                // Ensure default servers always exist (can't be deleted)
                val existingIds = loadedServers.map { it.id }
                val missingDefaults = defaultServers.filter { it.id !in existingIds }
                _servers.value = loadedServers + missingDefaults
                
                if (missingDefaults.isNotEmpty()) {
                    prefs.edit().putString(SERVERS_KEY, json.encodeToString(_servers.value)).apply()
                }
            }
            
            Log.i(TAG, "Loaded ${_servers.value.size} MCP servers")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load servers: ${e.message}", e)
            _servers.value = defaultServers
        }
    }

    /**
     * Save servers to SharedPreferences
     */
    private fun saveServers(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val serversJson = json.encodeToString(_servers.value)
            prefs.edit().putString(SERVERS_KEY, serversJson).apply()
            Log.i(TAG, "Saved ${_servers.value.size} MCP servers")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save servers: ${e.message}", e)
        }
    }

    /**
     * Add a new MCP server
     */
    fun addServer(context: Context, server: MCPServerConfig) {
        _servers.value = _servers.value + server
        saveServers(context)
        if (server.enabled) {
            getScope().launch { connectToServer(server) }
        }
    }

    /**
     * Update an existing server
     */
    fun updateServer(context: Context, server: MCPServerConfig) {
        _servers.value = _servers.value.map { 
            if (it.id == server.id) server else it 
        }
        saveServers(context)
        
        getScope().launch {
            if (server.enabled) {
                connectToServer(server)
            } else {
                disconnectFromServer(server.id)
            }
        }
    }

    /**
     * Remove a server
     */
    fun removeServer(context: Context, serverId: String) {
        getScope().launch { disconnectFromServer(serverId) }
        _servers.value = _servers.value.filter { it.id != serverId }
        saveServers(context)
        Log.i(TAG, "Removed server: $serverId")
    }
    
    /**
     * Check if a server is a default (built-in) server
     */
    fun isDefaultServer(serverId: String): Boolean {
        return defaultServers.any { it.id == serverId }
    }
    
    /**
     * Reset to default servers
     */
    fun resetToDefaults(context: Context) {
        getScope().launch {
            _servers.value.forEach { disconnectFromServer(it.id) }
        }
        _servers.value = defaultServers.toList()
        _tools.value = emptyList()
        _serverStatuses.value = emptyMap()
        saveServers(context)
        Log.i(TAG, "Reset to default servers")
    }

    /**
     * Toggle server enabled state (with context)
     */
    fun toggleServer(context: Context, serverId: String) {
        val server = _servers.value.find { it.id == serverId } ?: return
        updateServer(context, server.copy(enabled = !server.enabled))
    }
    
    /**
     * Toggle server enabled state (without context)
     */
    fun toggleServerEnabled(serverId: String) {
        val server = _servers.value.find { it.id == serverId } ?: return
        val newServer = server.copy(enabled = !server.enabled)
        _servers.value = _servers.value.map { if (it.id == serverId) newServer else it }
        
        getScope().launch {
            if (newServer.enabled) {
                connectToServer(newServer)
            } else {
                disconnectFromServer(serverId)
            }
        }
    }
    
    /**
     * Reconnect to a specific server
     */
    fun reconnectServer(serverId: String) {
        val server = _servers.value.find { it.id == serverId } ?: return
        getScope().launch {
            disconnectFromServer(serverId)
            connectToServer(server)
        }
    }

    /**
     * Import servers from JSON string
     */
    fun importServers(context: Context, jsonString: String): Result<Int> {
        return try {
            val trimmed = jsonString.trim()
            val imported: List<MCPServerConfig> = if (trimmed.startsWith("{") && trimmed.contains("mcpServers")) {
                parseMcpServersFormat(trimmed)
            } else {
                json.decodeFromString(trimmed)
            }
            
            _servers.value = _servers.value + imported
            saveServers(context)
            
            getScope().launch {
                imported.filter { it.enabled }.forEach { connectToServer(it) }
            }
            
            Result.success(imported.size)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import servers: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Parse mcpServers format from JavaScript Chat UI
     */
    private fun parseMcpServersFormat(jsonString: String): List<MCPServerConfig> {
        val rootObj = json.parseToJsonElement(jsonString).jsonObject
        val mcpServers = rootObj["mcpServers"]?.jsonObject ?: return emptyList()
        
        return mcpServers.entries.mapNotNull { (name, config) ->
            try {
                val serverObj = config.jsonObject
                val command = serverObj["command"]?.jsonPrimitive?.contentOrNull ?: ""
                val args = serverObj["args"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                val disabled = serverObj["disabled"]?.jsonPrimitive?.booleanOrNull ?: false
                val env = serverObj["env"]?.jsonObject?.entries?.associate { 
                    it.key to (it.value.jsonPrimitive.contentOrNull ?: "") 
                } ?: emptyMap()
                
                val url = when {
                    args.any { it.startsWith("http") } -> args.first { it.startsWith("http") }
                    command == "npx" && args.contains("mcp-remote") -> {
                        args.lastOrNull { it.startsWith("http") } ?: ""
                    }
                    else -> "stdio://$command ${args.joinToString(" ")}"
                }
                
                if (url.isBlank()) return@mapNotNull null
                
                MCPServerConfig(
                    id = "imported-${name.lowercase().replace(" ", "-")}",
                    name = name,
                    url = url,
                    enabled = !disabled,
                    type = if (url.startsWith("stdio://")) MCPTransportType.STDIO else MCPTransportType.SSE,
                    headers = env.filterValues { it.isNotBlank() }
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse server $name: ${e.message}")
                null
            }
        }
    }

    /**
     * Export servers to JSON string
     */
    fun exportServers(): String {
        return json.encodeToString(_servers.value)
    }

    /**
     * Connect to all enabled servers
     */
    fun connectAll() {
        getScope().launch {
            _servers.value.filter { it.enabled }.forEach { server ->
                connectToServer(server)
            }
        }
    }

    /**
     * Disconnect from all servers
     */
    fun disconnectAll() {
        getScope().launch {
            _servers.value.forEach { server ->
                disconnectFromServer(server.id)
            }
        }
    }

    /**
     * Connect to a specific MCP server with improved error handling and thread safety
     */
    private suspend fun connectToServer(server: MCPServerConfig) {
        updateServerStatus(server.id, server.name, MCPConnectionState.CONNECTING)
        
        try {
            // Use mutex to ensure thread-safe client operations
            clientsMutex.withLock {
                // Close existing client if any
                clients[server.id]?.let { existingClient ->
                    try {
                        existingClient.close()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error closing existing client for ${server.name}: ${e.message}")
                    }
                }
                
                // Create and initialize new client
                val client = MCPClient(server)
                clients[server.id] = client
            
            // Initialize with timeout
            val initSuccess = withTimeoutOrNull(30000) {
                client.initialize()
            } ?: false
            
            if (!initSuccess) {
                throw Exception("Initialization timeout (30s)")
            }
                
                // List tools with timeout
                val serverTools = withTimeoutOrNull(30000) {
                    client.listTools()
                } ?: emptyList()
                
                // Check tool limit
                val totalTools = _tools.value.filter { it.serverId != server.id }.size + serverTools.size
                if (totalTools > MAX_TOOLS) {
                    Log.w(TAG, "Tool limit exceeded: $totalTools > $MAX_TOOLS")
                    throw Exception("تجاوز الحد الأقصى للأدوات ($MAX_TOOLS)")
                }
                
                // Update tools list
                _tools.value = _tools.value.filter { it.serverId != server.id } + serverTools
                
                // Use STANDBY state if connected but no tools available
                val finalState = if (serverTools.isEmpty()) {
                    MCPConnectionState.STANDBY
                } else {
                    MCPConnectionState.CONNECTED
                }
                
                val statusMessage = if (serverTools.isEmpty()) "متصل ولكن لا توجد أدوات" else null
                updateServerStatus(server.id, server.name, finalState, statusMessage, toolCount = serverTools.size)
                
                if (serverTools.isEmpty()) {
                    Log.w(TAG, "⚠ Connected to ${server.name} but no tools available")
                } else {
                    Log.i(TAG, "✓ Connected to ${server.name} with ${serverTools.size} tools")
                }
            } // end of mutex lock
            
        } catch (e: Exception) {
            val errorMsg = when {
                e.message?.contains("timeout", ignoreCase = true) == true -> "انتهت مهلة الاتصال"
                e.message?.contains("refused", ignoreCase = true) == true -> "رفض الخادم الاتصال"
                e.message?.contains("host", ignoreCase = true) == true -> "لا يمكن الوصول للخادم"
                else -> e.message ?: "خطأ غير معروف"
            }
            Log.e(TAG, "✗ Failed to connect to ${server.name}: $errorMsg", e)
            updateServerStatus(server.id, server.name, MCPConnectionState.ERROR, errorMsg)
            clients.remove(server.id)
        }
    }

    /**
     * Disconnect from a server (thread-safe)
     */
    private suspend fun disconnectFromServer(serverId: String) {
        clientsMutex.withLock {
            try {
                clients[serverId]?.close()
                clients.remove(serverId)
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting from $serverId: ${e.message}", e)
            }
        }
        _tools.value = _tools.value.filter { it.serverId != serverId }
        _serverStatuses.value = _serverStatuses.value - serverId
    }

    /**
     * Update server status
     */
    private fun updateServerStatus(
        serverId: String, 
        serverName: String,
        state: MCPConnectionState, 
        error: String? = null,
        toolCount: Int = 0
    ) {
        _serverStatuses.value = _serverStatuses.value + (serverId to MCPServerStatus(
            serverId = serverId,
            serverName = serverName,
            state = state,
            error = error,
            toolCount = toolCount
        ))
    }

    /**
     * Call a tool on an MCP server
     */
    suspend fun callTool(toolCall: MCPToolCall): MCPToolResult {
        val client = clients[toolCall.serverId]
            ?: return MCPToolResult(
                content = "Server not connected",
                isError = true,
                toolName = toolCall.toolName
            )
        
        return try {
            client.callTool(toolCall.toolName, toolCall.arguments)
        } catch (e: Exception) {
            MCPToolResult(
                content = "Tool call failed: ${e.message}",
                isError = true,
                toolName = toolCall.toolName
            )
        }
    }

    /**
     * Get all available tools from connected servers
     */
    fun getAllTools(): List<MCPTool> = _tools.value

    /**
     * Get tools for LLM (OpenAI function calling format)
     * Uses sanitized names and caching
     * Returns JsonArray for direct use in API requests
     */
    fun getToolsForLLM(): kotlinx.serialization.json.JsonArray {
        val now = System.currentTimeMillis()
        
        // Check if cache is still valid
        if (now - toolsCacheTimestamp < CACHE_TTL_MS && _toolMapping.value.isNotEmpty()) {
            Log.d(TAG, "Using cached tools (${_toolMapping.value.size} tools)")
            return _tools.value.toLLMToolsWithMapping().first
        }
        
        // Update cache
        val (tools, mapping) = _tools.value.toLLMToolsWithMapping()
        _toolMapping.value = mapping
        toolsCacheTimestamp = now
        
        Log.i(TAG, "Updated tool cache: ${tools.size} tools, ${mapping.size} mappings")
        return tools
    }
    
    /**
     * Get tool mapping for resolving sanitized names back to original
     */
    fun getToolMapping(): Map<String, MCPToolMapping> = _toolMapping.value
    
    /**
     * Resolve sanitized tool name to original tool info
     */
    fun resolveToolName(sanitizedName: String): MCPToolMapping? {
        return _toolMapping.value[sanitizedName]
    }
    
    /**
     * Call tool by sanitized name (resolves to original)
     */
    suspend fun callToolBySanitizedName(sanitizedName: String, arguments: Map<String, kotlinx.serialization.json.JsonElement>): MCPToolResult {
        val mapping = resolveToolName(sanitizedName)
            ?: return MCPToolResult(
                content = "Tool not found: $sanitizedName",
                isError = true,
                toolName = sanitizedName
            )
        
        return callTool(MCPToolCall(
            toolName = mapping.originalName,
            arguments = arguments,
            serverId = mapping.serverId
        ))
    }
    
    /**
     * Clear tool cache (force refresh)
     */
    fun clearToolCache() {
        toolsCacheTimestamp = 0
        _toolMapping.value = emptyMap()
        Log.d(TAG, "Tool cache cleared")
    }

    /**
     * Find tool by name (checks both sanitized and original names)
     */
    fun findTool(toolName: String): MCPTool? {
        // First try direct match
        val direct = _tools.value.find { it.name == toolName }
        if (direct != null) return direct
        
        // Try via mapping (sanitized name)
        val mapping = _toolMapping.value[toolName]
        if (mapping != null) {
            return _tools.value.find { it.name == mapping.originalName && it.serverId == mapping.serverId }
        }
        
        return null
    }

    /**
     * Check if any servers are connected
     */
    fun hasConnectedServers(): Boolean {
        return _serverStatuses.value.any { it.value.state == MCPConnectionState.CONNECTED }
    }

    /**
     * Get connected server count
     */
    fun getConnectedServerCount(): Int {
        return _serverStatuses.value.count { it.value.state == MCPConnectionState.CONNECTED }
    }

    /**
     * Get total tool count
     */
    fun getTotalToolCount(): Int = _tools.value.size
}
