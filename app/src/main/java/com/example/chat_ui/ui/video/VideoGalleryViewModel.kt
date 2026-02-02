package com.example.chat_ui.ui.video

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat_ui.api.VeoVideoClient
import com.example.chat_ui.config.ConfigManager
import com.example.chat_ui.data.ApiProvider
import com.example.chat_ui.data.firebase.FirebaseManager
import com.example.chat_ui.utils.FirebaseAuthHelper
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class VideoGalleryViewModel : ViewModel() {

    private val TAG = "VideoGalleryViewModel"

    private var generationJob: Job? = null

    // --- Generation State Management ---
    private val _state = MutableStateFlow<GenerateVideoState>(GenerateVideoState.Idle)
    val state: StateFlow<GenerateVideoState> = _state.asStateFlow()

    private val _params = MutableStateFlow(VideoGenerationParams())
    val params: StateFlow<VideoGenerationParams> = _params.asStateFlow()

    private val _uiState = MutableStateFlow(GenerateVideoUiState())
    val uiState: StateFlow<GenerateVideoUiState> = _uiState.asStateFlow()

    // Validation flow
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

    // --- Gallery State Management ---
    private val _videos = MutableLiveData<List<VeoVideoClient.VideoGenerationResult>>(emptyList())
    val videos: LiveData<List<VeoVideoClient.VideoGenerationResult>> = _videos

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorType = MutableLiveData<ErrorType?>(null)
    val errorType: LiveData<ErrorType?> = _errorType

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    private var lastDocument: com.google.firebase.firestore.DocumentSnapshot? = null
    private var hasMoreVideos = true
    private val pageSize = 10

    enum class ErrorType {
        NETWORK,
        AUTHENTICATION,
        UNKNOWN
    }

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
                            selectedVideoUri = null,
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
                            selectedImageUri = null,
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

        // Cancel any in-flight generation
        generationJob?.cancel()

        val providerConfig = ConfigManager.getProviderConfigWithApiKey(context)
        val isGoogleAiStudio = providerConfig.provider == ApiProvider.GOOGLE_AI_STUDIO

        Log.i(TAG, "Provider: ${providerConfig.provider}")
        Log.i(TAG, "API Key present: ${providerConfig.apiKey.isNotBlank()}")

        // Configuration validation
        if (isGoogleAiStudio) {
            if (providerConfig.apiKey.isBlank()) {
                _state.value =
                        GenerateVideoState.NeedsConfiguration(
                                "Google AI Studio API Key is missing. Add GOOGLE_STUDIO_API_KEY to config.properties or set it in API Settings."
                        )
                return
            }
            if (currentParams.visibility == VeoVideoClient.VideoVisibility.PUBLIC) {
                _state.value =
                        GenerateVideoState.NeedsConfiguration(
                                "Public/YouTube upload requires the Veo backend. Switch visibility to PRIVATE."
                        )
                return
            }
        }
/*
        else {
            val backendBaseUrl = ConfigManager.veoBackendBaseUrl
            if (backendBaseUrl.isBlank() || backendBaseUrl.contains("localhost")) {
                _state.value =
                        GenerateVideoState.NeedsConfiguration(
                                "Veo backend is not configured. Open API Settings and set VEO backend base URL."
                        )
                return
            }
        }
*/

        // Validation
        if (currentParams.prompt.isBlank()) {
            _state.value = GenerateVideoState.Error("Please enter a prompt")
            return
        }

        if (currentParams.prompt.length < 10) {
            _state.value = GenerateVideoState.Error("Prompt must be at least 10 characters")
            return
        }

        // Duration bounds
        if (currentParams.durationSeconds !in 4..8) {
            _state.value = GenerateVideoState.Error("Duration must be between 4 and 8 seconds")
            return
        }

        // Aspect ratio validation
        val supportedAspectRatios =
                if (isGoogleAiStudio) {
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

        // Resolution constraints
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
                // No additional validation
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

                        Log.i(
                                TAG,
                                "Starting video generation with request: " +
                                        "mode=${request.mode}, model=${request.modelId}, duration=${request.durationSeconds}s"
                        )

                        // Generate video with progress callback
                        val result =
                                VeoVideoClient.generateVideo(
                                        context = context,
                                        request = request,
                                        onProgress = { jobId, progress ->
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
        _state.value = GenerateVideoState.Idle
    }

    /** Handle YouTube OAuth completion */
    fun onYouTubeAuthCompleted(context: Context) {
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

    // --- Gallery Methods ---

    fun loadVideos(refresh: Boolean = false) {
        if (refresh) {
            lastDocument = null
            hasMoreVideos = true
            _videos.value = emptyList()
        }

        if (!hasMoreVideos && !refresh) return
        if (_isLoading.value == true) return

        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorType.value = null
                _errorMessage.value = null

                val userId = FirebaseAuthHelper.getCurrentUserUid()
                if (userId == null) {
                    _errorType.value = ErrorType.AUTHENTICATION
                    _errorMessage.value = "User not authenticated"
                    return@launch
                }

                var query =
                        FirebaseManager.firestore
                                .collection("generated_videos")
                                .whereEqualTo("userId", userId)
                                .orderBy("createdAt", Query.Direction.DESCENDING)
                                .limit(pageSize.toLong())

                if (lastDocument != null) {
                    query = query.startAfter(lastDocument!!)
                }

                val snapshot = query.get().await()

                if (!snapshot.isEmpty) {
                    lastDocument = snapshot.documents.last()
                    val newVideos =
                            snapshot.toObjects(VeoVideoClient.VideoGenerationResult::class.java)

                    val currentList = _videos.value.orEmpty().toMutableList()
                    currentList.addAll(newVideos)
                    _videos.value = currentList

                    if (snapshot.size() < pageSize) {
                        hasMoreVideos = false
                    }
                } else {
                    hasMoreVideos = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading videos", e)
                _errorType.value = ErrorType.NETWORK
                _errorMessage.value = "Failed to load videos: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadMoreVideos() {
        loadVideos(refresh = false)
    }

    fun deleteVideo(videoResult: VeoVideoClient.VideoGenerationResult) {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                // 1. Delete from Firestore
                FirebaseManager.firestore
                        .collection("generated_videos")
                        .document(videoResult.id)
                        .delete()
                        .await()

                // 2. Remove from local list
                val currentList = _videos.value.orEmpty().toMutableList()
                currentList.removeAll { it.id == videoResult.id }
                _videos.value = currentList

                // 3. Optional: Delete from Storage if it's private (handled via Cloud Functions
                // usually, or we can do it here)
                // For now, we just remove the reference.

            } catch (e: Exception) {
                Log.e(TAG, "Error deleting video", e)
                _errorMessage.value = "Failed to delete video: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorType.value = null
        _errorMessage.value = null
    }

    // Private helper methods

    private fun validateAndUpdateCost() {
        val params = _params.value

        // Calculate estimated cost
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

            // Check file size (limit to 50MB)
            if (bytes.size > 50 * 1024 * 1024) {
                throw Exception("Video file too large (max 50MB)")
            }

            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            return base64 to mimeType
        }
                ?: throw Exception("Could not read video file")
    }
}
