package com.example.chat_ui.api

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.chat_ui.config.ConfigManager
import com.example.chat_ui.data.ApiProvider
import com.example.chat_ui.data.cloud.CloudinaryManager
import com.example.chat_ui.data.firebase.FirebaseDatabaseManager
import com.example.chat_ui.data.firebase.FirestoreManager
import com.example.chat_ui.data.models.GeneratedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * API Client for Image Generation
 * Supports:
 * - Google Gemini Image models (gemini-2.5-flash-image, gemini-3-pro-image-preview)
 * - Google Imagen models (imagen-4.0-*)
 * - HuggingFace models (any model with text-to-image capability)
 */
class ImageGenerationApiClient {
    companion object {
        private const val TAG = "ImageGenApiClient"
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    data class ImageGenerationRequest(
        val prompt: String,
        val negativePrompt: String? = null,
        val aspectRatio: String = "1:1",
        val imageSize: String? = null, // "1K", "2K", "4K" for Gemini 3 Pro
        val numberOfImages: Int = 1,
        val guidanceScale: Float? = null,
        val seed: Long? = null,
        val referenceImageBase64: String? = null,
        val referenceImageMimeType: String? = null
    )
    
    data class GeneratedImageResult(
        val base64Data: String,
        val mimeType: String = "image/png",
        val width: Int? = null,
        val height: Int? = null,
        val cloudinaryUrl: String? = null,
        val cloudinaryPublicId: String? = null,
        val firestoreId: String? = null
    )
    
    sealed class ImageGenResult {
        data class Success(val images: List<GeneratedImageResult>) : ImageGenResult()
        data class Error(val message: String) : ImageGenResult()
    }
    
    /**
     * Save generated image to Cloudinary and Firestore
     */
    suspend fun saveImageToCloudinaryAndFirestore(
        context: Context,
        base64Data: String,
        prompt: String,
        modelId: String
    ): Result<GeneratedImage> = withContext(Dispatchers.IO) {
        try {
            // Decode base64 to bytes
            val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
            
            // Save to temp file
            val tempFile = File(context.cacheDir, "gen_${System.currentTimeMillis()}.png")
            FileOutputStream(tempFile).use { it.write(imageBytes) }
            
            // Upload to Cloudinary
            val cloudinaryResult = CloudinaryManager.uploadImage(
                context = context,
                imageUri = Uri.fromFile(tempFile),
                folder = "chat-ui/generated-images",
                tags = listOf("ai-generated", modelId.replace("/", "-"))
            )
            
            // Get dimensions
            val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(tempFile.absolutePath, options)
            
            // Save to Firestore
            val imageId = UUID.randomUUID().toString()
            val generatedImage = GeneratedImage(
                id = imageId,
                prompt = prompt.trim(),
                cloudinaryUrl = cloudinaryResult.url,
                cloudinaryPublicId = cloudinaryResult.publicId,
                width = options.outWidth,
                height = options.outHeight,
                modelUsed = modelId
            )
            
            FirestoreManager.saveGeneratedImage(generatedImage)
            FirebaseDatabaseManager.saveGeneratedImage(
                imageId = imageId,
                prompt = prompt.trim(),
                imageUrl = cloudinaryResult.url,
                model = modelId,
                cloudinaryPublicId = cloudinaryResult.publicId,
                width = options.outWidth,
                height = options.outHeight
            )
            
            // Cleanup
            tempFile.delete()
            
            Log.i(TAG, "Image saved: $imageId -> ${cloudinaryResult.url}")
            Result.success(generatedImage)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save image", e)
            Result.failure(e)
        }
    }
    
    /**
     * Simple wrapper for generating and optionally saving images
     */
    suspend fun generateImage(
        prompt: String,
        modelId: String = "google/imagen-4.0-generate-001",
        context: Context,
        saveToGallery: Boolean = true,
        numberOfImages: Int = 1
    ): ImageGenResult = withContext(Dispatchers.IO) {
        try {
            val request = ImageGenerationRequest(
                prompt = prompt,
                numberOfImages = numberOfImages
            )
            
            generateImage(
                model = modelId,
                request = request,
                context = context,
                saveToGallery = saveToGallery
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in generateImage wrapper", e)
            ImageGenResult.Error(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Generate image using the appropriate API based on provider
     */
    suspend fun generateImage(
        model: String,
        request: ImageGenerationRequest,
        context: Context? = null,
        saveToGallery: Boolean = false
    ): ImageGenResult = withContext(Dispatchers.IO) {
        try {
            val providerConfig = ConfigManager.getProviderConfig()
            
            val rawResult = when (providerConfig.provider) {
                ApiProvider.GOOGLE_AI_STUDIO -> generateWithGoogleAIStudio(model, request)
                ApiProvider.HUGGINGFACE -> generateWithHuggingFace(model, request)
            }

            if (!saveToGallery || context == null || rawResult !is ImageGenResult.Success) {
                rawResult
            } else {
                val savedImages = rawResult.images.map { img ->
                    val saveResult = saveImageToCloudinaryAndFirestore(
                        context = context,
                        base64Data = img.base64Data,
                        prompt = request.prompt,
                        modelId = model
                    )

                    if (saveResult.isSuccess) {
                        val generatedImg = saveResult.getOrNull()
                        img.copy(
                            base64Data = "",
                            cloudinaryUrl = generatedImg?.cloudinaryUrl,
                            cloudinaryPublicId = generatedImg?.cloudinaryPublicId,
                            firestoreId = generatedImg?.id,
                            width = img.width ?: generatedImg?.width,
                            height = img.height ?: generatedImg?.height
                        )
                    } else {
                        Log.w(TAG, "Failed to persist generated image: ${saveResult.exceptionOrNull()?.message}")
                        img
                    }
                }
                ImageGenResult.Success(savedImages)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating image", e)
            ImageGenResult.Error(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Generate image using Google AI Studio API
     * Supports: gemini-2.5-flash-image, gemini-3-pro-image-preview, imagen-4.0-*
     */
    private suspend fun generateWithGoogleAIStudio(
        model: String,
        request: ImageGenerationRequest
    ): ImageGenResult {
        // Use provider config for API key
        val providerConfig = ConfigManager.getProviderConfig()
        val geminiConfig = ConfigManager.getGoogleGeminiConfig()
        val apiKey = providerConfig.apiKey
        
        if (apiKey.isBlank()) {
            return ImageGenResult.Error("Google AI Studio API Key is missing. Please configure it in API Settings.")
        }
        
        val modelId = model.removePrefix("google/")
        val isGeminiImage = modelId.startsWith("gemini-") && modelId.contains("image")
        val isImagen = modelId.startsWith("imagen-")
        
        // Use base URL from provider config
        val baseUrl = providerConfig.baseUrl.trimEnd('/')
        val url = when {
            isGeminiImage || isImagen -> {
                "$baseUrl/models/$modelId:generateContent?key=$apiKey"
            }
            else -> return ImageGenResult.Error("Unsupported model: $modelId")
        }
        
        // Build request body based on model type
        val requestBody = if (isGeminiImage) {
            // Gemini image models use responseModalities; support optional image-to-image by providing inlineData
            buildString {
                append("{")
                append("\"contents\":[{\"parts\":[")
                if (request.referenceImageBase64 != null && request.referenceImageMimeType != null) {
                    append(
                        "{\"inlineData\":{\"mimeType\":\"${request.referenceImageMimeType}\",\"data\":\"${request.referenceImageBase64}\"}},"
                    )
                }
                append("{\"text\":\"${request.prompt.escapeJson()}\"}")
                append("]}]")
                append(",\"generationConfig\":{")
                append("\"responseModalities\":[\"IMAGE\",\"TEXT\"],")
                append("\"candidateCount\":${request.numberOfImages.coerceIn(1, 4)}")
                // NOTE:
                // The public Gemini generateContent schema does NOT accept "imageSize" inside generationConfig.
                // Sending it causes: HTTP 400 "Unknown name \"imageSize\""
                append("}")
                append("}")
            }
        } else {
            // Imagen models use imageConfig with sampleCount
            val effectiveAspectRatio =
                if (request.aspectRatio == "1:1") geminiConfig.imageAspectRatio.value else request.aspectRatio
            val effectiveImageSize = request.imageSize ?: geminiConfig.imageSize.value
            buildString {
                append("{")
                append("\"contents\":[{\"parts\":[{\"text\":\"${request.prompt.escapeJson()}\"}]}],")
                append("\"generationConfig\":{")
                append("\"imageConfig\":{")
                append("\"aspectRatio\":\"${effectiveAspectRatio}\"")

                if (effectiveImageSize.isNotBlank()) {
                    append(",\"imageSize\":\"${effectiveImageSize}\"")
                }

                // negativePrompt support for Imagen
                if (request.negativePrompt != null) {
                    append(",\"negativePrompt\":\"${request.negativePrompt.escapeJson()}\"")
                }

                // guidanceScale support for Imagen
                if (request.guidanceScale != null) {
                    append(",\"guidanceScale\":${request.guidanceScale}")
                }

                // seed support for Imagen
                if (request.seed != null) {
                    append(",\"seed\":${request.seed}")
                }

                // numberOfImages for Imagen (max 4)
                append(",\"sampleCount\":${request.numberOfImages.coerceIn(1, 4)}")

                append("}")
                append("}")
                append("}")
            }
        }
        
        Log.d(TAG, "Request to $url")
        Log.d(TAG, "Body: $requestBody")
        
        val httpRequest = Request.Builder()
            .url(url)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        val response = client.newCall(httpRequest).execute()
        val responseBody = response.body?.string() ?: ""
        
        if (!response.isSuccessful) {
            Log.e(TAG, "API Error: HTTP ${response.code} - $responseBody")
            return ImageGenResult.Error("HTTP ${response.code}: ${parseErrorMessage(responseBody)}")
        }
        
        // Parse response
        return try {
            val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
            val candidates = jsonResponse["candidates"]?.jsonArray
            
            if (candidates.isNullOrEmpty()) {
                return ImageGenResult.Error("No images generated")
            }
            
            val images: MutableList<GeneratedImageResult> = mutableListOf()
            
            for (candidate in candidates) {
                val content = candidate.jsonObject["content"]?.jsonObject
                val parts = content?.get("parts")?.jsonArray ?: continue
                
                for (part in parts) {
                    val inlineData = part.jsonObject["inlineData"]?.jsonObject ?: continue
                    val mimeType = inlineData["mimeType"]?.jsonPrimitive?.content ?: "image/png"
                    val data = inlineData["data"]?.jsonPrimitive?.content ?: continue
                    
                    images.add(GeneratedImageResult(
                        base64Data = data,
                        mimeType = mimeType
                    ))
                }
            }
            
            if (images.isEmpty()) {
                ImageGenResult.Error("No valid images in response")
            } else {
                Log.i(TAG, "Successfully generated ${images.size} image(s)")
                ImageGenResult.Success(images)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing response", e)
            ImageGenResult.Error("Failed to parse response: ${e.message}")
        }
    }
    
    // Vertex AI support removed
    
    /**
     * Generate image using HuggingFace API
     */
    private suspend fun generateWithHuggingFace(
        model: String,
        request: ImageGenerationRequest
    ): ImageGenResult {
        // Use provider config for both API key and base URL
        val providerConfig = ConfigManager.getProviderConfig()
        val apiKey = providerConfig.apiKey
        val baseUrl = providerConfig.baseUrl.trimEnd('/')
        
        if (apiKey.isBlank()) {
            return ImageGenResult.Error("HuggingFace API Key is missing. Please configure it in API Settings.")
        }
        
        val cleanModel = model.removePrefix("hf/")
        val url = when {
            baseUrl.endsWith("/v1") -> "https://router.huggingface.co/hf-inference/models/$cleanModel"
            baseUrl.contains("/hf-inference/") -> "${baseUrl.trimEnd('/')}/$cleanModel"
            else -> "${baseUrl.trimEnd('/')}/models/$cleanModel"
        }
        
        // HuggingFace text-to-image format
        val requestBody = buildString {
            append("{")
            append("\"inputs\":\"${request.prompt.escapeJson()}\"")
            
            if (request.negativePrompt != null || request.guidanceScale != null || request.seed != null) {
                append(",\"parameters\":{")
                val params = mutableListOf<String>()
                
                request.negativePrompt?.let {
                    params.add("\"negative_prompt\":\"${it.escapeJson()}\"")
                }
                request.guidanceScale?.let {
                    params.add("\"guidance_scale\":$it")
                }
                request.seed?.let {
                    params.add("\"seed\":$it")
                }
                
                append(params.joinToString(","))
                append("}")
            }
            
            append("}")
        }
        
        Log.d(TAG, "HuggingFace Request: $url")
        
        val httpRequest = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        val response = client.newCall(httpRequest).execute()
        
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            Log.e(TAG, "HuggingFace Error: HTTP ${response.code} - $errorBody")
            return ImageGenResult.Error("HTTP ${response.code}: ${parseErrorMessage(errorBody)}")
        }
        
        // HuggingFace returns image bytes directly
        val imageBytes = response.body?.bytes()
        if (imageBytes == null || imageBytes.isEmpty()) {
            return ImageGenResult.Error("Empty response from HuggingFace")
        }
        
        val base64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
        
        return ImageGenResult.Success(listOf(
            GeneratedImageResult(
                base64Data = base64,
                mimeType = "image/png"
            )
        ))
    }
    
    private fun parseErrorMessage(responseBody: String): String {
        return try {
            val jsonError = json.parseToJsonElement(responseBody).jsonObject
            val errorNode = jsonError["error"]
            when {
                errorNode == null -> "Unknown error"
                errorNode is kotlinx.serialization.json.JsonPrimitive -> errorNode.content
                else -> errorNode.jsonObject["message"]?.jsonPrimitive?.content ?: "Unknown error"
            }
        } catch (e: Exception) {
            responseBody.take(220).ifBlank { "API error" }
        }
    }
    
    private fun String.escapeJson(): String {
        return this
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
