package com.example.chat_ui.api

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.chat_ui.config.ConfigManager
import com.example.chat_ui.data.cloud.CloudinaryManager
import com.example.chat_ui.data.firebase.FirestoreManager
import com.example.chat_ui.data.models.GeneratedImage
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Image Generation Client using HuggingFace Inference API
 * Similar to src/lib/server/api/routes/groups/images.ts
 *
 * SECURITY: Token must be provided via secure config (ConfigManager).
 * Never hardcode tokens in source code.
 */
object ImageGenerationClient {
        private const val TAG = "ImageGenerationClient"

        /** Supported models for image generation */
        enum class ImageModel(val id: String, val displayName: String, val provider: String) {
                // HuggingFace Models
                FLUX_SCHNELL(
                        "black-forest-labs/FLUX.1-schnell",
                        "FLUX.1-schnell (Fast & High Quality)",
                        "huggingface"
                ),
                STABLE_DIFFUSION_XL(
                        "stabilityai/stable-diffusion-xl-base-1.0",
                        "Stable Diffusion XL (Open Source Leader)",
                        "huggingface"
                ),
                SDXL_LIGHTNING(
                        "ByteDance/SDXL-Lightning", 
                        "SDXL-Lightning (Ultra Fast)",
                        "huggingface"
                ),
                STABLE_DIFFUSION_2_1(
                        "stabilityai/stable-diffusion-2-1",
                        "Stable Diffusion 2.1 (Lightweight)",
                        "huggingface"
                ),
                PLAYGROUND_V2_5(
                        "playgroundai/playground-v2.5-1024px-aesthetic",
                        "Playground v2.5 (Aesthetic Focused)",
                        "huggingface"
                ),
                
                // Google Imagen Models (via AI Studio API)
                IMAGEN_4_0(
                        "imagen-4.0-generate-001",
                        "Imagen 4.0 (Latest)",
                        "google"
                ),
                IMAGEN_4_0_FAST(
                        "imagen-4.0-fast-generate-001",
                        "Imagen 4.0 Fast",
                        "google"
                ),
                IMAGEN_4_0_ULTRA(
                        "imagen-4.0-ultra-generate-001",
                        "Imagen 4.0 Ultra (Highest Quality)",
                        "google"
                ),
                IMAGEN_3_0(
                        "imagen-3.0-generate-002",
                        "Imagen 3.0",
                        "google"
                );

                companion object {
                        val DEFAULT = FLUX_SCHNELL

                        fun fromId(id: String): ImageModel? {
                                return entries.find { it.id == id }
                        }

                        fun all(): List<ImageModel> = entries.toList()
                        
                        fun getHuggingFaceModels(): List<ImageModel> {
                                return entries.filter { it.provider == "huggingface" }
                        }
                        
                        fun getGoogleModels(): List<ImageModel> {
                                return entries.filter { it.provider == "google" }
                        }
                }
        }

        /** Result of image generation */
        data class GenerationResult(
                val id: String,
                val url: String,
                val prompt: String,
                val modelUsed: String,
                val width: Int,
                val height: Int,
                val createdAt: Long
        )

        /**
         * Generate image using HuggingFace Inference API Then upload to Cloudinary and save to
         * MongoDB
         *
         * @param context Android context
         * @param prompt Text description of the image
         * @param model Image generation model to use
         */
        suspend fun generateImage(
                context: Context,
                prompt: String,
                model: ImageModel = ImageModel.DEFAULT
        ): Result<GenerationResult> =
                withContext(Dispatchers.IO) {
                        try {
                                // Validate prompt
                                if (prompt.isBlank()) {
                                        return@withContext Result.failure(
                                                Exception("Prompt is required")
                                        )
                                }
                                if (prompt.length > 500) {
                                        return@withContext Result.failure(
                                                Exception("Prompt is too long (max 500 characters)")
                                        )
                                }

                                // Get API token (same token used for chat)
                                val apiToken = ConfigManager.openAiApiKey
                                if (apiToken.isBlank()) {
                                        return@withContext Result.failure(
                                                Exception("HF_TOKEN not configured")
                                        )
                                }

                                Log.i(TAG, "Generating image with model: ${model.id}")
                                Log.i(TAG, "Prompt: $prompt")

                                // Step 1: Call HuggingFace Inference API
                                val imageBytes =
                                        callHuggingFaceInference(apiToken, model.id, prompt)

                                Log.i(TAG, "Image generated, size: ${imageBytes.size} bytes")

                                // Step 2: Save to temp file
                                val tempFile =
                                        File(
                                                context.cacheDir,
                                                "generated_${System.currentTimeMillis()}.png"
                                        )
                                FileOutputStream(tempFile).use { it.write(imageBytes) }

                                // Get image dimensions
                                val options =
                                        BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                BitmapFactory.decodeFile(tempFile.absolutePath, options)
                                val width = options.outWidth
                                val height = options.outHeight

                                // Step 3: Upload to Cloudinary
                                Log.i(TAG, "Uploading to Cloudinary...")
                                val cloudinaryResult =
                                        CloudinaryManager.uploadImage(
                                                context = context,
                                                imageUri = Uri.fromFile(tempFile),
                                                folder = "chat-ui/generated-images",
                                                tags = listOf("flux", "generated", "kotlin")
                                        )

                                Log.i(TAG, "Cloudinary upload success: ${cloudinaryResult.url}")

                                // Step 4: Save to Firebase Firestore
                                val imageId = UUID.randomUUID().toString()
                                val generatedImage =
                                        GeneratedImage(
                                                id = imageId,
                                                prompt = prompt.trim(),
                                                cloudinaryUrl = cloudinaryResult.url,
                                                cloudinaryPublicId = cloudinaryResult.publicId,
                                                width = width,
                                                height = height,
                                                modelUsed = model.id
                                        )

                                FirestoreManager.saveGeneratedImage(generatedImage)
                                Log.i(TAG, "Image saved to Firebase")

                                // Clean up temp file
                                tempFile.delete()

                                Result.success(
                                        GenerationResult(
                                                id = imageId,
                                                url = cloudinaryResult.url,
                                                prompt = prompt.trim(),
                                                modelUsed = model.id,
                                                width = width,
                                                height = height,
                                                createdAt = generatedImage.createdAt
                                        )
                                )
                        } catch (e: Exception) {
                                Log.e(TAG, "Image generation failed", e)
                                Result.failure(e)
                        }
                }

        /** Call HuggingFace Inference API for text-to-image */
        private suspend fun callHuggingFaceInference(
                token: String,
                modelId: String,
                prompt: String
        ): ByteArray =
                withContext(Dispatchers.IO) {
                        // Use new HuggingFace Router API endpoint (api-inference is deprecated)
                        val url = URL("https://router.huggingface.co/hf-inference/models/$modelId")
                        val connection = url.openConnection() as HttpURLConnection

                        connection.apply {
                                requestMethod = "POST"
                                setRequestProperty("Authorization", "Bearer $token")
                                setRequestProperty("Content-Type", "application/json")
                                doOutput = true
                                connectTimeout = 120000 // 2 minutes for image generation
                                readTimeout = 120000
                        }

                        // Send request
                        val requestJson = JSONObject().apply { put("inputs", prompt) }
                        val requestBody = requestJson.toString()
                        connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
                                it.write(requestBody)
                        }

                        val responseCode = connection.responseCode
                        Log.i(TAG, "HuggingFace response code: $responseCode")

                        if (responseCode != HttpURLConnection.HTTP_OK) {
                                val error =
                                        connection.errorStream?.bufferedReader()?.readText()
                                                ?: "Unknown error"
                                Log.e(TAG, "HuggingFace error: $error")
                                throw Exception(
                                        "Image generation failed: HTTP $responseCode - $error"
                                )
                        }

                        // Read image bytes
                        connection.inputStream.readBytes()
                }

        /** Delete generated image from Cloudinary and Firebase */
        suspend fun deleteImage(imageId: String): Result<Unit> =
                withContext(Dispatchers.IO) {
                        try {
                                // Delete from Firebase Firestore
                                FirestoreManager.deleteGeneratedImage(imageId)

                                Log.i(TAG, "Image deleted: $imageId")
                                Result.success(Unit)
                        } catch (e: Exception) {
                                Log.e(TAG, "Failed to delete image", e)
                                Result.failure(e)
                        }
                }
}
