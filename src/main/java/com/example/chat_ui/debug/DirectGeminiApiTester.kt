package com.example.chat_ui.debug

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Direct Google AI Studio API tester.
 * 
 * Uses the generativelanguage.googleapis.com endpoint with API key.
 * Tests model availability WITHOUT consuming tokens by using:
 * - GET /v1beta/models (list all models - FREE)
 * - GET /v1beta/models/{model} (get model info - FREE)
 * 
 * Reference: https://ai.google.dev/gemini-api/docs/troubleshooting
 */
class DirectGeminiApiTester(
    private val apiKey: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta"

    data class ModelInfo(
        val id: String,
        val displayName: String,
        val description: String,
        val inputTokenLimit: Int,
        val outputTokenLimit: Int,
        val supportedMethods: List<String>,
        val available: Boolean,
        val error: String? = null
    )

    /**
     * Fetch all available models from Google AI Studio API.
     * This is FREE and doesn't consume any tokens.
     */
    suspend fun fetchAllModels(): List<ModelInfo> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/models?key=$apiKey"
        
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Accept", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                
                if (!response.isSuccessful) {
                    val errorMsg = parseError(body, response.code)
                    return@withContext listOf(
                        ModelInfo(
                            id = "error",
                            displayName = "API Error",
                            description = errorMsg,
                            inputTokenLimit = 0,
                            outputTokenLimit = 0,
                            supportedMethods = emptyList(),
                            available = false,
                            error = errorMsg
                        )
                    )
                }

                val json = JSONObject(body)
                val modelsArray = json.optJSONArray("models") ?: JSONArray()
                
                val models = mutableListOf<ModelInfo>()
                for (i in 0 until modelsArray.length()) {
                    val model = modelsArray.getJSONObject(i)
                    val name = model.optString("name", "")
                    val modelId = name.removePrefix("models/")
                    
                    // Filter for Gemini, Imagen, and Veo models
                    if (modelId.contains("gemini") || 
                        modelId.contains("imagen") || 
                        modelId.contains("veo")) {
                        
                        val methods = mutableListOf<String>()
                        val supportedMethods = model.optJSONArray("supportedGenerationMethods")
                        if (supportedMethods != null) {
                            for (j in 0 until supportedMethods.length()) {
                                methods.add(supportedMethods.getString(j))
                            }
                        }
                        
                        models.add(
                            ModelInfo(
                                id = modelId,
                                displayName = model.optString("displayName", modelId),
                                description = model.optString("description", ""),
                                inputTokenLimit = model.optInt("inputTokenLimit", 0),
                                outputTokenLimit = model.optInt("outputTokenLimit", 0),
                                supportedMethods = methods,
                                available = true
                            )
                        )
                    }
                }
                
                models.sortedBy { it.id }
            }
        } catch (e: Exception) {
            listOf(
                ModelInfo(
                    id = "error",
                    displayName = "Connection Error",
                    description = e.message ?: "Unknown error",
                    inputTokenLimit = 0,
                    outputTokenLimit = 0,
                    supportedMethods = emptyList(),
                    available = false,
                    error = e.message
                )
            )
        }
    }

    /**
     * Check if a specific model is available.
     * This is FREE and doesn't consume any tokens.
     */
    suspend fun checkModelAvailability(modelId: String): ModelInfo = withContext(Dispatchers.IO) {
        val cleanModelId = modelId.removePrefix("models/").removePrefix("google/")
        val url = "$baseUrl/models/$cleanModelId?key=$apiKey"
        
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Accept", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                
                if (!response.isSuccessful) {
                    val errorMsg = parseError(body, response.code)
                    return@withContext ModelInfo(
                        id = cleanModelId,
                        displayName = cleanModelId,
                        description = "",
                        inputTokenLimit = 0,
                        outputTokenLimit = 0,
                        supportedMethods = emptyList(),
                        available = false,
                        error = errorMsg
                    )
                }

                val model = JSONObject(body)
                val methods = mutableListOf<String>()
                val supportedMethods = model.optJSONArray("supportedGenerationMethods")
                if (supportedMethods != null) {
                    for (j in 0 until supportedMethods.length()) {
                        methods.add(supportedMethods.getString(j))
                    }
                }
                
                ModelInfo(
                    id = cleanModelId,
                    displayName = model.optString("displayName", cleanModelId),
                    description = model.optString("description", ""),
                    inputTokenLimit = model.optInt("inputTokenLimit", 0),
                    outputTokenLimit = model.optInt("outputTokenLimit", 0),
                    supportedMethods = methods,
                    available = true
                )
            }
        } catch (e: Exception) {
            ModelInfo(
                id = cleanModelId,
                displayName = cleanModelId,
                description = "",
                inputTokenLimit = 0,
                outputTokenLimit = 0,
                supportedMethods = emptyList(),
                available = false,
                error = e.message
            )
        }
    }

    /**
     * Test multiple specific models for availability.
     * Tests ALL Google models: Gemini (chat), Veo (video), Imagen (image).
     */
    suspend fun testAllGoogleModels(): List<ModelInfo> = withContext(Dispatchers.IO) {
        // All Google models from Vertex AI API
        val modelsToTest = listOf(
            // ========== GEMINI CHAT MODELS ==========
            // Gemini 3 Series (Preview)
            "gemini-3-pro-preview",
            "gemini-3-pro-image-preview",
            "gemini-3-flash-preview",
            // Gemini 2.5 Series
            "gemini-2.5-pro",
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-2.5-flash-image",
            "gemini-2.5-flash-image-preview",
            "gemini-2.5-flash-preview-09-2025",
            "gemini-2.5-flash-lite-preview-09-2025",
            "gemini-2.5-computer-use-preview-10-2025",
            // Gemini 2.0 Series
            "gemini-2.0-flash-001",
            "gemini-2.0-flash-lite-001",
            // Gemini 1.5 Series
            "gemini-1.5-pro-002",
            "gemini-1.5-flash-002",
            // Embedding
            "gemini-embedding-001",
            
            // ========== VEO VIDEO GENERATION MODELS ==========
            // Veo 3.1 Series
            "veo-3.1-generate-001",
            "veo-3.1-fast-generate-001",
            "veo-3.1-generate-preview",
            "veo-3.1-fast-generate-preview",
            // Veo 3.0 Series
            "veo-3.0-generate-001",
            "veo-3.0-fast-generate-001",
            "veo-3.0-generate-preview",
            "veo-3.0-fast-generate-preview",
            // Veo 2.0 Series
            "veo-2.0-generate-001",
            
            // ========== IMAGEN IMAGE GENERATION MODELS ==========
            // Imagen 4 Series
            "imagen-4.0-generate-001",
            "imagen-4.0-fast-generate-001",
            "imagen-4.0-ultra-generate-001",
            "imagen-4.0-generate-preview-06-06",
            "imagen-4.0-fast-generate-preview-06-06",
            "imagen-4.0-ultra-generate-preview-06-06",
            // Imagen 3 Series
            "imagen-3.0-generate-002",
            "imagen-3.0-capability-001",
            "imagen-3.0-capability-002",
            // Special
            "imagen-product-recontext-preview-06-30",
        )

        modelsToTest.map { modelId ->
            checkModelAvailability(modelId)
        }
    }

    /**
     * Parse error response from API.
     * Reference: https://ai.google.dev/gemini-api/docs/troubleshooting
     */
    private fun parseError(body: String, code: Int): String {
        return try {
            val json = JSONObject(body)
            val error = json.optJSONObject("error")
            val message = error?.optString("message") ?: "Unknown error"
            val status = error?.optString("status") ?: ""
            
            when (code) {
                400 -> "[$status] Bad Request: $message"
                401 -> "[$status] Invalid API Key: $message"
                403 -> "[$status] Permission Denied: $message"
                404 -> "[$status] Model Not Found: $message"
                429 -> "[$status] Rate Limit Exceeded: $message"
                500 -> "[$status] Server Error: $message"
                503 -> "[$status] Service Unavailable: $message"
                else -> "[$code] $message"
            }
        } catch (e: Exception) {
            "HTTP $code: $body"
        }
    }
}
