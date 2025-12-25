package com.example.chat_ui.data

/**
 * Google Vertex AI Models Catalog
 * 
 * Since Google Vertex AI doesn't provide a /models endpoint,
 * we maintain a static catalog of available models.
 */
object GoogleModels {
    
    data class GoogleModel(
        val id: String,
        val displayName: String,
        val description: String,
        val multimodal: Boolean,
        val supportsTools: Boolean,
        val type: ModelType = ModelType.CHAT
    )
    
    enum class ModelType {
        CHAT,           // Gemini chat models
        IMAGE,          // Imagen image generation
        VIDEO,          // Veo video generation
        EMBEDDING       // Embedding models
    }
    
    val AVAILABLE_MODELS = listOf(
        // ========== GEMINI CHAT MODELS ==========
        // Gemini 3 Series (Preview)
        GoogleModel(
            id = "google/gemini-3-pro-preview",
            displayName = "Gemini 3 Pro (Preview)",
            description = "Next-gen most advanced model",
            multimodal = true,
            supportsTools = true,
            type = ModelType.CHAT
        ),
        GoogleModel(
            id = "google/gemini-3-flash-preview",
            displayName = "Gemini 3 Flash (Preview)",
            description = "Next-gen fast model",
            multimodal = true,
            supportsTools = true,
            type = ModelType.CHAT
        ),
        GoogleModel(
            id = "google/gemini-3-pro-image-preview",
            displayName = "Gemini 3 Pro Image (Preview)",
            description = "Next-gen with enhanced image understanding",
            multimodal = true,
            supportsTools = true,
            type = ModelType.CHAT
        ),
        
        // Gemini 2.5 Series (Latest Stable)
        GoogleModel(
            id = "google/gemini-2.5-pro",
            displayName = "Gemini 2.5 Pro",
            description = "Most advanced stable model",
            multimodal = true,
            supportsTools = true,
            type = ModelType.CHAT
        ),
        GoogleModel(
            id = "google/gemini-2.5-flash",
            displayName = "Gemini 2.5 Flash",
            description = "Fast and efficient (recommended)",
            multimodal = true,
            supportsTools = true,
            type = ModelType.CHAT
        ),
        GoogleModel(
            id = "google/gemini-2.5-flash-lite",
            displayName = "Gemini 2.5 Flash Lite",
            description = "Lightweight for quick tasks",
            multimodal = true,
            supportsTools = true,
            type = ModelType.CHAT
        ),
        GoogleModel(
            id = "google/gemini-2.5-flash-image",
            displayName = "Gemini 2.5 Flash Image",
            description = "Enhanced image understanding",
            multimodal = true,
            supportsTools = true,
            type = ModelType.CHAT
        ),
        GoogleModel(
            id = "google/gemini-2.5-flash-image-preview",
            displayName = "Gemini 2.5 Flash Image (Preview)",
            description = "Preview with latest image features",
            multimodal = true,
            supportsTools = true,
            type = ModelType.CHAT
        ),
        GoogleModel(
            id = "google/gemini-2.5-flash-preview-09-2025",
            displayName = "Gemini 2.5 Flash Preview (Sep 2025)",
            description = "September preview release",
            multimodal = true,
            supportsTools = true,
            type = ModelType.CHAT
        ),
        GoogleModel(
            id = "google/gemini-2.5-flash-lite-preview-09-2025",
            displayName = "Gemini 2.5 Flash Lite Preview (Sep 2025)",
            description = "Lightweight September preview",
            multimodal = true,
            supportsTools = true,
            type = ModelType.CHAT
        ),
        GoogleModel(
            id = "google/gemini-2.5-computer-use-preview-10-2025",
            displayName = "Gemini 2.5 Computer Use (Preview)",
            description = "Computer interaction capabilities",
            multimodal = true,
            supportsTools = true,
            type = ModelType.CHAT
        ),
        
        // Gemini 2.0 Series
        GoogleModel(
            id = "google/gemini-2.0-flash-001",
            displayName = "Gemini 2.0 Flash",
            description = "Fast and efficient model for most tasks",
            multimodal = true,
            supportsTools = true
        ),
        GoogleModel(
            id = "google/gemini-2.0-flash-lite-001",
            displayName = "Gemini 2.0 Flash Lite",
            description = "Lightweight and fast",
            multimodal = true,
            supportsTools = true,
            type = ModelType.CHAT
        ),
        
        // Gemini 1.5 Series
        GoogleModel(
            id = "google/gemini-1.5-pro-002",
            displayName = "Gemini 1.5 Pro",
            description = "Advanced model with extended context",
            multimodal = true,
            supportsTools = true,
            type = ModelType.CHAT
        ),
        GoogleModel(
            id = "google/gemini-1.5-flash-002",
            displayName = "Gemini 1.5 Flash",
            description = "Balanced performance and speed",
            multimodal = true,
            supportsTools = true,
            type = ModelType.CHAT
        ),
        
        // Embedding Model
        GoogleModel(
            id = "google/gemini-embedding-001",
            displayName = "Gemini Embedding",
            description = "Text embedding model",
            multimodal = false,
            supportsTools = false,
            type = ModelType.EMBEDDING
        ),
        
        // ========== VEO VIDEO GENERATION MODELS ==========
        // Veo 3.1 Series (Latest)
        GoogleModel(
            id = "google/veo-3.1-generate-001",
            displayName = "Veo 3.1",
            description = "Latest video generation model",
            multimodal = true,
            supportsTools = false,
            type = ModelType.VIDEO
        ),
        GoogleModel(
            id = "google/veo-3.1-fast-generate-001",
            displayName = "Veo 3.1 Fast",
            description = "Fast video generation",
            multimodal = true,
            supportsTools = false,
            type = ModelType.VIDEO
        ),
        GoogleModel(
            id = "google/veo-3.1-generate-preview",
            displayName = "Veo 3.1 (Preview)",
            description = "Preview version of Veo 3.1",
            multimodal = true,
            supportsTools = false,
            type = ModelType.VIDEO
        ),
        GoogleModel(
            id = "google/veo-3.1-fast-generate-preview",
            displayName = "Veo 3.1 Fast (Preview)",
            description = "Fast preview version",
            multimodal = true,
            supportsTools = false,
            type = ModelType.VIDEO
        ),
        
        // Veo 3.0 Series
        GoogleModel(
            id = "google/veo-3.0-generate-001",
            displayName = "Veo 3.0",
            description = "High-quality video generation",
            multimodal = true,
            supportsTools = false,
            type = ModelType.VIDEO
        ),
        GoogleModel(
            id = "google/veo-3.0-fast-generate-001",
            displayName = "Veo 3.0 Fast",
            description = "Faster video generation",
            multimodal = true,
            supportsTools = false,
            type = ModelType.VIDEO
        ),
        GoogleModel(
            id = "google/veo-3.0-generate-preview",
            displayName = "Veo 3.0 (Preview)",
            description = "Preview of Veo 3.0",
            multimodal = true,
            supportsTools = false,
            type = ModelType.VIDEO
        ),
        GoogleModel(
            id = "google/veo-3.0-fast-generate-preview",
            displayName = "Veo 3.0 Fast (Preview)",
            description = "Fast preview version",
            multimodal = true,
            supportsTools = false,
            type = ModelType.VIDEO
        ),
        
        // Veo 2.0 Series
        GoogleModel(
            id = "google/veo-2.0-generate-001",
            displayName = "Veo 2.0",
            description = "Stable video generation model",
            multimodal = true,
            supportsTools = false,
            type = ModelType.VIDEO
        ),
        
        // ========== IMAGEN IMAGE GENERATION MODELS ==========
        // Imagen 4 Series (Latest)
        GoogleModel(
            id = "google/imagen-4.0-generate-001",
            displayName = "Imagen 4.0",
            description = "Latest image generation model",
            multimodal = true,
            supportsTools = false,
            type = ModelType.IMAGE
        ),
        GoogleModel(
            id = "google/imagen-4.0-fast-generate-001",
            displayName = "Imagen 4.0 Fast",
            description = "Fast image generation",
            multimodal = true,
            supportsTools = false,
            type = ModelType.IMAGE
        ),
        GoogleModel(
            id = "google/imagen-4.0-ultra-generate-001",
            displayName = "Imagen 4.0 Ultra",
            description = "Ultra high-quality images",
            multimodal = true,
            supportsTools = false,
            type = ModelType.IMAGE
        ),
        GoogleModel(
            id = "google/imagen-4.0-generate-preview-06-06",
            displayName = "Imagen 4.0 (Preview June)",
            description = "June preview release",
            multimodal = true,
            supportsTools = false,
            type = ModelType.IMAGE
        ),
        GoogleModel(
            id = "google/imagen-4.0-fast-generate-preview-06-06",
            displayName = "Imagen 4.0 Fast (Preview June)",
            description = "Fast June preview",
            multimodal = true,
            supportsTools = false,
            type = ModelType.IMAGE
        ),
        GoogleModel(
            id = "google/imagen-4.0-ultra-generate-preview-06-06",
            displayName = "Imagen 4.0 Ultra (Preview June)",
            description = "Ultra June preview",
            multimodal = true,
            supportsTools = false,
            type = ModelType.IMAGE
        ),
        
        // Imagen 3 Series
        GoogleModel(
            id = "google/imagen-3.0-generate-002",
            displayName = "Imagen 3.0",
            description = "High-quality image generation",
            multimodal = true,
            supportsTools = false,
            type = ModelType.IMAGE
        ),
        GoogleModel(
            id = "google/imagen-3.0-capability-001",
            displayName = "Imagen 3.0 Capability 001",
            description = "Enhanced capabilities version",
            multimodal = true,
            supportsTools = false,
            type = ModelType.IMAGE
        ),
        GoogleModel(
            id = "google/imagen-3.0-capability-002",
            displayName = "Imagen 3.0 Capability 002",
            description = "Latest capabilities version",
            multimodal = true,
            supportsTools = false,
            type = ModelType.IMAGE
        ),
        
        // Imagen Special Models
        GoogleModel(
            id = "google/imagen-product-recontext-preview-06-30",
            displayName = "Imagen Product Recontext (Preview)",
            description = "Product recontextualization",
            multimodal = true,
            supportsTools = false,
            type = ModelType.IMAGE
        )
    )
    
    /**
     * Get default model for Google Vertex AI
     */
    fun getDefaultModel(): String = "google/gemini-2.0-flash-001"
    
    /**
     * Check if model ID is valid Google model
     */
    fun isValidModel(modelId: String): Boolean {
        return AVAILABLE_MODELS.any { it.id == modelId }
    }
    
    /**
     * Get stable models only (exclude experimental, preview, embedding)
     * These are the models suitable for chat
     */
    fun getStableChatModels(): List<GoogleModel> {
        return AVAILABLE_MODELS.filter { model ->
            val id = model.id.lowercase()
            // Exclude experimental, preview, and embedding models
            !id.contains("exp") &&
            !id.contains("preview") &&
            !id.contains("embedding") &&
            !id.contains("lite")  // Lite models are less capable
        }
    }
    
    /**
     * Filter models by category
     */
    object ModelCategory {
        // Recommended for chat
        val RECOMMENDED = listOf(
            "google/gemini-2.5-flash",
            "google/gemini-2.0-flash-001",
            "google/gemini-1.5-flash-002"
        )
        
        // Pro models (higher quality, slower)
        val PRO = listOf(
            "google/gemini-2.5-pro",
            "google/gemini-1.5-pro-002"
        )
        
        // Fast models (lower quality, faster)
        val FAST = listOf(
            "google/gemini-2.5-flash-lite",
            "google/gemini-2.0-flash-lite-001"
        )
    }
}
