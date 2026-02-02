package com.example.chat_ui.ui.video

import com.example.chat_ui.api.VeoVideoClient

/**
 * State management for Generate Video Screen
 * Supports reactive UI updates with StateFlow
 */
sealed class GenerateVideoState {
    object Idle : GenerateVideoState()
    object Loading : GenerateVideoState()
    data class Polling(val jobId: String, val progress: Int = 0) : GenerateVideoState()
    data class Success(
        val result: VeoVideoClient.VideoGenerationResult
    ) : GenerateVideoState()
    data class NeedsYouTubeAuth(val authUrl: String) : GenerateVideoState()
    data class NeedsConfiguration(val message: String) : GenerateVideoState()
    data class Error(val message: String) : GenerateVideoState()
}

/**
 * UI State for video generation parameters
 */
data class VideoGenerationParams(
    val prompt: String = "",
    // Only allow selecting models that are confirmed available (exclude NOT_FOUND)
    val modelId: String = "veo-3.0-generate-001",
    val mode: VeoVideoClient.VideoMode = VeoVideoClient.VideoMode.TEXT_TO_VIDEO,
    // When generating directly via Google AI Studio (Gemini API), public/YouTube upload requires a separate backend.
    // Default to PRIVATE to avoid confusing failures in fresh installs.
    val visibility: VeoVideoClient.VideoVisibility = VeoVideoClient.VideoVisibility.PRIVATE,
    val durationSeconds: Int = 6,
    val aspectRatio: String = "16:9",
    val quality: VeoVideoClient.VideoQuality = VeoVideoClient.VideoQuality.STANDARD,
    val cinematicStyle: VeoVideoClient.CinematicStyle? = null,
    val motionLevel: VeoVideoClient.MotionLevel? = null,
    val lightingStyle: VeoVideoClient.LightingStyle? = null,
    val fps: Int? = null,
    val seed: Int? = null,
    val negativePrompt: String? = null,
    val generateAudio: Boolean = true,
    // Media data for IMAGE/VIDEO modes
    val selectedImageUri: android.net.Uri? = null,
    val selectedVideoUri: android.net.Uri? = null,
    val imageBase64: String? = null,
    val videoBase64: String? = null,
    val imageMimeType: String? = null,
    val videoMimeType: String? = null
)

/**
 * UI Settings state
 */
data class GenerateVideoUiState(
    val showAdvancedSettings: Boolean = false,
    val isGenerating: Boolean = false,
    val canGenerate: Boolean = false,
    val estimatedCost: String? = null,
    val quotaInfo: VeoVideoClient.QuotaUsage? = null
)
