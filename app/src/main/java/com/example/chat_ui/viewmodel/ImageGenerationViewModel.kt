package com.example.chat_ui.viewmodel

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat_ui.api.ImageGenerationApiClient
import com.example.chat_ui.config.ConfigManager
import com.example.chat_ui.data.ApiProvider
import kotlinx.coroutines.launch
import android.util.Base64
import com.example.chat_ui.utils.PromptPreferences

class ImageGenerationViewModel : ViewModel() {

    private val imageGenClient = ImageGenerationApiClient()

    private fun mapToUserFriendlyError(message: String): String {
        val msg = message.trim()

        val isQuotaIssue =
            msg.contains("quota", ignoreCase = true) ||
                msg.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
                msg.contains("limit: 0", ignoreCase = true)

        if (isQuotaIssue) {
            return "This model isn't available for your current quota/plan. Try another model or enable billing.\n\nDetails: $msg"
        }

        return msg
    }
    
    // State
    var prompt by mutableStateOf("")
        private set
    
    var negativePrompt by mutableStateOf("")
        private set
    
    var selectedModel by mutableStateOf("google/gemini-2.5-flash-image")
        private set
    
    var aspectRatio by mutableStateOf("1:1")
        private set
    
    var imageSize by mutableStateOf("1K")
        private set
    
    var numberOfImages by mutableStateOf(1)
        private set
    
    var guidanceScale by mutableStateOf(7.5f)
        private set
    
    var seed by mutableStateOf<Long?>(null)
        private set
    
    // Optional reference image for image-to-image (Google Gemini Image models)
    var referenceImageBase64 by mutableStateOf<String?>(null)
        private set
    var referenceImageMimeType by mutableStateOf<String?>(null)
        private set
    
    var isGenerating by mutableStateOf(false)
        private set
    
    var generatedImages by mutableStateOf<List<GeneratedImageData>>(emptyList())
        private set
    
    var errorMessage by mutableStateOf<String?>(null)
        private set
    
    var generationHistory by mutableStateOf<List<GenerationHistoryItem>>(emptyList())
        private set
    
    data class GeneratedImageData(
        val base64Data: String,
        val mimeType: String,
        val prompt: String,
        val model: String,
        val timestamp: Long = System.currentTimeMillis(),
        val width: Int? = null,
        val height: Int? = null
    )
    
    data class GenerationHistoryItem(
        val id: String = java.util.UUID.randomUUID().toString(),
        val prompt: String,
        val model: String,
        val images: List<GeneratedImageData>,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    // Actions
    fun updatePrompt(newPrompt: String) {
        prompt = newPrompt
        errorMessage = null
    }
    
    fun updateNegativePrompt(newPrompt: String) {
        negativePrompt = newPrompt
    }
    
    fun updateSelectedModel(model: String) {
        selectedModel = model
        
        // Reset imageSize if not Gemini 3 Pro
        if (!model.contains("gemini-3-pro-image")) {
            imageSize = "1K"
        }
    }
    
    fun updateAspectRatio(ratio: String) {
        aspectRatio = ratio
    }
    
    fun updateImageSize(size: String) {
        imageSize = size
    }
    
    fun updateNumberOfImages(count: Int) {
        numberOfImages = count.coerceIn(1, 4)
    }
    
    fun updateGuidanceScale(scale: Float) {
        guidanceScale = scale.coerceIn(1f, 20f)
    }
    
    fun updateSeed(newSeed: Long?) {
        seed = newSeed
    }
    
    fun updateReferenceImage(base64: String, mimeType: String) {
        referenceImageBase64 = base64
        referenceImageMimeType = mimeType
    }
    
    fun clearReferenceImage() {
        referenceImageBase64 = null
        referenceImageMimeType = null
    }
    
    fun clearError() {
        errorMessage = null
    }
    
    /**
     * Generate image with current settings
     */
    fun generateImage(context: Context? = null, saveToFirestore: Boolean = false) {
        if (prompt.isBlank()) {
            errorMessage = "Please enter a prompt"
            return
        }
        
        if (isGenerating) return
        
        viewModelScope.launch {
            try {
                isGenerating = true
                errorMessage = null
                
                val request = ImageGenerationApiClient.ImageGenerationRequest(
                    prompt = prompt,
                    negativePrompt = negativePrompt.takeIf { it.isNotBlank() },
                    aspectRatio = aspectRatio,
                    // imageSize is currently not supported by the public Gemini generateContent schema.
                    // Keep it null to avoid sending invalid JSON fields.
                    imageSize = null,
                    numberOfImages = numberOfImages,
                    guidanceScale = guidanceScale.takeIf { ConfigManager.getProviderConfig().provider == ApiProvider.HUGGINGFACE },
                    seed = seed,
                    referenceImageBase64 = referenceImageBase64,
                    referenceImageMimeType = referenceImageMimeType
                )
                
                when (val result = imageGenClient.generateImage(selectedModel, request)) {
                    is ImageGenerationApiClient.ImageGenResult.Success -> {
                        val newImages = result.images.map { img ->
                            GeneratedImageData(
                                base64Data = img.base64Data,
                                mimeType = img.mimeType,
                                prompt = prompt,
                                model = selectedModel,
                                width = img.width,
                                height = img.height
                            )
                        }
                        
                        generatedImages = newImages
                        
                        // Save to Cloudinary/Firestore if requested - REMOVED (Backend cancelled)
                        /*
                        if (saveToFirestore && context != null && newImages.isNotEmpty()) {
                            newImages.forEach { imageData ->
                                try {
                                    imageGenClient.saveImageToCloudinaryAndFirestore(
                                        context = context,
                                        base64Data = imageData.base64Data,
                                        prompt = imageData.prompt,
                                        modelId = imageData.model
                                    )
                                } catch (e: Exception) {
                                    // Log but don't fail the whole operation
                                    android.util.Log.e("ImageGenViewModel", "Failed to save image", e)
                                }
                            }
                        }
                        */
                        
                        // Add to history
                        generationHistory = listOf(
                            GenerationHistoryItem(
                                prompt = prompt,
                                model = selectedModel,
                                images = newImages
                            )
                        ) + generationHistory
                        

                        
                        // Save prompt to history
                        if (context != null) {
                            // PromptPreferences.addImageHistory(context, prompt)
                        }

                        errorMessage = null
                    }
                    is ImageGenerationApiClient.ImageGenResult.Error -> {
                        errorMessage = mapToUserFriendlyError(result.message)
                    }
                }
            } catch (e: Exception) {
                errorMessage = mapToUserFriendlyError(e.message ?: "Unknown error")
            } finally {
                isGenerating = false
            }
        }
    }
    
    /**
     * Load image from history
     */
    fun loadFromHistory(historyItem: GenerationHistoryItem) {
        prompt = historyItem.prompt
        selectedModel = historyItem.model
        generatedImages = historyItem.images
    }
    
    /**
     * Delete history item
     */
    fun deleteHistoryItem(id: String) {
        generationHistory = generationHistory.filter { it.id != id }
    }
    
    /**
     * Clear all generated images
     */
    fun clearImages() {
        generatedImages = emptyList()
        errorMessage = null
    }
    
    /**
     * Clear history
     */
    fun clearHistory() {
        generationHistory = emptyList()
    }
    
    /**
     * Get image dimensions from base64
     */
    fun getImageDimensions(base64Data: String): Pair<Int, Int>? {
        return try {
            val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            Pair(bitmap.width, bitmap.height)
        } catch (e: Exception) {
            null
        }
    }
}
