package com.example.chat_ui.api

import android.content.Context
import android.util.Log
import com.example.chat_ui.config.ConfigManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

/**
 * Vertex AI Client via Backend Proxy
 * 
 * Uses Veo Backend as a proxy to Vertex AI:
 * - Endpoint: /v1/chat/completions (OpenAI compatible)
 * - Auth: Firebase ID Token
 * - Backend uses Service Account for Vertex AI
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
object VertexAIClient {
    private const val TAG = "VertexAIClient"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    // Current active EventSource for cancellation
    @Volatile
    private var currentEventSource: EventSource? = null
    private val streamLock = Any()
    
    /**
     * Cancel the current streaming request
     */
    fun cancelCurrentStream() {
        synchronized(streamLock) {
            Log.d(TAG, "Cancelling current stream")
            currentEventSource?.cancel()
            currentEventSource = null
        }
    }
    
    private fun setCurrentEventSource(eventSource: EventSource?) {
        synchronized(streamLock) {
            currentEventSource?.cancel()
            currentEventSource = eventSource
        }
    }
    
    /**
     * Get Firebase ID Token for authentication
     * Note: For Vertex AI, we use Firebase ID Token with proper IAM setup
     */
    suspend fun getFirebaseToken(): String? = withContext(Dispatchers.IO) {
        try {
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                Log.e(TAG, "No Firebase user signed in")
                return@withContext null
            }
            
            // Get fresh Firebase ID Token
            val tokenResult = currentUser.getIdToken(false).await()
            val token = tokenResult.token
            
            if (token != null) {
                Log.i(TAG, "Got Firebase ID Token successfully")
            }
            token
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get Firebase ID Token", e)
            null
        }
    }
    
    /**
     * Get Veo Backend URL for chat completions
     */
    fun getBackendChatEndpoint(): String {
        val backendUrl = ConfigManager.get(ConfigManager.Keys.VEO_BACKEND_BASE_URL, 
            "https://veo-backend-347302193342.us-central1.run.app")
        return "${backendUrl.trimEnd('/')}/v1/chat/completions"
    }
    
    
    /**
     * Stream chat completion via Veo Backend Proxy
     * Uses OpenAI-compatible format - backend handles Vertex AI conversion
     */
    fun streamGenerateContent(
        context: Context,
        messages: JsonArray,
        model: String,
        temperature: Double = 0.7,
        maxTokens: Int = 4096
    ): Flow<StreamEvent> = callbackFlow {
        // Get Firebase ID Token for backend auth
        val accessToken = try {
            getFirebaseToken()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get access token", e)
            null
        }
        
        if (accessToken == null) {
            trySend(StreamEvent.Error("❌ فشل الحصول على Token\n\nتأكد من تسجيل الدخول بـ Google"))
            close()
            return@callbackFlow
        }
        
        // Use Veo Backend as proxy
        val endpoint = getBackendChatEndpoint()
        Log.i(TAG, "Using Backend Proxy: $endpoint")
        
        // Build OpenAI-compatible request body
        val requestBody = buildJsonObject {
            put("model", model)
            put("messages", messages)
            put("stream", true)
            put("temperature", temperature)
            put("max_tokens", maxTokens)
        }.toString()
        
        Log.d(TAG, "Request body: $requestBody")
        
        // Build HTTP request
        val request = Request.Builder()
            .url(endpoint)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $accessToken")
            .build()
        
        var fullText = ""
        
        try {
            val eventSource = EventSources.createFactory(client)
                .newEventSource(request, object : EventSourceListener() {
                    override fun onOpen(eventSource: EventSource, response: Response) {
                        Log.d(TAG, "Backend stream opened")
                        trySend(StreamEvent.Status("Connected to Gemini via Backend", false))
                    }
                    
                    override fun onEvent(
                        eventSource: EventSource,
                        id: String?,
                        type: String?,
                        data: String
                    ) {
                        if (data == "[DONE]") {
                            trySend(StreamEvent.Complete(fullText, false))
                            close()
                            return
                        }
                        
                        try {
                            val jsonElement = json.parseToJsonElement(data)
                            val jsonObject = jsonElement.jsonObject
                            
                            // Parse OpenAI-compatible response format
                            val choices = jsonObject["choices"]?.jsonArray
                            if (choices != null && choices.isNotEmpty()) {
                                val choice = choices[0].jsonObject
                                val delta = choice["delta"]?.jsonObject
                                val content = delta?.get("content")?.jsonPrimitive?.content
                                
                                if (content != null && content.isNotEmpty()) {
                                    fullText += content
                                    trySend(StreamEvent.Token(content))
                                }
                                
                                // Check for finish reason
                                val finishReason = choice["finish_reason"]?.jsonPrimitive?.content
                                if (finishReason == "stop") {
                                    trySend(StreamEvent.Complete(fullText, false))
                                    close()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse response: ${e.message}", e)
                        }
                    }
                    
                    override fun onClosed(eventSource: EventSource) {
                        Log.d(TAG, "Backend stream closed")
                        if (fullText.isNotEmpty()) {
                            trySend(StreamEvent.Complete(fullText, false))
                        }
                        channel.close()
                    }
                    
                    override fun onFailure(
                        eventSource: EventSource,
                        t: Throwable?,
                        response: Response?
                    ) {
                        Log.e(TAG, "Backend stream failed: ${t?.message}", t)
                        val statusCode = response?.code
                        val responseBody = try { response?.body?.string() } catch (e: Exception) { null }
                        
                        val errorMsg = buildString {
                            when (statusCode) {
                                400 -> append("⚠️ طلب غير صالح")
                                401 -> append("⚠️ غير مصرح - تحقق من تسجيل الدخول")
                                403 -> append("⚠️ غير مسموح")
                                429 -> append("⚠️ تجاوزت حد الاستخدام")
                                500, 502, 503 -> append("⚠️ خطأ في السيرفر")
                                else -> append("⚠️ خطأ: ${t?.message ?: "غير معروف"}")
                            }
                            
                            if (statusCode != null) append(" ($statusCode)")
                            
                            // Try to extract error message from response
                            if (!responseBody.isNullOrBlank()) {
                                try {
                                    val errorJson = json.parseToJsonElement(responseBody).jsonObject
                                    val errorMessage = errorJson["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                                    if (errorMessage != null) {
                                        append("\n$errorMessage")
                                    }
                                } catch (e: Exception) {
                                    if (responseBody.length < 300) append("\n$responseBody")
                                }
                            }
                        }
                        
                        Log.e(TAG, "Error: $statusCode - $responseBody")
                        trySend(StreamEvent.Error(errorMsg, statusCode))
                        close(t)
                    }
                })
            
            setCurrentEventSource(eventSource)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create stream", e)
            trySend(StreamEvent.Error(e.message ?: "Unknown error"))
            close(e)
        }
        
        awaitClose {
            Log.d(TAG, "Closing stream")
            cancelCurrentStream()
        }
    }
}
