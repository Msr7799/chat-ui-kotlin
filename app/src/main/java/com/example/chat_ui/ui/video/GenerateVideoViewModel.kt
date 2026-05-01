package com.example.chat_ui.ui.video

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat_ui.api.VeoVideoClient
import com.example.chat_ui.config.ConfigManager
import com.example.chat_ui.data.ApiProvider
import com.example.chat_ui.utils.PromptPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for Generate Video Screen
 *
 * Features:
 * - Reactive UI state management with StateFlow
 * - Public/Private video generation support
 * - YouTube OAuth flow handling
 * - Media picker integration (Image/Video)
 * - Advanced settings management
 * - Progress tracking with job polling
 */
class GenerateVideoViewModel : ViewModel() {

    private val TAG = "GenerateVideoViewModel"

    private var generationJob: Job? = null

    // State management
    private val _state = MutableStateFlow<GenerateVideoState>(GenerateVideoState.Idle)
    val state: StateFlow<GenerateVideoState> = _state.asStateFlow()

    private val _params = MutableStateFlow(VideoGenerationParams())
    val params: StateFlow<VideoGenerationParams> = _params.asStateFlow()

    private val _uiState = MutableStateFlow(GenerateVideoUiState())
    val uiState: StateFlow<GenerateVideoUiState> = _uiState.asStateFlow()

    // Combine states for validation
    val canGenerate: StateFlow<Boolean> =
            combine(_params, _state) { params: VideoGenerationParams, state: GenerateVideoState ->
                        state !is GenerateVideoState.Loading &&
                                state !is GenerateVideoState.Polling &&
                                params.prompt.isNotBlank() &&
                                params.prompt.length >= 10 &&
                                when (params.mode) {
                                    VeoVideoClient.VideoMode.TEXT_TO_VIDEO -> true
                                    VeoVideoClient.VideoMode.IMAGE_TO_VIDEO ->
                                            params.selectedImageUri != null
                                    VeoVideoClient.VideoMode.VIDEO_TO_VIDEO ->
                                            params.selectedVideoUri != null
                                }
                    }
                    .stateIn(viewModelScope, SharingStarted.Lazily, false)

    /** Update video generation parameters */
    fun updateParams(update: VideoGenerationParams.() -> VideoGenerationParams) {
        _params.value = _params.value.update()
        validateAndUpdateCost()
    }

    /** Toggle advanced settings visibility */
    fun toggleAdvancedSettings() {
        _uiState.value =
                _uiState.value.copy(showAdvancedSettings = !_uiState.value.showAdvancedSettings)
    }

    /** Set selected image for IMAGE_TO_VIDEO mode */
    fun setSelectedImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val (base64, mimeType) = processImageUri(context, uri)
                updateParams {
                    copy(
                            selectedImageUri = uri,
                            imageBase64 = base64,
                            imageMimeType = mimeType,
                            selectedVideoUri = null, // Clear video if image selected
                            videoBase64 = null,
                            videoMimeType = null
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process image", e)
                _state.value = GenerateVideoState.Error("Failed to process image: ${e.message}")
            }
        }
    }

    /** Set selected video for VIDEO_TO_VIDEO mode */
    fun setSelectedVideo(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val (base64, mimeType) = processVideoUri(context, uri)
                updateParams {
                    copy(
                            selectedVideoUri = uri,
                            videoBase64 = base64,
                            videoMimeType = mimeType,
                            selectedImageUri = null, // Clear image if video selected
                            imageBase64 = null,
                            imageMimeType = null
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process video", e)
                _state.value = GenerateVideoState.Error("Failed to process video: ${e.message}")
            }
        }
    }

    /** Generate video with current parameters */
    fun generateVideo(context: Context) {
        val currentParams = _params.value

        // Cancel any in-flight generation before starting a new one
        generationJob?.cancel()

        // Configuration validation
        val providerConfig = ConfigManager.getProviderConfigWithApiKey(context)
        val isGoogleAiStudio = providerConfig.provider == ApiProvider.GOOGLE_AI_STUDIO

        // Google AI Studio (Gemini API) runs without the custom backend for PRIVATE generation.
        if (isGoogleAiStudio) {
            if (providerConfig.apiKey.isBlank()) {
                _state.value =
                        GenerateVideoState.NeedsConfiguration(
                                "Google AI Studio API Key is missing. Open API Settings and set the API key."
                        )
                return
            }
        }

        // Validation
        if (currentParams.prompt.isBlank()) {
            _state.value = GenerateVideoState.Error("Please enter a prompt")
            return
        }

        if (currentParams.prompt.length < 10) {
            _state.value = GenerateVideoState.Error("Prompt must be at least 10 characters")
            return
        }

        // Duration bounds (VeoVideoClient clamps, but we want explicit UX feedback)
        if (currentParams.durationSeconds !in 4..8) {
            _state.value = GenerateVideoState.Error("Duration must be between 4 and 8 seconds")
            return
        }
        // Aspect ratio validation
        val supportedAspectRatios =
                if (isGoogleAiStudio) {
                    // Gemini API Veo supports 16:9 and 9:16.
                    setOf("16:9", "9:16")
                } else {
                    setOf("16:9", "9:16", "1:1")
                }
        if (!supportedAspectRatios.contains(currentParams.aspectRatio)) {
            _state.value =
                    GenerateVideoState.Error(
                            "Unsupported aspect ratio: ${currentParams.aspectRatio}"
                    )
            return
        }

        // Resolution constraints (Gemini API / Veo): 1080p requires 8s. For Veo 3.0, 1080p is 16:9
        // only.
        val is1080pQuality = currentParams.quality == VeoVideoClient.VideoQuality.ULTRA
        if (is1080pQuality) {
            if (currentParams.durationSeconds != 8) {
                _state.value = GenerateVideoState.Error("1080p requires 8 seconds duration")
                return
            }
            if (isGoogleAiStudio &&
                            currentParams.modelId.startsWith("veo-3.0") &&
                            currentParams.aspectRatio != "16:9"
            ) {
                _state.value = GenerateVideoState.Error("Veo 3.0 in 1080p supports 16:9 only")
                return
            }
        }

        // Mode-specific validation
        when (currentParams.mode) {
            VeoVideoClient.VideoMode.IMAGE_TO_VIDEO -> {
                if (currentParams.imageBase64 == null) {
                    _state.value = GenerateVideoState.Error("Please select an image")
                    return
                }
            }
            VeoVideoClient.VideoMode.VIDEO_TO_VIDEO -> {
                if (currentParams.videoBase64 == null) {
                    _state.value = GenerateVideoState.Error("Please select a video")
                    return
                }
            }
            VeoVideoClient.VideoMode.TEXT_TO_VIDEO -> {
                // No additional validation needed
            }
        }

        generationJob =
                viewModelScope.launch {
                    try {
                        _uiState.value = _uiState.value.copy(isGenerating = true)
                        _state.value = GenerateVideoState.Loading

                        // Create unified request
                        val request =
                                VeoVideoClient.GenerateVideoRequest(
                                        prompt = currentParams.prompt.trim(),
                                        modelId = currentParams.modelId,
                                        mode = currentParams.mode,
                                        visibility = currentParams.visibility,
                                        durationSeconds = currentParams.durationSeconds,
                                        aspectRatio = currentParams.aspectRatio,
                                        fps = currentParams.fps,
                                        quality = currentParams.quality,
                                        cinematicStyle = currentParams.cinematicStyle,
                                        motionLevel = currentParams.motionLevel,
                                        lightingStyle = currentParams.lightingStyle,
                                        seed = currentParams.seed,
                                        negativePrompt = currentParams.negativePrompt,
                                        generateAudio = currentParams.generateAudio,
                                        imageBase64 = currentParams.imageBase64,
                                        imageMimeType = currentParams.imageMimeType,
                                        videoBase64 = currentParams.videoBase64,
                                        videoMimeType = currentParams.videoMimeType
                                )

                        // Generate video (with progress callback)
                        val result =
                                VeoVideoClient.generateVideo(
                                        context = context,
                                        request = request,
                                        onProgress = { jobId, progress ->
                                            // Update UI with backend-reported progress
                                            // (best-effort)
                                            _state.value =
                                                    GenerateVideoState.Polling(
                                                            jobId = jobId,
                                                            progress = progress
                                                    )
                                        }
                                )

                        when (result) {
                            is VeoVideoClient.VeoApiResult.Success -> {
                                _state.value = GenerateVideoState.Success(result.data)
                                Log.i(TAG, "Video generation completed: ${result.data.url}")
                                // Save prompt to history
                                // PromptPreferences.addVideoHistory(context, currentParams.prompt)
                            }
                            is VeoVideoClient.VeoApiResult.RequiresYouTubeAuth -> {
                                _state.value = GenerateVideoState.NeedsYouTubeAuth(result.authUrl)
                                Log.i(TAG, "YouTube authentication required: ${result.authUrl}")
                            }
                            is VeoVideoClient.VeoApiResult.Error -> {
                                _state.value = GenerateVideoState.Error(result.message)
                                Log.e(TAG, "Video generation failed: ${result.message}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Video generation exception", e)
                        _state.value =
                                GenerateVideoState.Error(e.message ?: "Unknown error occurred")
                    } finally {
                        _uiState.value = _uiState.value.copy(isGenerating = false)
                        generationJob = null
                    }
                }
    }

    fun cancelGeneration() {
        generationJob?.cancel()
        generationJob = null
        _uiState.value = _uiState.value.copy(isGenerating = false)
        // Keep current params so user can retry quickly
        _state.value = GenerateVideoState.Idle
    }

    /** Handle YouTube OAuth completion */
    fun onYouTubeAuthCompleted(context: Context) {
        // Retry generation after successful auth
        generateVideo(context)
    }

    /** Reset state to idle */
    fun resetState() {
        _state.value = GenerateVideoState.Idle
    }

    /** Clear current generation and reset */
    fun clearGeneration() {
        generationJob?.cancel()
        generationJob = null
        updateParams { VideoGenerationParams() }
        _state.value = GenerateVideoState.Idle
        _uiState.value = GenerateVideoUiState()
    }

    // Private helper methods

    private fun validateAndUpdateCost() {
        val params = _params.value

        // Calculate estimated cost based on parameters
        val baseCost =
                when (params.quality) {
                    VeoVideoClient.VideoQuality.STANDARD -> 1.0f
                    VeoVideoClient.VideoQuality.HIGH -> 2.0f
                    VeoVideoClient.VideoQuality.ULTRA -> 4.0f
                }

        val durationMultiplier = params.durationSeconds / 6.0f
        val modeMultiplier =
                when (params.mode) {
                    VeoVideoClient.VideoMode.TEXT_TO_VIDEO -> 1.0f
                    VeoVideoClient.VideoMode.IMAGE_TO_VIDEO -> 1.2f
                    VeoVideoClient.VideoMode.VIDEO_TO_VIDEO -> 1.5f
                }

        val estimatedCost =
                (baseCost * durationMultiplier * modeMultiplier).let {
                    when {
                        it < 1.0f -> "Low cost"
                        it < 2.0f -> "Medium cost"
                        else -> "High cost"
                    }
                }

        _uiState.value =
                _uiState.value.copy(canGenerate = canGenerate.value, estimatedCost = estimatedCost)
    }

    /**
     * Enhance the current prompt using AI
     */
    fun enhancePrompt(prompt: String, onResult: (String) -> Unit) {
        if (prompt.isBlank()) return
        
        viewModelScope.launch {
            try {
                val enhanced = VeoVideoClient.enhancePrompt(prompt)
                onResult(enhanced)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enhance prompt", e)
                onResult(prompt) // Fallback
            }
        }
    }

    private suspend fun processImageUri(context: Context, uri: Uri): Pair<String, String> {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri) ?: "image/jpeg"

        contentResolver.openInputStream(uri)?.use { inputStream ->
            val bytes = inputStream.readBytes()
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            return base64 to mimeType
        }
                ?: throw Exception("Could not read image file")
    }

    private suspend fun processVideoUri(context: Context, uri: Uri): Pair<String, String> {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri) ?: "video/mp4"

        contentResolver.openInputStream(uri)?.use { inputStream ->
            val bytes = inputStream.readBytes()

            // Check file size (limit to 50MB for video-to-video)
            if (bytes.size > 50 * 1024 * 1024) {
                throw Exception("Video file too large (max 50MB)")
            }

            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            return base64 to mimeType
        }
                ?: throw Exception("Could not read video file")
    }
}
