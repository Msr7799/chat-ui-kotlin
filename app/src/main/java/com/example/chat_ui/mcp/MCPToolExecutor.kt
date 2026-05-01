package com.example.chat_ui.mcp

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/**
 * MCP Tool Executor - Handles tool execution from LLM responses
 * Parses function calls from LLM and executes them via MCP servers
 */
object MCPToolExecutor {
    private const val TAG = "MCPToolExecutor"
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
    }

    /**
     * Check if a message contains a tool call
     */
    fun containsToolCall(content: String): Boolean {
        return content.contains("<tool_call>") || 
               content.contains("```tool") ||
               content.contains("\"function_call\"") ||
               content.contains("\"tool_calls\"")
    }

    /**
     * Parse tool calls from LLM response
     * Supports multiple formats:
     * - OpenAI function calling format
     * - XML-style tool calls
     * - Markdown code blocks
     */
    fun parseToolCalls(content: String): List<MCPToolCall> {
        val toolCalls = mutableListOf<MCPToolCall>()
        
        // Try XML format: <tool_call>{"name": "...", "arguments": {...}}</tool_call>
        val xmlPattern = Regex("""<tool_call>\s*(\{.*?\})\s*</tool_call>""", RegexOption.DOT_MATCHES_ALL)
        xmlPattern.findAll(content).forEach { match ->
            try {
                val jsonStr = match.groupValues[1]
                val toolCall = parseToolCallJson(jsonStr)
                if (toolCall != null) toolCalls.add(toolCall)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse XML tool call: ${e.message}")
            }
        }
        
        // Try markdown code block format: ```tool\n{...}\n```
        val mdPattern = Regex("""```(?:tool|json)?\s*\n(\{.*?\})\s*\n```""", RegexOption.DOT_MATCHES_ALL)
        mdPattern.findAll(content).forEach { match ->
            try {
                val jsonStr = match.groupValues[1]
                val toolCall = parseToolCallJson(jsonStr)
                if (toolCall != null) toolCalls.add(toolCall)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse markdown tool call: ${e.message}")
            }
        }
        
        // Try OpenAI function_call format in JSON
        if (content.contains("\"function_call\"") || content.contains("\"tool_calls\"")) {
            try {
                val jsonElement = json.parseToJsonElement(content)
                parseOpenAIToolCalls(jsonElement, toolCalls)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse OpenAI format: ${e.message}")
            }
        }
        
        return toolCalls
    }

    /**
     * Parse a single tool call from JSON string
     */
    private fun parseToolCallJson(jsonStr: String): MCPToolCall? {
        return try {
            val jsonElement = json.parseToJsonElement(jsonStr).jsonObject
            
            val name = jsonElement["name"]?.jsonPrimitive?.content 
                ?: jsonElement["function"]?.jsonPrimitive?.content
                ?: return null
            
            val arguments = jsonElement["arguments"]?.jsonObject 
                ?: jsonElement["parameters"]?.jsonObject
                ?: buildJsonObject {}
            
            // Find the server that has this tool
            val tool = MCPManager.findTool(name)
            val serverId = tool?.serverId ?: ""
            
            MCPToolCall(
                toolName = name,
                arguments = arguments.toMap().mapValues { it.value },
                serverId = serverId
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse tool call JSON: ${e.message}")
            null
        }
    }

    /**
     * Parse OpenAI-style tool calls
     */
    private fun parseOpenAIToolCalls(jsonElement: JsonElement, toolCalls: MutableList<MCPToolCall>) {
        when {
            jsonElement is JsonObject -> {
                // Check for function_call
                jsonElement["function_call"]?.jsonObject?.let { funcCall ->
                    val name = funcCall["name"]?.jsonPrimitive?.content ?: return
                    val argsStr = funcCall["arguments"]?.jsonPrimitive?.content ?: "{}"
                    val args = json.parseToJsonElement(argsStr).jsonObject
                    
                    val tool = MCPManager.findTool(name)
                    toolCalls.add(MCPToolCall(
                        toolName = name,
                        arguments = args.toMap().mapValues { it.value },
                        serverId = tool?.serverId ?: ""
                    ))
                }
                
                // Check for tool_calls array
                jsonElement["tool_calls"]?.jsonArray?.forEach { toolCallJson ->
                    val funcObj = toolCallJson.jsonObject["function"]?.jsonObject ?: return@forEach
                    val name = funcObj["name"]?.jsonPrimitive?.content ?: return@forEach
                    val argsStr = funcObj["arguments"]?.jsonPrimitive?.content ?: "{}"
                    val args = json.parseToJsonElement(argsStr).jsonObject
                    
                    val tool = MCPManager.findTool(name)
                    toolCalls.add(MCPToolCall(
                        toolName = name,
                        arguments = args.toMap().mapValues { it.value },
                        serverId = tool?.serverId ?: ""
                    ))
                }
            }
        }
    }

    /**
     * Execute a tool call and return the result
     */
    suspend fun executeTool(toolCall: MCPToolCall): MCPToolResult {
        return executeToolDetailed(toolCall).result
    }

    /**
     * Execute a tool call and return a detailed trace for UI/debugging.
     */
    suspend fun executeToolDetailed(toolCall: MCPToolCall): MCPToolExecutionTrace {
        return withContext(Dispatchers.IO) {
            Log.i(TAG, "Executing tool: ${toolCall.toolName}")
            
            // Check if tool exists
            val tool = MCPManager.findTool(toolCall.toolName)
            if (tool == null) {
                return@withContext MCPToolExecutionTrace(
                    toolName = toolCall.toolName,
                    serverId = toolCall.serverId,
                    serverName = MCPManager.getServerName(toolCall.serverId),
                    input = toolCall.arguments,
                    durationMs = 0,
                    result = MCPToolResult(
                        content = "Tool '${toolCall.toolName}' not found. Available tools: ${MCPManager.getAllTools().map { it.name }}",
                        isError = true,
                        toolName = toolCall.toolName
                    )
                )
            }
            
            // Execute via MCPManager
            val startTime = System.currentTimeMillis()
            val result = MCPManager.callTool(toolCall.copy(serverId = tool.serverId))
            val durationMs = (System.currentTimeMillis() - startTime).coerceAtLeast(0)
            Log.i(TAG, "Tool result: ${result.content.take(100)}...")
            
            MCPToolExecutionTrace(
                toolName = toolCall.toolName,
                serverId = tool.serverId,
                serverName = MCPManager.getServerName(tool.serverId),
                input = toolCall.arguments,
                result = result,
                durationMs = durationMs
            )
        }
    }

    /**
     * Execute all tool calls in a message and return formatted results
     */
    suspend fun executeAllToolCalls(content: String): List<MCPToolResult> {
        return executeAllToolCallsDetailed(content).map { it.result }
    }

    suspend fun executeAllToolCallsDetailed(content: String): List<MCPToolExecutionTrace> {
        val toolCalls = parseToolCalls(content)
        if (toolCalls.isEmpty()) return emptyList()
        
        return toolCalls.map { executeToolDetailed(it) }
    }

    /**
     * Format tool results for display or sending back to LLM
     */
    fun formatToolResults(results: List<MCPToolExecutionTrace>): String {
        if (results.isEmpty()) return ""
        
        return buildString {
            results.forEach { result ->
                appendLine("**🔧 ${result.serverName} / ${result.toolName}:**")
                if (result.result.isError) {
                    appendLine("❌ Error: ${result.result.content}")
                } else {
                    appendLine(result.result.content)
                }
                appendLine()
            }
        }.trim()
    }

    fun buildDebugFoldContent(results: List<MCPToolExecutionTrace>): String {
        if (results.isEmpty()) return "No MCP tool execution details."

        return buildString {
            results.forEachIndexed { index, trace ->
                appendLine("MCP Server: ${trace.serverName}")
                appendLine("Tool: ${trace.toolName}")
                appendLine("Server ID: ${trace.serverId}")
                appendLine("Duration: ${trace.durationMs} ms")
                appendLine("Input:")
                appendLine(prettyPrintJson(trace.input))
                if (index != results.lastIndex) {
                    appendLine()
                    appendLine("---")
                    appendLine()
                }
            }
        }.trim()
    }

    fun buildOutputFoldContent(results: List<MCPToolExecutionTrace>): String {
        if (results.isEmpty()) return ""

        return buildString {
            results.forEachIndexed { index, trace ->
                appendLine("MCP Server: ${trace.serverName}")
                appendLine("Tool: ${trace.toolName}")
                appendLine("Output:")
                appendLine(trace.result.content)
                if (index != results.lastIndex) {
                    appendLine()
                    appendLine("---")
                    appendLine()
                }
            }
        }.trim()
    }

    /**
     * Build system prompt with available tools
     */
    fun buildToolsSystemPrompt(): String {
        val tools = MCPManager.getAllTools()
        if (tools.isEmpty()) return ""
        
        return buildString {
            appendLine("You have access to the following tools:")
            appendLine()
            tools.forEach { tool ->
                appendLine("- **${tool.name}**: ${tool.description ?: "No description"}")
                tool.inputSchema?.let { schema ->
                    appendLine("  Parameters: $schema")
                }
            }
            appendLine()
            appendLine("To use a tool, respond with:")
            appendLine("<tool_call>{\"name\": \"tool_name\", \"arguments\": {...}}</tool_call>")
        }
    }

    /**
     * Check if MCP tools are available
     */
    fun hasAvailableTools(): Boolean = MCPManager.getTotalToolCount() > 0

    private fun prettyPrintJson(arguments: Map<String, JsonElement>): String {
        if (arguments.isEmpty()) return "{}"
        return Json { prettyPrint = true }.encodeToString(JsonObject(arguments))
    }
}
