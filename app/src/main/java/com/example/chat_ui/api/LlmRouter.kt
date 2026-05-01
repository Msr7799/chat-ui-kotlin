package com.example.chat_ui.api

import android.content.Context
import android.util.Log
import com.example.chat_ui.config.ConfigManager
import com.example.chat_ui.data.ApiProvider
import com.example.chat_ui.utils.FirebaseAuthHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * LLM Router for smart model selection
 * Similar to src/lib/server/router/arch.ts in Svelte
 * 
 * This router analyzes the conversation and selects the best model
 * for the user's current task using Arch router API.
 */
object LlmRouter {
    private const val TAG = "LlmRouter"
    
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    
    @Serializable
    data class Route(
        val name: String,
        val description: String,
        val primary_model: String,
        val fallback_models: List<String> = emptyList()
    )
    
    @Serializable
    data class RouteSelection(
        val route: String
    )
    
    private var routes: List<Route> = emptyList()
    
    /**
     * Load routes from assets
     */
    fun loadRoutes(context: Context) {
        try {
            val routesJson = context.assets.open("routes.chat.json").bufferedReader().readText()
            routes = json.decodeFromString<List<Route>>(routesJson)
            Log.i(TAG, "Loaded ${routes.size} routes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load routes, using defaults", e)
            routes = getDefaultRoutes()
        }
    }
    
    /**
     * Select the best model for the given messages
     * HYBRID APPROACH: Uses fast static policy for simple cases, Arch API for complex ones
     * @param hasImages If true, bypasses routing and uses multimodal model
     * @param hasTools If true, bypasses routing and uses a model that supports tools/MCP
     */
    suspend fun selectModel(
        messages: List<ChatApiClient.ChatMessage>, 
        hasImages: Boolean = false,
        hasTools: Boolean = false,
        hasDocuments: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val fallbackModel = ConfigManager.get(ConfigManager.Keys.LLM_ROUTER_FALLBACK_MODEL, "Qwen/Qwen3-235B-A22B-Instruct-2507")
        
        // Priority 1: Special cases bypass all routing
        if (hasTools) {
            val toolsRoute = routes.find { it.name == "tools" }
            if (toolsRoute != null) {
                Log.i(TAG, "[HYBRID] Tools/MCP detected - using tools route: ${toolsRoute.primary_model}")
                return@withContext toolsRoute.primary_model
            }
            val defaultToolsModel = "Qwen/Qwen3-235B-A22B-Instruct-2507"
            Log.i(TAG, "[HYBRID] Tools/MCP detected - using default tools model: $defaultToolsModel")
            return@withContext defaultToolsModel
        }
        
        if (hasImages) {
            val multimodalRoute = routes.find { it.name == "multimodal" }
            if (multimodalRoute != null) {
                Log.i(TAG, "[HYBRID] Images detected - using multimodal route: ${multimodalRoute.primary_model}")
                return@withContext multimodalRoute.primary_model
            }
            val defaultVisionModel = "Qwen/Qwen2.5-VL-72B-Instruct"
            Log.i(TAG, "[HYBRID] Images detected - using default vision model: $defaultVisionModel")
            return@withContext defaultVisionModel
        }

        if (hasDocuments) {
            val documentRoute = routes.find { it.name == "document_analysis" }
            if (documentRoute != null) {
                Log.i(TAG, "[HYBRID] Documents detected - using document route: ${documentRoute.primary_model}")
                return@withContext documentRoute.primary_model
            }
            Log.i(TAG, "[HYBRID] Documents detected - using fallback text model: $fallbackModel")
            return@withContext fallbackModel
        }
        
        if (routes.isEmpty()) {
            Log.w(TAG, "[HYBRID] No routes loaded, using fallback model")
            return@withContext fallbackModel
        }
        
        // Priority 2: Fast path for simple/obvious cases
        val lastUserMessage = messages.lastOrNull { it.role == "user" }?.content ?: ""
        val simpleRouteMatch = isSimpleCase(lastUserMessage)
        
        if (simpleRouteMatch != null) {
            val selectedModel = staticPolicySelect(simpleRouteMatch)
            if (selectedModel != null) {
                Log.i(TAG, "[HYBRID-FAST] Simple case detected: $simpleRouteMatch -> $selectedModel")
                return@withContext selectedModel
            }
        }
        
        // Priority 3: Intelligent path for complex cases using Arch API
        val archBaseUrl = ConfigManager.get(ConfigManager.Keys.LLM_ROUTER_ARCH_BASE_URL, "").trimEnd('/')
        val archModel = ConfigManager.get(ConfigManager.Keys.LLM_ROUTER_ARCH_MODEL, "router/omni")
        
        if (archBaseUrl.isBlank()) {
            Log.w(TAG, "[HYBRID] Arch router not configured, using fallback model")
            return@withContext fallbackModel
        }
        
        try {
            val routerPrompt = buildRouterPrompt(messages, routes)
            val routeSelection = callRouterApi(archBaseUrl, archModel, routerPrompt)
            val selectedRoute = routes.find { it.name == routeSelection }
            
            if (selectedRoute != null) {
                Log.i(TAG, "[HYBRID-SMART] Arch selected route: ${selectedRoute.name} -> ${selectedRoute.primary_model}")
                return@withContext selectedRoute.primary_model
            }
            
            val otherRoute = ConfigManager.get(ConfigManager.Keys.LLM_ROUTER_OTHER_ROUTE, "casual_conversation")
            val defaultRoute = routes.find { it.name == otherRoute }
            
            if (defaultRoute != null) {
                Log.i(TAG, "[HYBRID-SMART] Using default route: ${defaultRoute.name} -> ${defaultRoute.primary_model}")
                return@withContext defaultRoute.primary_model
            }
            
            Log.w(TAG, "[HYBRID] No route found, using fallback")
            fallbackModel
        } catch (e: Exception) {
            Log.e(TAG, "[HYBRID] Arch router failed, using fallback", e)
            fallbackModel
        }
    }
    
    private fun buildRouterPrompt(messages: List<ChatApiClient.ChatMessage>, routes: List<Route>): String {
        val routesDescription = routes.joinToString("\n") { route ->
            "${route.name}: ${route.description}"
        }
        
        val maxAssistantLength = 500
        val maxPrevUserLength = 400
        
        // Trim messages for router
        val lastUserIndex = messages.indexOfLast { it.role == "user" }
        val trimmedMessages = messages.mapIndexed { index, msg ->
            val content = when {
                msg.role == "assistant" && msg.content.length > maxAssistantLength -> {
                    trimMiddle(msg.content, maxAssistantLength)
                }
                msg.role == "user" && index != lastUserIndex && msg.content.length > maxPrevUserLength -> {
                    trimMiddle(msg.content, maxPrevUserLength)
                }
                else -> msg.content
            }
            "${msg.role}: $content"
        }
        
        val conversation = trimmedMessages.takeLast(16).joinToString("\n")
        
        return """
You are a helpful assistant designed to find the best suited route.
You are provided with route description within <routes></routes> XML tags:

<routes>
$routesDescription
</routes>

<conversation>
$conversation
</conversation>

Your task is to decide which route is best suit with user intent on the conversation in <conversation></conversation> XML tags.

Follow those instructions:
1. Use prior turns to choose the best route for the current message if needed.
2. If no route match the full conversation respond with other route {"route": "other"}.
3. Analyze the route descriptions and find the best match route for user latest intent.
4. Respond only with the route name that best matches the user's request, using the exact name in the <routes> block.
Based on your analysis, provide your response in the following JSON format if you decide to match any route:
{"route": "route_name"}
        """.trim()
    }
    
    private suspend fun callRouterApi(baseUrl: String, model: String, prompt: String): String {
        val providerConfig = ConfigManager.getProviderConfig()
        val isBackendHuggingFace = providerConfig.provider == ApiProvider.HUGGINGFACE
        val firebaseToken = if (isBackendHuggingFace) {
            FirebaseAuthHelper.getFirebaseIdToken(forceRefresh = false)
        } else {
            null
        }
        val apiKey = ConfigManager.openAiApiKey
        val url = URL(if (isBackendHuggingFace) "${baseUrl.trimEnd('/')}/chat" else "$baseUrl/chat/completions")
        val connection = url.openConnection() as HttpURLConnection
        
        connection.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            when {
                firebaseToken != null -> setRequestProperty("Authorization", "Bearer $firebaseToken")
                apiKey.isNotBlank() -> setRequestProperty("Authorization", "Bearer $apiKey")
            }
            doOutput = true
            connectTimeout = 10000
            readTimeout = 10000
        }
        
        val requestBody = """
{
    "model": "$model",
    "messages": [{"role": "user", "content": ${json.encodeToString(kotlinx.serialization.serializer(), prompt)}}],
    "max_tokens": 50,
    "temperature": 0
}
        """.trim()
        
        connection.outputStream.bufferedWriter().use { it.write(requestBody) }
        
        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw Exception("Router API error: HTTP $responseCode")
        }
        
        val responseBody = connection.inputStream.bufferedReader().readText()
        
        // Extract route from response
        val routeRegex = """"route"\s*:\s*"([^"]+)"""".toRegex()
        val match = routeRegex.find(responseBody)
        
        return match?.groupValues?.get(1) ?: "other"
    }
    
    /**
     * Detect simple/obvious cases for fast static routing
     * Returns route name if matched, null otherwise
     */
    private fun isSimpleCase(lastMessage: String): String? {
        val lowerMessage = lastMessage.lowercase().trim()
        
        // Too short to classify confidently
        if (lowerMessage.length < 10) return null
        
        // Translation indicators
        if (lowerMessage.matches(Regex(".*(translate|ترجم|翻译).*")) &&
            (lowerMessage.contains("to ") || lowerMessage.contains("into ") || 
             lowerMessage.contains("إلى") || lowerMessage.contains("من"))) {
            return "translation"
        }
        
        // Code generation indicators
        if (lowerMessage.matches(Regex(".*(write code|create function|implement|كتابة كود|برمج).*")) ||
            lowerMessage.matches(Regex(".*(function |class |def |const |var |let ).*"))) {
            return "code_generation"
        }
        
        // Summarization indicators
        if (lowerMessage.matches(Regex(".*(summarize|summary|tldr|خلاص|ملخص).*"))) {
            return "summarization"
        }
        
        // Email writing indicators
        if (lowerMessage.matches(Regex(".*(write email|draft email|email to|بريد|رسالة بريد).*"))) {
            return "email_writing"
        }
        
        // Travel planning indicators
        if (lowerMessage.matches(Regex(".*(travel plan|itinerary|trip to|خطة سفر|رحلة إلى).*"))) {
            return "travel_planning"
        }
        
        // Creative writing indicators
        if (lowerMessage.matches(Regex(".*(write story|write poem|fiction|قصة|قصيدة|شعر).*"))) {
            return "creative_writing"
        }
        
        // Math/formal proof indicators
        if (lowerMessage.matches(Regex(".*(prove theorem|lean 4|formal proof|برهان رياضي).*"))) {
            return "formal_proof"
        }
        
        // Spelling/grammar check indicators
        if (lowerMessage.matches(Regex(".*(fix spelling|check grammar|proofread|تصحيح إملائي).*"))) {
            return "spell_checker"
        }
        
        // No simple match found - use intelligent routing
        return null
    }
    
    /**
     * Fast static policy selection from routes
     * Returns primary model for the given route name
     */
    private fun staticPolicySelect(routeName: String): String? {
        val route = routes.find { it.name == routeName }
        return route?.primary_model
    }
    
    private fun trimMiddle(content: String, maxLength: Int): String {
        if (content.length <= maxLength) return content
        
        val indicator = "…"
        val availableLength = maxLength - indicator.length
        
        if (availableLength <= 0) return content.take(maxLength)
        
        val startLength = (availableLength * 0.6).toInt()
        val endLength = availableLength - startLength
        
        if (endLength <= 0) return content.take(availableLength) + indicator
        
        val start = content.take(startLength)
        val end = content.takeLast(endLength)
        
        return start + indicator + end
    }
    
    private fun getDefaultRoutes(): List<Route> {
        return listOf(
            Route("code_generation", "Generate new code, tests, and scaffolds from specs.", "Qwen/Qwen3-Coder-480B-A35B-Instruct"),
            Route("qa_explanations", "Provide concise answers and plain-language explanations.", "Qwen/Qwen3-235B-A22B-Instruct-2507"),
            Route("translation", "Translate between languages with register and terminology control.", "CohereLabs/command-a-translate-08-2025"),
            Route("creative_writing", "Write fiction, poems, jokes, or scripts with style control.", "moonshotai/Kimi-K2-Instruct-0905"),
            Route("casual_conversation", "Engage in friendly and open-ended casual chat.", "Qwen/Qwen3-235B-A22B-Instruct-2507"),
            Route("summarization", "Condense documents into an abstract, key points, and action items.", "Qwen/Qwen3-235B-A22B-Instruct-2507")
        )
    }
}
