package com.example.chat_ui.data

import kotlinx.serialization.Serializable

/**
 * Google Gemini API Configuration
 * Comprehensive settings for Google Gemini chat and image generation
 */
@Serializable
data class GoogleGeminiConfig(
    // ===== Chat Generation Settings =====
    val temperature: Float = 1.0f,
    val maxOutputTokens: Int = 8192,
    val topP: Float = 0.95f,
    val topK: Int = 40,
    
    // ===== Thinking Configuration =====
    val thinkingEnabled: Boolean = true,
    val thinkingLevel: ThinkingLevel = ThinkingLevel.MEDIUM,
    
    // ===== Media Resolution =====
    val mediaResolution: MediaResolution = MediaResolution.HIGH,
    
    // ===== Tools =====
    val googleSearchEnabled: Boolean = false,
    val urlContextEnabled: Boolean = false,
    
    // ===== Response Format =====
    val responseMimeType: ResponseMimeType = ResponseMimeType.TEXT,
    
    // ===== Safety Settings =====
    val safetyHarassment: SafetyThreshold = SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE,
    val safetyHateSpeech: SafetyThreshold = SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE,
    val safetySexuallyExplicit: SafetyThreshold = SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE,
    val safetyDangerousContent: SafetyThreshold = SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE,
    
    // ===== Image Generation Settings =====
    val imageAspectRatio: ImageAspectRatio = ImageAspectRatio.RATIO_16_9,
    val imageSize: ImageSize = ImageSize.SIZE_4K,
    val imageResponseModalities: Boolean = true
) {
    
    enum class ThinkingLevel(val value: String, val displayName: String) {
        LOW("LOW", "منخفض / Low"),
        MEDIUM("MEDIUM", "متوسط / Medium"),
        HIGH("HIGH", "عالي / High");
        
        companion object {
            fun fromValue(value: String): ThinkingLevel {
                return entries.find { it.value == value } ?: MEDIUM
            }
        }
    }
    
    enum class MediaResolution(val value: String, val displayName: String) {
        LOW("MEDIA_RESOLUTION_LOW", "منخفض / Low"),
        MEDIUM("MEDIA_RESOLUTION_MEDIUM", "متوسط / Medium"),
        HIGH("MEDIA_RESOLUTION_HIGH", "عالي / High");
        
        companion object {
            fun fromValue(value: String): MediaResolution {
                return entries.find { it.value == value } ?: HIGH
            }
        }
    }
    
    enum class SafetyThreshold(val value: String, val displayName: String) {
        BLOCK_NONE("BLOCK_NONE", "لا تحظر / Block None"),
        BLOCK_ONLY_HIGH("BLOCK_ONLY_HIGH", "حظر العالي فقط / Block High Only"),
        BLOCK_MEDIUM_AND_ABOVE("BLOCK_MEDIUM_AND_ABOVE", "حظر المتوسط وما فوق / Block Medium+"),
        BLOCK_LOW_AND_ABOVE("BLOCK_LOW_AND_ABOVE", "حظر المنخفض وما فوق / Block Low+");
        
        companion object {
            fun fromValue(value: String): SafetyThreshold {
                return entries.find { it.value == value } ?: BLOCK_MEDIUM_AND_ABOVE
            }
        }
    }
    
    enum class ResponseMimeType(val value: String, val displayName: String) {
        TEXT("text/plain", "نص عادي / Plain Text"),
        JSON("application/json", "JSON"),
        MARKDOWN("text/markdown", "Markdown");
        
        companion object {
            fun fromValue(value: String): ResponseMimeType {
                return entries.find { it.value == value } ?: TEXT
            }
        }
    }
    
    enum class ImageAspectRatio(val value: String, val displayName: String) {
        RATIO_1_1("1:1", "مربع / Square (1:1)"),
        RATIO_4_3("4:3", "قياسي / Standard (4:3)"),
        RATIO_16_9("16:9", "عريض / Wide (16:9)"),
        RATIO_9_16("9:16", "عمودي / Portrait (9:16)");
        
        companion object {
            fun fromValue(value: String): ImageAspectRatio {
                return entries.find { it.value == value } ?: RATIO_16_9
            }
        }
    }
    
    enum class ImageSize(val value: String, val displayName: String) {
        SIZE_SD("SD", "SD (480p)"),
        SIZE_HD("HD", "HD (720p)"),
        SIZE_FULL_HD("FULL_HD", "Full HD (1080p)"),
        SIZE_4K("4K", "4K (2160p)");
        
        companion object {
            fun fromValue(value: String): ImageSize {
                return entries.find { it.value == value } ?: SIZE_4K
            }
        }
    }
    
    companion object {
        /**
         * Default configuration
         */
        fun default() = GoogleGeminiConfig()
        
        /**
         * Validate configuration values
         */
        fun GoogleGeminiConfig.validate(): GoogleGeminiConfig {
            return copy(
                temperature = temperature.coerceIn(0f, 2f),
                maxOutputTokens = maxOutputTokens.coerceIn(1, 65536),
                topP = topP.coerceIn(0f, 1f),
                topK = topK.coerceIn(1, 100)
            )
        }
    }
}
