package com.example.chat_ui.api

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.chat_ui.config.ConfigManager
import com.example.chat_ui.data.ApiProvider
import com.example.chat_ui.utils.FirebaseAuthHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Chat API Client - OpenAI-compatible API client
 * Uses configuration from ConfigManager
 */
class ChatApiClient {
    private val TAG = "ChatApiClient"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    
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
            
            val isBackendHuggingFace = providerConfig.provider == ApiProvider.HUGGINGFACE
            val firebaseToken = if (isBackendHuggingFace) {
                FirebaseAuthHelper.getFirebaseIdToken(forceRefresh = false)
                    ?: return@withContext ApiResult.Error("Please sign in before sending chat requests")
            } else {
                null
            }

            val url = URL(if (isBackendHuggingFace) "$baseUrl/chat" else "$baseUrl/chat/completions")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${firebaseToken ?: apiKey}")
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
     * Send one PDF to the backend as multipart/form-data stream.
     * الأمان: لا نقرأ PDF كاملًا في الذاكرة ولا نحوله Base64 داخل Android.
     */
    suspend fun chatCompletionWithPdfFile(
        context: Context,
        fileUri: Uri,
        fileName: String,
        mimeType: String,
        sizeBytes: Long,
        message: String,
        model: String = ConfigManager.defaultModel
    ): ApiResult<ChatResponse> = withContext(Dispatchers.IO) {
        try {
            val providerConfig = ConfigManager.getProviderConfig()
            if (providerConfig.provider != ApiProvider.HUGGINGFACE) {
                return@withContext ApiResult.Error("PDF backend upload is supported only through the Go backend")
            }

            val firebaseToken = FirebaseAuthHelper.getFirebaseIdToken(forceRefresh = false)
                ?: return@withContext ApiResult.Error("Please sign in before sending file requests")

            val effectiveMime = mimeType.ifBlank { "application/pdf" }
            val fileBody = uriRequestBody(context, fileUri, effectiveMime, sizeBytes)
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("message", message)
                .addFormDataPart("model", model)
                .addFormDataPart("file", fileName, fileBody)
                .build()

            val request = Request.Builder()
                .url("${providerConfig.baseUrl.trimEnd('/')}/chat/with-file")
                .addHeader("Authorization", "Bearer $firebaseToken")
                .post(requestBody)
                .build()

            Log.i(
                TAG,
                "Uploading PDF to backend: name=$fileName, mime=$effectiveMime, bytes=$sizeBytes, model=$model"
            )

            okHttpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    parseOpenAIChatResponse(responseBody, model)
                } else {
                    ApiResult.Error(parseErrorMessage(responseBody), response.code)
                }
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
            val providerConfig = ConfigManager.getProviderConfig()
            val apiKey = providerConfig.apiKey
            val geminiConfig = ConfigManager.getGoogleGeminiConfig()
            val modelName = if (model.startsWith("google/")) model.substringAfter("google/") else model
            val usesBackendGoogle = providerConfig.baseUrl.contains("/v1/google")
            val isBetaOrAlphaApi =
                providerConfig.baseUrl.contains("/v1beta") || providerConfig.baseUrl.contains("/v1alpha")

            val firebaseToken = if (usesBackendGoogle) {
                FirebaseAuthHelper.getFirebaseIdToken(forceRefresh = false)
                    ?: return@withContext ApiResult.Error("Please sign in before using Google Studio")
            } else null

            if (!usesBackendGoogle && apiKey.isBlank()) {
                return@withContext ApiResult.Error("Google AI Studio API key is missing")
            }

            val supportsThinkingAndMediaConfig = modelName.startsWith("gemini-3") && isBetaOrAlphaApi
            
            // Google AI Studio endpoint: /v1beta/models/{model}:generateContent
            val baseUrl = providerConfig.baseUrl.trimEnd('/')
            val url = URL(
                if (usesBackendGoogle) "$baseUrl/models/$modelName:generateContent"
                else "$baseUrl/models/$modelName:generateContent?key=$apiKey"
            )
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                if (firebaseToken != null) {
                    setRequestProperty("Authorization", "Bearer $firebaseToken")
                }
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

    private fun uriRequestBody(
        context: Context,
        uri: Uri,
        mimeType: String,
        sizeBytes: Long
    ): RequestBody {
        return object : RequestBody() {
            override fun contentType() = mimeType.toMediaTypeOrNull()

            override fun contentLength(): Long = if (sizeBytes >= 0) sizeBytes else -1L

            override fun writeTo(sink: BufferedSink) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        sink.write(buffer, 0, read)
                    }
                } ?: error("Cannot open attached file")
            }
        }
    }

    private fun parseOpenAIChatResponse(response: String, fallbackModel: String): ApiResult<ChatResponse> {
        return try {
            val jsonResponse = JSONObject(response)
            val choices = jsonResponse.getJSONArray("choices")

            if (choices.length() == 0) {
                return ApiResult.Error("No response from API")
            }

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
                    content = message.optString("content", ""),
                    model = jsonResponse.optString("model", fallbackModel),
                    finishReason = choice.optString("finish_reason"),
                    usage = usage
                )
            )
        } catch (e: Exception) {
            ApiResult.Error("Invalid API response")
        }
    }

    private fun parseErrorMessage(response: String): String {
        return try {
            JSONObject(response).optJSONObject("error")?.optString("message")
                ?: JSONObject(response).optString("error", response)
        } catch (_: Exception) {
            response.ifBlank { "Request failed" }
        }
    }
    
    /**
     * Get available models
     */
    suspend fun getModels(): ApiResult<List<Model>> = withContext(Dispatchers.IO) {
        try {
            val providerConfig = ConfigManager.getProviderConfig()
            val isBackendHuggingFace = providerConfig.provider == ApiProvider.HUGGINGFACE
            val firebaseToken = if (isBackendHuggingFace) {
                FirebaseAuthHelper.getFirebaseIdToken(forceRefresh = false)
                    ?: return@withContext ApiResult.Error("Please sign in before fetching models")
            } else {
                null
            }

            val url = URL("$baseUrl/models")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer ${firebaseToken ?: apiKey}")
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
            if (config.provider != ApiProvider.HUGGINGFACE && config.apiKey.isBlank()) {
                return@withContext ApiResult.Error("Missing API key")
            }

            return@withContext when (config.provider) {
                ApiProvider.GOOGLE_AI_STUDIO -> {
                    testGoogleAiStudio(config.apiKey)
                }
                ApiProvider.HUGGINGFACE -> {
                    testBackendCompatible(config.baseUrl)
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

    private suspend fun testBackendCompatible(baseUrl: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        val firebaseToken = FirebaseAuthHelper.getFirebaseIdToken(forceRefresh = false)
            ?: return@withContext ApiResult.Error("Please sign in before testing backend")

        try {
            val url = URL("${baseUrl.trimEnd('/')}/models")
            val connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $firebaseToken")
                connectTimeout = 10000
                readTimeout = 30000
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Error("Backend rejected request", responseCode)
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
