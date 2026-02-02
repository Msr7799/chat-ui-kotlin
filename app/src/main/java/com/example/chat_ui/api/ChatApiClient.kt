package com.example.chat_ui.api

import com.example.chat_ui.config.ConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Chat API Client - OpenAI-compatible API client
 * Uses configuration from ConfigManager
 */
class ChatApiClient {
    
    data class ChatMessage(
        val role: String, // "user", "assistant", "system"
        val content: String
    )
    
    data class ChatResponse(
        val id: String,
        val content: String,
        val model: String,
        val finishReason: String?,
        val usage: Usage?
    )
    
    data class Usage(
        val promptTokens: Int,
        val completionTokens: Int,
        val totalTokens: Int
    )
    
    data class Model(
        val id: String,
        val name: String,
        val ownedBy: String
    )
    
    sealed class ApiResult<out T> {
        data class Success<T>(val data: T) : ApiResult<T>()
        data class Error(val message: String, val code: Int? = null) : ApiResult<Nothing>()
    }
    
    private val baseUrl: String
        get() = ConfigManager.openAiBaseUrl
    
    private val apiKey: String
        get() = ConfigManager.openAiApiKey

    private fun testOpenAiCompatible(baseUrl: String, apiKey: String): ApiResult<Unit> {
        return try {
            val url = URL("$baseUrl/models")
            val connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $apiKey")
                connectTimeout = 10000
                readTimeout = 30000
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                ApiResult.Success(Unit)
            } else {
                val errorStream = connection.errorStream ?: connection.inputStream
                val errorResponse = BufferedReader(InputStreamReader(errorStream)).use { reader ->
                    reader.readText()
                }

                val errorMessage = try {
                    JSONObject(errorResponse).optJSONObject("error")?.optString("message")
                        ?: errorResponse
                } catch (_: Exception) {
                    errorResponse
                }

                ApiResult.Error(errorMessage, responseCode)
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Unknown error occurred")
        }
    }
    
    /**
     * Send a chat completion request
     */
    suspend fun chatCompletion(
        messages: List<ChatMessage>,
        model: String = ConfigManager.defaultModel,
        temperature: Float = 0.7f,
        maxTokens: Int? = null,
        stream: Boolean = false
    ): ApiResult<ChatResponse> = withContext(Dispatchers.IO) {
        try {
            val providerConfig = ConfigManager.getProviderConfig()
            
            // For Google AI Studio, use different endpoint format
            if (providerConfig.provider == com.example.chat_ui.data.ApiProvider.GOOGLE_AI_STUDIO) {
                return@withContext chatCompletionGoogleAIStudio(messages, model, temperature, maxTokens)
            }
            
            val url = URL("$baseUrl/chat/completions")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
                doOutput = true
                connectTimeout = 30000
                readTimeout = 60000
            }
            
            // Build request body
            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    messages.forEach { msg ->
                        put(JSONObject().apply {
                            put("role", msg.role)
                            put("content", msg.content)
                        })
                    }
                })
                put("temperature", temperature)
                put("stream", stream)
                maxTokens?.let { put("max_tokens", it) }
            }
            
            // Send request
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }
            
            // Read response
            val responseCode = connection.responseCode
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    reader.readText()
                }
                
                val jsonResponse = JSONObject(response)
                val choices = jsonResponse.getJSONArray("choices")
                
                if (choices.length() > 0) {
                    val choice = choices.getJSONObject(0)
                    val message = choice.getJSONObject("message")
                    
                    val usage = if (jsonResponse.has("usage")) {
                        val usageJson = jsonResponse.getJSONObject("usage")
                        Usage(
                            promptTokens = usageJson.optInt("prompt_tokens", 0),
                            completionTokens = usageJson.optInt("completion_tokens", 0),
                            totalTokens = usageJson.optInt("total_tokens", 0)
                        )
                    } else null
                    
                    ApiResult.Success(
                        ChatResponse(
                            id = jsonResponse.optString("id", ""),
                            content = message.getString("content"),
                            model = jsonResponse.optString("model", model),
                            finishReason = choice.optString("finish_reason"),
                            usage = usage
                        )
                    )
                } else {
                    ApiResult.Error("No response from API")
                }
            } else {
                val errorStream = connection.errorStream ?: connection.inputStream
                val errorResponse = BufferedReader(InputStreamReader(errorStream)).use { reader ->
                    reader.readText()
                }
                
                val errorMessage = try {
                    JSONObject(errorResponse).optJSONObject("error")?.optString("message")
                        ?: errorResponse
                } catch (e: Exception) {
                    errorResponse
                }
                
                ApiResult.Error(errorMessage, responseCode)
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Unknown error occurred")
        }
    }
    
    /**
     * Handle Google AI Studio API calls (different format than OpenAI)
     */
    private suspend fun chatCompletionGoogleAIStudio(
        messages: List<ChatMessage>,
        model: String,
        temperature: Float,
        maxTokens: Int?
    ): ApiResult<ChatResponse> = withContext(Dispatchers.IO) {
        try {
            val apiKey = ConfigManager.openAiApiKey
            val geminiConfig = ConfigManager.getGoogleGeminiConfig()
            val modelName = if (model.startsWith("google/")) model.substringAfter("google/") else model

            val providerConfig = ConfigManager.getProviderConfig()
            val isBetaOrAlphaApi =
                providerConfig.baseUrl.contains("/v1beta") || providerConfig.baseUrl.contains("/v1alpha")

            val supportsThinkingAndMediaConfig = modelName.startsWith("gemini-3") && isBetaOrAlphaApi
            
            // Google AI Studio endpoint: /v1beta/models/{model}:generateContent
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 30000
                readTimeout = 60000
            }
            
            // Convert messages to Gemini format (role + parts)
            val contents = JSONArray()
            messages.forEach { msg ->
                contents.put(JSONObject().apply {
                    put("role", if (msg.role == "assistant") "model" else msg.role)
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", msg.content)
                        })
                    })
                })
            }
            
            // Build request body in Gemini format
            val requestBody = JSONObject().apply {
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    val effectiveTemperature = if (temperature != 0.7f) temperature else geminiConfig.temperature
                    put("temperature", effectiveTemperature)
                    put("topP", geminiConfig.topP)
                    put("topK", geminiConfig.topK)
                    put("maxOutputTokens", maxTokens ?: geminiConfig.maxOutputTokens)
                    put("responseMimeType", geminiConfig.responseMimeType.value)

                    if (supportsThinkingAndMediaConfig && geminiConfig.thinkingEnabled) {
                        put("thinkingConfig", JSONObject().apply {
                            put("thinkingLevel", geminiConfig.thinkingLevel.value)
                        })
                    }

                    if (supportsThinkingAndMediaConfig) {
                        put("mediaResolution", geminiConfig.mediaResolution.value)
                    }
                })

                put("safetySettings", JSONArray().apply {
                    put(JSONObject().apply {
                        put("category", "HARM_CATEGORY_HARASSMENT")
                        put("threshold", geminiConfig.safetyHarassment.value)
                    })
                    put(JSONObject().apply {
                        put("category", "HARM_CATEGORY_HATE_SPEECH")
                        put("threshold", geminiConfig.safetyHateSpeech.value)
                    })
                    put(JSONObject().apply {
                        put("category", "HARM_CATEGORY_SEXUALLY_EXPLICIT")
                        put("threshold", geminiConfig.safetySexuallyExplicit.value)
                    })
                    put(JSONObject().apply {
                        put("category", "HARM_CATEGORY_DANGEROUS_CONTENT")
                        put("threshold", geminiConfig.safetyDangerousContent.value)
                    })
                })

                val tools = JSONArray().apply {
                    if (geminiConfig.urlContextEnabled) {
                        put(JSONObject().apply {
                            put("urlContext", JSONObject())
                        })
                    }
                    if (geminiConfig.googleSearchEnabled) {
                        put(JSONObject().apply {
                            put("googleSearch", JSONObject())
                        })
                    }
                }

                if (tools.length() > 0) {
                    put("tools", tools)
                }
            }
            
            // Send request
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }
            
            // Read response
            val responseCode = connection.responseCode
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    reader.readText()
                }
                
                val jsonResponse = JSONObject(response)
                val candidates = jsonResponse.optJSONArray("candidates")
                
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val content = candidate.getJSONObject("content")
                    val parts = content.getJSONArray("parts")
                    val text = parts.getJSONObject(0).getString("text")
                    
                    val usage = if (jsonResponse.has("usageMetadata")) {
                        val usageJson = jsonResponse.getJSONObject("usageMetadata")
                        Usage(
                            promptTokens = usageJson.optInt("promptTokenCount", 0),
                            completionTokens = usageJson.optInt("candidatesTokenCount", 0),
                            totalTokens = usageJson.optInt("totalTokenCount", 0)
                        )
                    } else null
                    
                    ApiResult.Success(
                        ChatResponse(
                            id = jsonResponse.optString("modelVersion", ""),
                            content = text,
                            model = modelName,
                            finishReason = candidate.optString("finishReason", "stop"),
                            usage = usage
                        )
                    )
                } else {
                    ApiResult.Error("No response from Gemini API")
                }
            } else {
                val errorStream = connection.errorStream ?: connection.inputStream
                val errorResponse = BufferedReader(InputStreamReader(errorStream)).use { reader ->
                    reader.readText()
                }
                
                val errorMessage = try {
                    JSONObject(errorResponse).optJSONObject("error")?.optString("message")
                        ?: errorResponse
                } catch (e: Exception) {
                    errorResponse
                }
                
                ApiResult.Error("Google AI Studio Error: $errorMessage", responseCode)
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Unknown error occurred")
        }
    }
    
    /**
     * Get available models
     */
    suspend fun getModels(): ApiResult<List<Model>> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/models")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $apiKey")
                connectTimeout = 10000
                readTimeout = 30000
            }
            
            val responseCode = connection.responseCode
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    reader.readText()
                }
                
                val jsonResponse = JSONObject(response)
                val dataArray = jsonResponse.getJSONArray("data")
                
                val models = mutableListOf<Model>()
                for (i in 0 until dataArray.length()) {
                    val modelJson = dataArray.getJSONObject(i)
                    models.add(
                        Model(
                            id = modelJson.getString("id"),
                            name = modelJson.optString("name", modelJson.getString("id")),
                            ownedBy = modelJson.optString("owned_by", "unknown")
                        )
                    )
                }
                
                ApiResult.Success(models)
            } else {
                ApiResult.Error("Failed to fetch models", responseCode)
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Unknown error occurred")
        }
    }
    
    /**
     * Validate API key by making a simple models request
     */
    suspend fun validateApiKey(): Boolean {
        return when (testConnection()) {
            is ApiResult.Success -> true
            is ApiResult.Error -> false
        }
    }

    suspend fun testConnection(): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val providerConfig = ConfigManager.getProviderConfig()
            return@withContext testConnection(providerConfig)
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Unknown error occurred")
        }
    }

    suspend fun testConnection(config: com.example.chat_ui.data.ProviderConfig): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            if (config.apiKey.isBlank()) {
                return@withContext ApiResult.Error("Missing API key")
            }

            return@withContext when (config.provider) {
                com.example.chat_ui.data.ApiProvider.GOOGLE_AI_STUDIO -> {
                    testGoogleAiStudio(config.apiKey)
                }
                com.example.chat_ui.data.ApiProvider.HUGGINGFACE -> {
                    testOpenAiCompatible(config.baseUrl, config.apiKey)
                }
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Unknown error occurred")
        }
    }

    private fun testGoogleAiStudio(apiKey: String): ApiResult<Unit> {
        return try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 30000
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                ApiResult.Success(Unit)
            } else {
                val errorStream = connection.errorStream ?: connection.inputStream
                val errorResponse = BufferedReader(InputStreamReader(errorStream)).use { reader ->
                    reader.readText()
                }

                val errorMessage = try {
                    JSONObject(errorResponse).optJSONObject("error")?.optString("message")
                        ?: errorResponse
                } catch (_: Exception) {
                    errorResponse
                }

                ApiResult.Error("Google AI Studio Error: $errorMessage", responseCode)
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Unknown error occurred")
        }
    }
    
    companion object {
        @Volatile
        private var instance: ChatApiClient? = null
        
        fun getInstance(): ChatApiClient {
            return instance ?: synchronized(this) {
                instance ?: ChatApiClient().also { instance = it }
            }
        }
    }
}
