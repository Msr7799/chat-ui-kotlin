package com.example.chat_ui.api

import android.content.Context
import android.util.Log
import com.example.chat_ui.config.ConfigManager
import com.example.chat_ui.data.ApiProvider
import com.example.chat_ui.data.ProviderConfig
import com.example.chat_ui.data.firebase.FirestoreManager
import com.example.chat_ui.data.firebase.FirebaseManager
import com.example.chat_ui.utils.FirebaseAuthHelper
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.Serializable
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Veo Video Generation Client
 * 
 * Handles video generation workflow:
 * 1. Call Veo Backend API with Firebase Auth
 * 2. Poll job status until completion
 * 3. Download video from signed URL
 * 4. Upload to Firebase Storage (private) or YouTube (public)
 * 5. Save metadata to Firestore
 * 
 * Similar pattern to ImageGenerationClient but for video
 */
object VeoVideoClient {
    private const val TAG = "VeoVideoClient"
    // Local cache for jobs started via direct Google AI Studio calls.
    // We keep a mapping from our synthetic jobId -> operation name so the existing polling loop can work.
    private val localGoogleOperations: MutableMap<String, String> = mutableMapOf()
    private val localJobStatusCache: MutableMap<String, JobStatusResponse> = mutableMapOf()

    private suspend fun getFirebaseTokenWithRetry(): String? {
        val token = FirebaseAuthHelper.getFirebaseIdToken(forceRefresh = false)
        if (token != null) return token
        return FirebaseAuthHelper.getFirebaseIdToken(forceRefresh = true)
    }

    private fun sanitizeDurationSeconds(durationSeconds: Int): Int {
        return durationSeconds.coerceIn(4, 8)
    }

    private fun sanitizeFps(fps: Int?): Int? {
        return when (fps) {
            24, 30 -> fps
            else -> null
        }
    }

    private fun toBackendQuality(quality: VideoQuality): String {
        return when (quality) {
            VideoQuality.STANDARD -> "standard"
            VideoQuality.HIGH -> "high"
            VideoQuality.ULTRA -> "high"
        }
    }

    private fun toBackendLighting(lighting: LightingStyle?): String? {
        return when (lighting) {
            null -> null
            LightingStyle.NATURAL -> "natural"
            LightingStyle.DRAMATIC -> "dramatic"
            LightingStyle.SOFT -> "soft"
            LightingStyle.GOLDEN_HOUR -> null
        }
    }

    private fun toBackendCameraStyle(style: CinematicStyle?): String? {
        return when (style) {
            null -> null
            CinematicStyle.CINEMATIC -> "cinematic"
            CinematicStyle.DOCUMENTARY -> "documentary"
            CinematicStyle.NATURAL, CinematicStyle.ARTISTIC -> null
        }
    }
    
    // Video visibility options
    enum class VideoVisibility {
        PRIVATE,    // Firebase Storage
        PUBLIC      // YouTube
    }
    
    // Supported video modes
    enum class VideoMode(val apiPath: String, val displayName: String) {
        TEXT_TO_VIDEO("text", "Text to Video"),
        IMAGE_TO_VIDEO("image", "Image to Video"),
        VIDEO_TO_VIDEO("video", "Video to Video")
    }
    
    // Job status from backend
    enum class JobStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        UNKNOWN
    }
    
    // Video quality levels
    enum class VideoQuality(val value: String, val description: String) {
        STANDARD("standard", "720p, 24fps - Fast generation"),
        HIGH("high", "1080p, 30fps - Balanced quality"),
        ULTRA("ultra", "1080p, 60fps - Best quality, slower")
    }
    
    // Cinematic styles
    enum class CinematicStyle(val value: String, val description: String) {
        NATURAL("natural", "Natural, realistic look"),
        CINEMATIC("cinematic", "Movie-like lighting and composition"),
        ARTISTIC("artistic", "Creative and stylized"),
        DOCUMENTARY("documentary", "Documentary-style realism")
    }
    
    // Motion intensity levels
    enum class MotionLevel(val value: String, val description: String) {
        LOW("low", "Minimal camera movement"),
        MEDIUM("medium", "Moderate motion and transitions"),
        HIGH("high", "Dynamic camera movements")
    }
    
    // Lighting styles
    enum class LightingStyle(val value: String, val description: String) {
        NATURAL("natural", "Natural ambient lighting"),
        DRAMATIC("dramatic", "High contrast, dramatic shadows"),
        SOFT("soft", "Soft, diffused lighting"),
        GOLDEN_HOUR("golden_hour", "Warm, golden lighting")
    }
    
    // Request parameters for text-to-video
    data class TextToVideoParams(
        val prompt: String,
        val modelId: String = "veo-3.0-generate-001",
        val durationSeconds: Int = 6,
        val aspectRatio: String = "16:9",
        val quality: VideoQuality = VideoQuality.STANDARD,
        val cinematicStyle: CinematicStyle? = null,
        val motionLevel: MotionLevel? = null,
        val lightingStyle: LightingStyle? = null,
        val fps: Int? = null,
        val seed: Int? = null,
        val negativePrompt: String? = null,
        val generateAudio: Boolean = true
    )
    
    // Request parameters for image-to-video
    data class ImageToVideoParams(
        val prompt: String,
        val modelId: String = "veo-3.0-generate-001",
        val imageBase64: String,
        val imageMimeType: String,
        val durationSeconds: Int = 4,
        val aspectRatio: String = "16:9",
        val quality: VideoQuality = VideoQuality.STANDARD,
        val motionLevel: MotionLevel? = null,
        val seed: Int? = null
    )
    
    // Request parameters for video-to-video
    data class VideoToVideoParams(
        val prompt: String,
        val modelId: String = "veo-3.0-generate-001",
        val videoBase64: String,
        val videoMimeType: String,
        val durationSeconds: Int = 6,
        val aspectRatio: String = "16:9",
        val quality: VideoQuality = VideoQuality.STANDARD,
        val motionLevel: MotionLevel? = null,
        val lightingStyle: LightingStyle? = null,
        val strength: Float = 0.7f, // How much to transform (0.1 = subtle, 0.9 = major changes)
        val seed: Int? = null
    )
    
    // Unified request for all video generation modes
    data class GenerateVideoRequest(
        val prompt: String,
        // Video generation model to use (must be available; exclude NOT_FOUND)
        val modelId: String = "veo-3.0-generate-001",
        val mode: VideoMode,
        val visibility: VideoVisibility,
        val durationSeconds: Int,
        val aspectRatio: String = "16:9",
        val fps: Int? = null,
        val quality: VideoQuality = VideoQuality.STANDARD,
        val cinematicStyle: CinematicStyle? = null,
        val motionLevel: MotionLevel? = null,
        val lightingStyle: LightingStyle? = null,
        val seed: Int? = null,
        val negativePrompt: String? = null,
        val generateAudio: Boolean = true,
        // Mode-specific data
        val imageBase64: String? = null,
        val imageMimeType: String? = null,
        val videoBase64: String? = null,
        val videoMimeType: String? = null,
        val strength: Float = 0.7f // For video-to-video transformation
    )
    
    // API response for job creation
    data class VideoJobResponse(
        val jobId: String,
        val status: JobStatus,
        val mode: VideoMode,
        val message: String,
        val quota: QuotaUsage
    )
    
    // Quota usage information
    data class QuotaUsage(
        val used: Int,
        val limit: Int,
        val remaining: Int
    )
    
    // Job status response
    data class JobStatusResponse(
        val jobId: String,
        val status: JobStatus,
        val progress: Int? = null,
        val error: String? = null,
        val signedUrl: String? = null,
        val gcsPath: String? = null,
        val completedAt: String? = null
    )
    
    // Final generation result
    data class VideoGenerationResult(
        val id: String,
        val url: String,          // Final storage URL (Firebase Storage or YouTube)
        val prompt: String,
        val visibility: VideoVisibility,
        val duration: Int,
        val aspectRatio: String,
        val createdAt: Long,
        val jobId: String
    ) : Serializable
    
    sealed class VeoApiResult<out T> {
        data class Success<T>(val data: T) : VeoApiResult<T>()
        data class Error(val message: String, val code: Int? = null) : VeoApiResult<Nothing>()
        data class RequiresYouTubeAuth(val authUrl: String) : VeoApiResult<Nothing>()
    }
    
    /**
     * Generate video from text prompt
     */
    suspend fun generateTextToVideo(
        context: Context,
        params: TextToVideoParams,
        visibility: VideoVisibility = VideoVisibility.PUBLIC
    ): VeoApiResult<VideoGenerationResult> = withContext(Dispatchers.IO) {
        try {
            // Step 1: Start video generation job
            val jobResult = startVideoGeneration(VideoMode.TEXT_TO_VIDEO, params, null, null)
            if (jobResult !is VeoApiResult.Success) {
                return@withContext VeoApiResult.Error(jobResult.toString())
            }
            
            Log.i(TAG, "Text-to-video job started: ${jobResult.data.jobId}")
            
            // Step 2: Poll until completion
            val completedJob = pollJobCompletion(jobResult.data.jobId)
            if (completedJob !is VeoApiResult.Success) {
                return@withContext VeoApiResult.Error(completedJob.toString())
            }
            
            if (completedJob.data.signedUrl == null) {
                return@withContext VeoApiResult.Error("No video URL returned")
            }
            
            Log.i(TAG, "Video generation completed, downloading...")
            
            // Step 3: Download video from signed URL
            val videoBytes = downloadVideo(completedJob.data.signedUrl)
            
            // Step 4: Save to temporary file
            val tempFile = File(context.cacheDir, "veo_video_${System.currentTimeMillis()}.mp4")
            FileOutputStream(tempFile).use { it.write(videoBytes) }
            
            Log.i(TAG, "Video downloaded, size: ${videoBytes.size} bytes")
            
            // Step 5: Upload to final destination
            val finalUrl = when (visibility) {
                VideoVisibility.PRIVATE -> uploadToFirebaseStorage(tempFile, params.prompt)
                VideoVisibility.PUBLIC -> uploadToYouTube(tempFile, params.prompt, context)
            }
            
            Log.i(TAG, "Video uploaded to ${visibility.name}: $finalUrl")
            
            // Step 6: Save metadata to Firestore
            val videoId = UUID.randomUUID().toString()
            val result = VideoGenerationResult(
                id = videoId,
                url = finalUrl,
                prompt = params.prompt.trim(),
                visibility = visibility,
                duration = params.durationSeconds,
                aspectRatio = params.aspectRatio,
                createdAt = System.currentTimeMillis(),
                jobId = jobResult.data.jobId
            )
            
            saveVideoMetadata(result)
            
            VeoApiResult.Success(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Video generation failed", e)
            VeoApiResult.Error(e.message ?: "Unknown error occurred")
        }
    }
    
    /**
     * Generate video from image + text prompt
     */
    suspend fun generateImageToVideo(
        context: Context,
        params: ImageToVideoParams,
        visibility: VideoVisibility = VideoVisibility.PUBLIC
    ): VeoApiResult<VideoGenerationResult> = withContext(Dispatchers.IO) {
        try {
            // Similar workflow but with image parameters
            val jobResult = startVideoGeneration(VideoMode.IMAGE_TO_VIDEO, null, params, null)
            if (jobResult !is VeoApiResult.Success) {
                return@withContext VeoApiResult.Error(jobResult.toString())
            }
            
            Log.i(TAG, "Image-to-video job started: ${jobResult.data.jobId}")
            
            // Rest follows same pattern as text-to-video...
            val completedJob = pollJobCompletion(jobResult.data.jobId)
            if (completedJob !is VeoApiResult.Success) {
                return@withContext VeoApiResult.Error(completedJob.toString())
            }
            
            if (completedJob.data.signedUrl == null) {
                return@withContext VeoApiResult.Error("No video URL returned")
            }
            
            val videoBytes = downloadVideo(completedJob.data.signedUrl)
            val tempFile = File(context.cacheDir, "veo_video_${System.currentTimeMillis()}.mp4")
            FileOutputStream(tempFile).use { it.write(videoBytes) }
            
            val finalUrl = when (visibility) {
                VideoVisibility.PRIVATE -> uploadToFirebaseStorage(tempFile, params.prompt)
                VideoVisibility.PUBLIC -> uploadToYouTube(tempFile, params.prompt, context)
            }
            
            val videoId = UUID.randomUUID().toString()
            val result = VideoGenerationResult(
                id = videoId,
                url = finalUrl,
                prompt = params.prompt.trim(),
                visibility = visibility,
                duration = params.durationSeconds,
                aspectRatio = params.aspectRatio,
                createdAt = System.currentTimeMillis(),
                jobId = jobResult.data.jobId
            )
            
            saveVideoMetadata(result)
            tempFile.delete()
            
            VeoApiResult.Success(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Image-to-video generation failed", e)
            VeoApiResult.Error(e.message ?: "Unknown error occurred")
        }
    }
    
    /**
     * Generate video from video + text prompt (transformation)
     */
    suspend fun generateVideoToVideo(
        context: Context,
        params: VideoToVideoParams,
        visibility: VideoVisibility = VideoVisibility.PUBLIC
    ): VeoApiResult<VideoGenerationResult> = withContext(Dispatchers.IO) {
        try {
            val jobResult = startVideoGeneration(VideoMode.VIDEO_TO_VIDEO, null, null, params)
            if (jobResult !is VeoApiResult.Success) {
                return@withContext VeoApiResult.Error(jobResult.toString())
            }
            
            Log.i(TAG, "Video-to-video job started: ${jobResult.data.jobId}")
            
            val completedJob = pollJobCompletion(jobResult.data.jobId)
            if (completedJob !is VeoApiResult.Success) {
                return@withContext VeoApiResult.Error(completedJob.toString())
            }
            
            if (completedJob.data.signedUrl == null) {
                return@withContext VeoApiResult.Error("No video URL returned")
            }
            
            val videoBytes = downloadVideo(completedJob.data.signedUrl)
            val tempFile = File(context.cacheDir, "veo_video_${System.currentTimeMillis()}.mp4")
            FileOutputStream(tempFile).use { it.write(videoBytes) }
            
            val finalUrl = when (visibility) {
                VideoVisibility.PRIVATE -> uploadToFirebaseStorage(tempFile, params.prompt)
                VideoVisibility.PUBLIC -> uploadToYouTube(tempFile, params.prompt, context)
            }
            
            val videoId = UUID.randomUUID().toString()
            val result = VideoGenerationResult(
                id = videoId,
                url = finalUrl,
                prompt = params.prompt.trim(),
                visibility = visibility,
                duration = params.durationSeconds,
                aspectRatio = params.aspectRatio,
                createdAt = System.currentTimeMillis(),
                jobId = jobResult.data.jobId
            )
            
            saveVideoMetadata(result)
            tempFile.delete()
            
            VeoApiResult.Success(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Video-to-video generation failed", e)
            VeoApiResult.Error(e.message ?: "Video-to-video generation failed")
        }
    }
    
    /**
     * Start video generation job on Veo backend
     */
    private suspend fun startVideoGeneration(
        mode: VideoMode,
        textParams: TextToVideoParams?,
        imageParams: ImageToVideoParams?,
        videoParams: VideoToVideoParams?
    ): VeoApiResult<VideoJobResponse> = withContext(Dispatchers.IO) {
        try {
            // If the user selected Google AI Studio, generate directly via the Gemini API.
            // This path does NOT use the Veo backend.
            val providerConfig = ConfigManager.getProviderConfig()
            if (providerConfig.provider == ApiProvider.GOOGLE_AI_STUDIO) {
                return@withContext startGoogleAiStudioOperation(
                    mode = mode,
                    providerConfig = providerConfig,
                    textParams = textParams,
                    imageParams = imageParams,
                    videoParams = videoParams
                )
            }

            suspend fun doRequest(firebaseToken: String): VeoApiResult<VideoJobResponse> {
                val baseUrl = ConfigManager.veoBackendBaseUrl.trimEnd('/')
                val url = URL("$baseUrl/v1/video/${mode.apiPath}")
                val connection = url.openConnection() as HttpURLConnection
                
                connection.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $firebaseToken")
                    doOutput = true
                    connectTimeout = 30000
                    readTimeout = 120000  // Video generation can take time
                }
                
                // Build request body based on mode
                val requestBody = when (mode) {
                    VideoMode.TEXT_TO_VIDEO -> buildTextToVideoRequest(textParams!!)
                    VideoMode.IMAGE_TO_VIDEO -> buildImageToVideoRequest(imageParams!!)
                    VideoMode.VIDEO_TO_VIDEO -> buildVideoToVideoRequest(videoParams!!)
                }
                
                // Send request
                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(requestBody.toString())
                    writer.flush()
                }
                
                // Read response
                val responseCode = connection.responseCode
                
                return if (responseCode == HttpURLConnection.HTTP_ACCEPTED) { // 202
                    val response = BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                        reader.readText()
                    }
                    
                    parseJobResponse(response)
                } else {
                    val errorStream = connection.errorStream ?: connection.inputStream
                    val errorResponse = BufferedReader(InputStreamReader(errorStream)).use { reader ->
                        reader.readText()
                    }
                    
                    val errorMessage = try {
                        JSONObject(errorResponse).optJSONObject("error")?.optString("message")
                            ?: errorResponse
                    } catch (e: Exception) {
                        errorResponse
                    }
                    
                    VeoApiResult.Error(errorMessage, responseCode)
                }
            }

            val firebaseToken = getFirebaseTokenWithRetry()
                ?: return@withContext VeoApiResult.Error("User not authenticated")

            val firstAttempt = doRequest(firebaseToken)
            if (firstAttempt is VeoApiResult.Error && firstAttempt.code == HttpURLConnection.HTTP_UNAUTHORIZED) {
                val refreshedToken = FirebaseAuthHelper.getFirebaseIdToken(forceRefresh = true)
                if (refreshedToken != null) {
                    return@withContext doRequest(refreshedToken)
                }
            }

            firstAttempt
        } catch (e: Exception) {
            VeoApiResult.Error(e.message ?: "Network error occurred")
        }
    }
    
    /**
     * Poll job status until completion
     */
    private suspend fun pollJobCompletion(
        jobId: String,
        maxAttempts: Int = 60,  // 5 minutes max (5s intervals)
        intervalMs: Long = 5000
    ): VeoApiResult<JobStatusResponse> = withContext(Dispatchers.IO) {
        repeat(maxAttempts) { attempt ->
            try {
                val statusResult = getJobStatus(jobId)
                
                if (statusResult is VeoApiResult.Success) {
                    val status = statusResult.data
                    
                    Log.d(TAG, "Job $jobId status: ${status.status} (attempt ${attempt + 1})")
                    
                    when (status.status) {
                        JobStatus.COMPLETED -> return@withContext VeoApiResult.Success(status)
                        JobStatus.FAILED -> return@withContext VeoApiResult.Error(
                            status.error ?: "Video generation failed"
                        )
                        JobStatus.PENDING, JobStatus.PROCESSING -> {
                            // Continue polling
                            delay(intervalMs)
                        }
                        JobStatus.UNKNOWN -> return@withContext VeoApiResult.Error("Unknown job status")
                    }
                } else {
                    Log.w(TAG, "Failed to get job status: $statusResult")
                    delay(intervalMs)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error polling job status: ${e.message}")
                delay(intervalMs)
            }
        }
        
        VeoApiResult.Error("Job polling timeout after ${maxAttempts * intervalMs / 1000} seconds")
    }
    
    /**
     * Get current job status
     */
    private suspend fun getJobStatus(jobId: String): VeoApiResult<JobStatusResponse> = withContext(Dispatchers.IO) {
        // 1) If we already have a terminal cached status, return it fast.
        synchronized(localJobStatusCache) {
            localJobStatusCache[jobId]?.let {
                if (it.status == JobStatus.COMPLETED || it.status == JobStatus.FAILED) {
                    return@withContext VeoApiResult.Success(it)
                }
            }
        }

        // 2) Google AI Studio operations polling path (Gemini API).
        val operationName = synchronized(localGoogleOperations) { localGoogleOperations[jobId] }
        if (operationName != null) {
            val providerConfig = ConfigManager.getProviderConfig()
            val apiKey = providerConfig.apiKey
            val baseUrl = providerConfig.baseUrl.trimEnd('/')

            if (apiKey.isBlank()) {
                return@withContext VeoApiResult.Error("Google AI Studio API key is missing")
            }

            val status = pollGoogleAiStudioOperation(
                jobId = jobId,
                operationName = operationName,
                baseUrl = baseUrl,
                apiKey = apiKey
            )

            // Cache terminal states and stop polling once complete.
            if (status.status == JobStatus.COMPLETED || status.status == JobStatus.FAILED) {
                synchronized(localJobStatusCache) { localJobStatusCache[jobId] = status }
                synchronized(localGoogleOperations) { localGoogleOperations.remove(jobId) }
            }

            return@withContext VeoApiResult.Success(status)
        }

        // 3) Default: poll the Veo backend.
        try {
            suspend fun doRequest(firebaseToken: String): VeoApiResult<JobStatusResponse> {
                val baseUrl = ConfigManager.veoBackendBaseUrl.trimEnd('/')
                val url = URL("$baseUrl/v1/video/status/$jobId")
                val connection = url.openConnection() as HttpURLConnection

                connection.apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $firebaseToken")
                    connectTimeout = 10000
                    readTimeout = 30000
                }

                val responseCode = connection.responseCode

                return if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                        reader.readText()
                    }

                    parseJobStatusResponse(response)
                } else {
                    val errorResponse = BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream)).use { reader ->
                        reader.readText()
                    }
                    VeoApiResult.Error("HTTP $responseCode: $errorResponse", responseCode)
                }
            }

            val firebaseToken = getFirebaseTokenWithRetry()
                ?: return@withContext VeoApiResult.Error("User not authenticated")

            val firstAttempt = doRequest(firebaseToken)
            if (firstAttempt is VeoApiResult.Error && firstAttempt.code == HttpURLConnection.HTTP_UNAUTHORIZED) {
                val refreshedToken = FirebaseAuthHelper.getFirebaseIdToken(forceRefresh = true)
                if (refreshedToken != null) {
                    return@withContext doRequest(refreshedToken)
                }
            }

            firstAttempt
        } catch (e: Exception) {
            VeoApiResult.Error(e.message ?: "Network error occurred")
        }
    }

    /**
     * Start a long-running generation using Google AI Studio (Gemini API).
     * We return a synthetic jobId and store the operation name in memory, so the existing polling loop
     * (pollJobCompletionWithRetry -> getJobStatus) can continue to work without a backend.
     */
    private suspend fun startGoogleAiStudioOperation(
        mode: VideoMode,
        providerConfig: ProviderConfig,
        textParams: TextToVideoParams?,
        imageParams: ImageToVideoParams?,
        videoParams: VideoToVideoParams?
    ): VeoApiResult<VideoJobResponse> = withContext(Dispatchers.IO) {
        try {
            val apiKey = providerConfig.apiKey
            if (apiKey.isBlank()) {
                return@withContext VeoApiResult.Error("Google AI Studio API key is missing")
            }

            if (mode == VideoMode.VIDEO_TO_VIDEO) {
                // Gemini API supports video extension, but the app currently provides raw base64 video, not a file URI.
                return@withContext VeoApiResult.Error("Video-to-video is not supported via Google AI Studio in this app build")
            }

            val modelId = when (mode) {
                VideoMode.TEXT_TO_VIDEO -> textParams!!.modelId
                VideoMode.IMAGE_TO_VIDEO -> imageParams!!.modelId
                VideoMode.VIDEO_TO_VIDEO -> videoParams!!.modelId
            }

            val baseUrl = providerConfig.baseUrl.trimEnd('/')
            val endpoint = "$baseUrl/models/$modelId:predictLongRunning"

            val requestBody = buildGoogleAiStudioPredictLongRunningRequest(
                mode = mode,
                textParams = textParams,
                imageParams = imageParams
            )

            Log.i(TAG, "Google AI Studio request -> $endpoint")

            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-goog-api-key", apiKey)
                connectTimeout = 30000
                readTimeout = 120000
                doOutput = true
            }

            OutputStreamWriter(conn.outputStream).use { it.write(requestBody.toString()); it.flush() }

            val code = conn.responseCode
            val body = BufferedReader(InputStreamReader(if (code in 200..299) conn.inputStream else conn.errorStream ?: conn.inputStream)).use { it.readText() }

            if (code !in 200..299) {
                val errorMessage = try {
                    JSONObject(body).optJSONObject("error")?.optString("message") ?: body
                } catch (_: Exception) {
                    body
                }
                return@withContext VeoApiResult.Error("Google AI Studio error (HTTP $code): $errorMessage", code)
            }

            val operationName = try { JSONObject(body).optString("name") } catch (_: Exception) { "" }
            if (operationName.isBlank()) {
                return@withContext VeoApiResult.Error("Google AI Studio returned no operation name")
            }

            val jobId = "google_${UUID.randomUUID()}"
            synchronized(localGoogleOperations) { localGoogleOperations[jobId] = operationName }

            VeoApiResult.Success(
                VideoJobResponse(
                    jobId = jobId,
                    status = JobStatus.PROCESSING,
                    mode = mode,
                    message = "Started (Google AI Studio)",
                    quota = QuotaUsage(0, 0, 0)
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Google AI Studio start failed", e)
            VeoApiResult.Error("Google AI Studio start failed: ${e.message}")
        }
    }

    private fun buildGoogleAiStudioPredictLongRunningRequest(
        mode: VideoMode,
        textParams: TextToVideoParams?,
        imageParams: ImageToVideoParams?
    ): JSONObject {
        val instances = JSONArray()
        val instance = JSONObject()
        val parameters = JSONObject()

        when (mode) {
            VideoMode.TEXT_TO_VIDEO -> {
                val p = textParams!!
                instance.put("prompt", p.prompt)
                parameters.put("aspectRatio", p.aspectRatio)
                parameters.put("durationSeconds", sanitizeDurationSeconds(p.durationSeconds).toString())
                parameters.put("resolution", if (p.quality == VideoQuality.STANDARD) "720p" else "1080p")
                p.negativePrompt?.takeIf { it.isNotBlank() }?.let { parameters.put("negativePrompt", it) }
                p.seed?.let { parameters.put("seed", it) }
            }
            VideoMode.IMAGE_TO_VIDEO -> {
                val p = imageParams!!
                instance.put("prompt", p.prompt)
                instance.put(
                    "image",
                    JSONObject()
                        .put("imageBytes", p.imageBase64)
                        .put("mimeType", p.imageMimeType)
                )
                parameters.put("aspectRatio", p.aspectRatio)
                parameters.put("durationSeconds", sanitizeDurationSeconds(p.durationSeconds).toString())
                parameters.put("resolution", if (p.quality == VideoQuality.STANDARD) "720p" else "1080p")
                p.seed?.let { parameters.put("seed", it) }
            }
            VideoMode.VIDEO_TO_VIDEO -> {
                // Not supported in this app build
            }
        }

        instances.put(instance)

        return JSONObject().apply {
            put("instances", instances)
            if (parameters.length() > 0) {
                put("parameters", parameters)
            }
        }
    }

    private suspend fun pollGoogleAiStudioOperation(
        jobId: String,
        operationName: String,
        baseUrl: String,
        apiKey: String
    ): JobStatusResponse = withContext(Dispatchers.IO) {
        // Per docs: GET {baseUrl}/{operation_name} with x-goog-api-key.
        val opPath = operationName.trimStart('/')
        val opUrl = if (opPath.startsWith("http")) opPath else "$baseUrl/$opPath"

        val conn = (URL(opUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("x-goog-api-key", apiKey)
            connectTimeout = 15000
            readTimeout = 120000
        }

        val code = conn.responseCode
        val body = BufferedReader(InputStreamReader(if (code in 200..299) conn.inputStream else conn.errorStream ?: conn.inputStream)).use { it.readText() }

        if (code !in 200..299) {
            val message = try {
                JSONObject(body).optJSONObject("error")?.optString("message") ?: body
            } catch (_: Exception) {
                body
            }

            // Treat 4xx as terminal errors; 5xx can be transient.
            val terminal = code in 400..499
            return@withContext JobStatusResponse(
                jobId = jobId,
                status = if (terminal) JobStatus.FAILED else JobStatus.PROCESSING,
                progress = null,
                error = "Google operation poll error (HTTP $code): ${message.take(300)}"
            )
        }

        val json = try { JSONObject(body) } catch (_: Exception) { null }
            ?: return@withContext JobStatusResponse(jobId = jobId, status = JobStatus.PROCESSING)

        // If operation contains error
        json.optJSONObject("error")?.let { err ->
            val msg = err.optString("message", "Google AI Studio operation failed")
            return@withContext JobStatusResponse(jobId = jobId, status = JobStatus.FAILED, error = msg)
        }

        val done = json.optBoolean("done", false)
        if (!done) {
            return@withContext JobStatusResponse(jobId = jobId, status = JobStatus.PROCESSING)
        }

        val response = json.optJSONObject("response")

        // REST example uses response.generateVideoResponse.generatedSamples[0].video.uri
        val videoUri = response
            ?.optJSONObject("generateVideoResponse")
            ?.optJSONArray("generatedSamples")
            ?.optJSONObject(0)
            ?.optJSONObject("video")
            ?.optString("uri")
            ?.takeIf { it.isNotBlank() }
            ?: response
                ?.optJSONArray("generatedVideos")
                ?.optJSONObject(0)
                ?.optJSONObject("video")
                ?.optString("uri")
                ?.takeIf { it.isNotBlank() }

        if (videoUri.isNullOrBlank()) {
            return@withContext JobStatusResponse(jobId = jobId, status = JobStatus.FAILED, error = "Google AI Studio returned no video URI")
        }

        JobStatusResponse(jobId = jobId, status = JobStatus.COMPLETED, signedUrl = videoUri)
    }
    
    private fun shouldAttachGoogleApiKey(url: String): Boolean {
        // Gemini API "file uri" is served from generativelanguage.googleapis.com and requires x-goog-api-key.
        return url.startsWith("https://generativelanguage.googleapis.com/")
    }

    /**
     * Download bytes with manual redirect handling.
     * - For Gemini "file uri" endpoints we attach x-goog-api-key on the first request.
     * - Google often redirects to a signed download URL that does not need the API key.
     */
    private suspend fun downloadBytesWithRedirects(
        signedUrl: String,
        apiKeyOrNull: String?
    ): ByteArray = withContext(Dispatchers.IO) {
        var currentUrl = signedUrl
        var attachKey = apiKeyOrNull != null && shouldAttachGoogleApiKey(currentUrl)

        repeat(6) { redirectCount ->
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = 30000
                readTimeout = 120000
                if (attachKey) {
                    setRequestProperty("x-goog-api-key", apiKeyOrNull)
                }
            }

            val code = conn.responseCode
            when {
                code in 200..299 -> return@withContext conn.inputStream.readBytes()
                code in 300..399 -> {
                    val location = conn.getHeaderField("Location")
                        ?: throw Exception("Redirect without Location header")
                    currentUrl = location
                    // After the first hop, the download URL is usually already signed.
                    attachKey = false
                }
                else -> {
                    val err = BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream)).use { it.readText() }
                    throw Exception("Failed to download video: HTTP $code: ${err.take(300)}")
                }
            }

            if (redirectCount == 5) {
                throw Exception("Too many redirects while downloading video")
            }
        }

        throw Exception("Failed to download video")
    }

    /**
     * Download video from signed URL
     */
    private suspend fun downloadVideo(signedUrl: String): ByteArray {
        val providerConfig = ConfigManager.getProviderConfig()
        val apiKey = if (providerConfig.provider == ApiProvider.GOOGLE_AI_STUDIO) providerConfig.apiKey else null
        return downloadBytesWithRedirects(signedUrl, apiKey.takeIf { !it.isNullOrBlank() })
    }
    
    /**
     * Upload video to Firebase Storage (private videos)
     */
    private suspend fun uploadToFirebaseStorage(
        videoFile: File,
        prompt: String
    ): String = withContext(Dispatchers.IO) {
        val userId = FirebaseAuthHelper.getCurrentUserUid()
            ?: throw Exception("User not authenticated")
        
        val storage = FirebaseStorage.getInstance()
        val fileName = "video_${System.currentTimeMillis()}.mp4"
        // Keep Storage path consistent with storage.rules (generated_videos).
        val videoRef = storage.reference.child("generated_videos/$userId/$fileName")
        
        // Set metadata
        val metadata = com.google.firebase.storage.StorageMetadata.Builder()
            .setContentType("video/mp4")
            .setCustomMetadata("prompt", prompt.take(100))
            .setCustomMetadata("generated_at", System.currentTimeMillis().toString())
            .build()
        
        val uploadTask = videoRef.putFile(android.net.Uri.fromFile(videoFile), metadata)
        val taskSnapshot = uploadTask.await()
        
        // Get download URL
        val downloadUrl = taskSnapshot.storage.downloadUrl.await()
        downloadUrl.toString()
    }
    
    /**
     * Upload video to YouTube (public videos)
     * Note: This is a simplified implementation. 
     * Full YouTube upload requires OAuth flow and YouTube Data API setup.
     */
    private suspend fun uploadToYouTube(
        videoFile: File,
        prompt: String,
        context: Context
    ): String = withContext(Dispatchers.IO) {
        // TODO: Implement YouTube upload using YouTube Data API v3
        // For now, return a placeholder URL
        // This requires:
        // 1. YouTube API credentials setup
        // 2. OAuth flow for user permission
        // 3. YouTube Data API integration
        
        Log.w(TAG, "YouTube upload not yet implemented, using Firebase Storage fallback")
        
        // Fallback to Firebase Storage for now
        uploadToFirebaseStorage(videoFile, prompt)
    }
    
    /**
     * Save video metadata to Firestore
     */
    private suspend fun saveVideoMetadata(result: VideoGenerationResult) {
        try {
            val userId = FirebaseAuthHelper.getCurrentUserUid() ?: return
            
            val videoData = mapOf(
                "id" to result.id,
                "url" to result.url,
                "prompt" to result.prompt,
                "visibility" to result.visibility.name,
                "duration" to result.duration,
                "aspectRatio" to result.aspectRatio,
                "createdAt" to result.createdAt,
                "jobId" to result.jobId,
                "userId" to userId
            )
            
            FirebaseManager.firestore
                .collection("generated_videos")
                .document(result.id)
                .set(videoData)
                .await()
            
            Log.i(TAG, "Video metadata saved to Firestore: ${result.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save video metadata", e)
        }
    }
    
    // Helper methods for JSON parsing and request building
    
    private fun buildTextToVideoRequest(params: TextToVideoParams): JSONObject {
        return JSONObject().apply {
            put("model", params.modelId)
            put("prompt", params.prompt)
            put("durationSeconds", sanitizeDurationSeconds(params.durationSeconds))
            put("aspectRatio", params.aspectRatio)
            put("quality", toBackendQuality(params.quality))
            sanitizeFps(params.fps)?.let { put("fps", it) }
            toBackendCameraStyle(params.cinematicStyle)?.let { put("cameraStyle", it) }
            params.motionLevel?.let { put("motionLevel", it.value) }
            toBackendLighting(params.lightingStyle)?.let { put("lighting", it) }
            params.seed?.let { put("seed", it) }
            params.negativePrompt?.let { put("negativePrompt", it) }
            put("generateAudio", params.generateAudio)
        }
    }
    
    private fun buildImageToVideoRequest(params: ImageToVideoParams): JSONObject {
        return JSONObject().apply {
            put("model", params.modelId)
            put("prompt", params.prompt)
            put("imageBase64", params.imageBase64)
            put("imageMimeType", params.imageMimeType)
            put("durationSeconds", sanitizeDurationSeconds(params.durationSeconds))
            put("aspectRatio", params.aspectRatio)
            put("quality", toBackendQuality(params.quality))
            params.motionLevel?.let { put("motionLevel", it.value) }
            params.seed?.let { put("seed", it) }
        }
    }
    
    private fun buildVideoToVideoRequest(params: VideoToVideoParams): JSONObject {
        return JSONObject().apply {
            put("model", params.modelId)
            put("prompt", params.prompt)
            put("videoBase64", params.videoBase64)
            put("videoMimeType", params.videoMimeType)
            put("durationSeconds", sanitizeDurationSeconds(params.durationSeconds))
            put("aspectRatio", params.aspectRatio)
            put("quality", toBackendQuality(params.quality))
            put("strength", params.strength)
            params.motionLevel?.let { put("motionLevel", it.value) }
            toBackendLighting(params.lightingStyle)?.let { put("lighting", it) }
            params.seed?.let { put("seed", it) }
        }
    }
    
    private fun parseJobResponse(responseJson: String): VeoApiResult<VideoJobResponse> {
        return try {
            val json = JSONObject(responseJson)
            val data = json.getJSONObject("data")
            val quotaJson = data.getJSONObject("quota")
            
            val jobResponse = VideoJobResponse(
                jobId = data.getString("jobId"),
                status = parseJobStatus(data.getString("status")),
                mode = parseVideoMode(data.getString("mode")),
                message = data.optString("message", ""),
                quota = QuotaUsage(
                    used = quotaJson.getInt("used"),
                    limit = quotaJson.getInt("limit"),
                    remaining = quotaJson.getInt("remaining")
                )
            )
            
            VeoApiResult.Success(jobResponse)
        } catch (e: Exception) {
            VeoApiResult.Error("Failed to parse job response: ${e.message}")
        }
    }
    
    private fun parseJobStatusResponse(responseJson: String): VeoApiResult<JobStatusResponse> {
        return try {
            val json = JSONObject(responseJson)
            val data = json.getJSONObject("data")
            
            val statusResponse = JobStatusResponse(
                jobId = data.getString("jobId"),
                status = parseJobStatus(data.getString("status")),
                progress = data.optInt("progress"),
                error = data.optString("error").takeIf { it.isNotEmpty() },
                signedUrl = data.optJSONObject("result")?.optString("signedUrl"),
                gcsPath = data.optJSONObject("result")?.optString("gcsPath"),
                completedAt = data.optString("completedAt").takeIf { it.isNotEmpty() }
            )
            
            VeoApiResult.Success(statusResponse)
        } catch (e: Exception) {
            VeoApiResult.Error("Failed to parse status response: ${e.message}")
        }
    }
    
    private fun parseJobStatus(statusString: String): JobStatus {
        return when (statusString.uppercase()) {
            "PENDING" -> JobStatus.PENDING
            "PROCESSING" -> JobStatus.PROCESSING
            "COMPLETED" -> JobStatus.COMPLETED
            "FAILED" -> JobStatus.FAILED
            else -> JobStatus.UNKNOWN
        }
    }
    
    private fun parseVideoMode(modeString: String): VideoMode {
        return when (modeString) {
            "TEXT_TO_VIDEO" -> VideoMode.TEXT_TO_VIDEO
            "IMAGE_TO_VIDEO" -> VideoMode.IMAGE_TO_VIDEO
            else -> VideoMode.TEXT_TO_VIDEO // fallback
        }
    }
    
    /**
     * Unified video generation method supporting all modes and Public/Private flow
     * This is the main method for the new Generate Video Screen with async job polling
     */
    suspend fun generateVideo(
        context: Context,
        request: GenerateVideoRequest,
        onProgress: ((jobId: String, progress: Int) -> Unit)? = null
    ): VeoApiResult<VideoGenerationResult> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Starting video generation: ${request.mode} - ${request.visibility}")
            
            // Step 1: Check YouTube auth for public videos
            if (request.visibility == VideoVisibility.PUBLIC) {
                val authCheck = checkYouTubeAuth()
                if (authCheck is VeoApiResult.RequiresYouTubeAuth) {
                    return@withContext authCheck
                }
            }
            
            // Step 2: Start video generation job
            // Backend repo (Msr7799/veo_backend) exposes /v1/video/* routes.
            // Use the legacy routes directly to avoid a guaranteed 404 on /api/video/generate.
            val jobResult = startLegacyVideoGeneration(request)
            val jobId = when (jobResult) {
                is VeoApiResult.Success -> {
                    Log.i(TAG, "Video generation job started: ${jobResult.data.jobId}")
                    jobResult.data.jobId
                }
                is VeoApiResult.RequiresYouTubeAuth -> return@withContext jobResult
                is VeoApiResult.Error -> return@withContext jobResult
            }

            onProgress?.invoke(jobId, 0)
            
            // Step 3: Poll until completion with retry logic
            val completedStatus = when (val polled = pollJobCompletionWithRetry(jobId, onProgress)) {
                is VeoApiResult.Success -> polled.data
                is VeoApiResult.Error -> return@withContext polled
                is VeoApiResult.RequiresYouTubeAuth -> return@withContext VeoApiResult.Error("Unexpected polling result")
            }

            onProgress?.invoke(jobId, 100)
            
            if (completedStatus.signedUrl == null) {
                return@withContext VeoApiResult.Error("No video URL returned from backend")
            }
            
            Log.i(TAG, "Video generation completed, processing final storage...")
            
            // Step 4/5/6: Store based on visibility.
            // - PRIVATE: download then upload to Firebase Storage.
            // - PUBLIC: do NOT download to device; send signedUrl to backend which uploads to YouTube.
            val finalUrl = when (request.visibility) {
                VideoVisibility.PRIVATE -> {
                    val videoBytes = downloadVideoWithRetry(completedStatus.signedUrl)
                    val tempFile = File(context.cacheDir, "veo_video_${System.currentTimeMillis()}.mp4")
                    FileOutputStream(tempFile).use { it.write(videoBytes) }
                    Log.i(TAG, "Video downloaded, size: ${videoBytes.size} bytes")
                    try {
                        uploadToFirebaseStorage(tempFile, request.prompt)
                    } finally {
                        tempFile.delete()
                    }
                }
                VideoVisibility.PUBLIC -> {
                    uploadToYouTubeViaBackend(completedStatus.signedUrl, request.prompt)
                }
            }
            
            Log.i(TAG, "Video uploaded to ${request.visibility.name}: $finalUrl")
            
            // Step 7: Save metadata to Firestore
            val videoId = UUID.randomUUID().toString()
            val result = VideoGenerationResult(
                id = videoId,
                url = finalUrl,
                prompt = request.prompt.trim(),
                visibility = request.visibility,
                duration = request.durationSeconds,
                aspectRatio = request.aspectRatio,
                createdAt = System.currentTimeMillis(),
                jobId = jobId
            )
            
            saveVideoMetadata(result)
            
            VeoApiResult.Success(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Video generation failed", e)
            VeoApiResult.Error(e.message ?: "Unknown error occurred")
        }
    }
    
    /**
     * Start unified video generation job supporting all modes
     */
    private suspend fun startUnifiedVideoGeneration(
        request: GenerateVideoRequest
    ): VeoApiResult<VideoJobResponse> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = ConfigManager.veoBackendBaseUrl.trimEnd('/')

            suspend fun doRequest(firebaseToken: String): Pair<Int, String> {
                val url = URL("$baseUrl/api/video/generate")
                val connection = url.openConnection() as HttpURLConnection
                
                connection.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $firebaseToken")
                    doOutput = true
                    connectTimeout = 30000
                    readTimeout = 120000
                }
            
            // Build request JSON based on mode
            val requestJson = JSONObject().apply {
                put("prompt", request.prompt)
                put("model", request.modelId)
                put("mode", request.mode.name)
                put("visibility", request.visibility.name)
                put("durationSeconds", request.durationSeconds)
                put("aspectRatio", request.aspectRatio)
                put("quality", request.quality.value)
                
                // Optional parameters
                request.fps?.let { put("fps", it) }
                request.cinematicStyle?.let { put("cinematicStyle", it.value) }
                request.motionLevel?.let { put("motionLevel", it.value) }
                request.lightingStyle?.let { put("lightingStyle", it.value) }
                request.seed?.let { put("seed", it) }
                request.negativePrompt?.let { put("negativePrompt", it) }
                put("generateAudio", request.generateAudio)
                
                // Mode-specific data
                when (request.mode) {
                    VideoMode.IMAGE_TO_VIDEO -> {
                        put("imageBase64", request.imageBase64)
                        put("imageMimeType", request.imageMimeType)
                    }
                    VideoMode.VIDEO_TO_VIDEO -> {
                        put("videoBase64", request.videoBase64)
                        put("videoMimeType", request.videoMimeType)
                        put("strength", request.strength)
                    }
                    VideoMode.TEXT_TO_VIDEO -> {
                        // No additional data needed
                    }
                }
            }
            
                // Send request
                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(requestJson.toString())
                    writer.flush()
                }
                
                val responseCode = connection.responseCode
                val responseBody = if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                } else {
                    BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream)).use { it.readText() }
                }

                return responseCode to responseBody
            }

            val firebaseToken = getFirebaseTokenWithRetry()
                ?: return@withContext VeoApiResult.Error("User not authenticated")

            var (responseCode, responseBody) = doRequest(firebaseToken)
            if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                val refreshedToken = FirebaseAuthHelper.getFirebaseIdToken(forceRefresh = true)
                if (refreshedToken != null) {
                    val retried = doRequest(refreshedToken)
                    responseCode = retried.first
                    responseBody = retried.second
                }
            }

            // Backend compatibility: some deployments only support the legacy /v1/video/* routes.
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                Log.w(TAG, "Unified endpoint not found ($baseUrl/api/video/generate). Falling back to legacy /v1/video routes.")
                return@withContext startLegacyVideoGeneration(request)
            }
            
            when (responseCode) {
                HttpURLConnection.HTTP_OK -> parseJobResponse(responseBody)
                HttpURLConnection.HTTP_UNAUTHORIZED -> VeoApiResult.Error("Authentication failed")
                HttpURLConnection.HTTP_FORBIDDEN -> {
                    // Check if it's YouTube auth required
                    if (responseBody.contains("youtube_auth_required")) {
                        val authUrl = JSONObject(responseBody).optString("authUrl", "")
                        VeoApiResult.RequiresYouTubeAuth(authUrl)
                    } else {
                        VeoApiResult.Error("Access forbidden: $responseBody")
                    }
                }
                429 -> VeoApiResult.Error("Rate limit exceeded. Please try again later.")
                else -> VeoApiResult.Error("Server error ($responseCode): $responseBody")
            }
            
        } catch (e: Exception) {
            VeoApiResult.Error("Network error: ${e.message}")
        }
    }

    private suspend fun startLegacyVideoGeneration(
        request: GenerateVideoRequest
    ): VeoApiResult<VideoJobResponse> {
        return when (request.mode) {
            VideoMode.TEXT_TO_VIDEO -> {
                val params = TextToVideoParams(
                    prompt = request.prompt,
                    modelId = request.modelId,
                    durationSeconds = request.durationSeconds,
                    aspectRatio = request.aspectRatio,
                    quality = request.quality,
                    cinematicStyle = request.cinematicStyle,
                    motionLevel = request.motionLevel,
                    lightingStyle = request.lightingStyle,
                    fps = request.fps,
                    seed = request.seed,
                    negativePrompt = request.negativePrompt,
                    generateAudio = request.generateAudio
                )
                startVideoGeneration(VideoMode.TEXT_TO_VIDEO, params, null, null)
            }

            VideoMode.IMAGE_TO_VIDEO -> {
                val imageBase64 = request.imageBase64
                    ?: return VeoApiResult.Error("Please select an image")
                val imageMimeType = request.imageMimeType ?: "image/jpeg"
                val params = ImageToVideoParams(
                    prompt = request.prompt,
                    modelId = request.modelId,
                    imageBase64 = imageBase64,
                    imageMimeType = imageMimeType,
                    durationSeconds = request.durationSeconds,
                    aspectRatio = request.aspectRatio,
                    quality = request.quality,
                    motionLevel = request.motionLevel,
                    seed = request.seed
                )
                startVideoGeneration(VideoMode.IMAGE_TO_VIDEO, null, params, null)
            }

            VideoMode.VIDEO_TO_VIDEO -> {
                val videoBase64 = request.videoBase64
                    ?: return VeoApiResult.Error("Please select a video")
                val videoMimeType = request.videoMimeType ?: "video/mp4"
                val params = VideoToVideoParams(
                    prompt = request.prompt,
                    modelId = request.modelId,
                    videoBase64 = videoBase64,
                    videoMimeType = videoMimeType,
                    durationSeconds = request.durationSeconds,
                    aspectRatio = request.aspectRatio,
                    quality = request.quality,
                    motionLevel = request.motionLevel,
                    lightingStyle = request.lightingStyle,
                    strength = request.strength,
                    seed = request.seed
                )
                startVideoGeneration(VideoMode.VIDEO_TO_VIDEO, null, null, params)
            }
        }
    }
    
    /**
     * Poll job completion with retry logic and exponential backoff
     */
    private suspend fun pollJobCompletionWithRetry(
        jobId: String,
        onProgress: ((jobId: String, progress: Int) -> Unit)? = null,
        maxRetries: Int = 60,
        initialDelayMs: Long = 2000
    ): VeoApiResult<JobStatusResponse> = withContext(Dispatchers.IO) {
        var retryCount = 0
        var delayMs = initialDelayMs
        
        while (retryCount < maxRetries) {
            ensureActive()
            try {
                val statusResult = getJobStatus(jobId)
                
                when (statusResult) {
                    is VeoApiResult.Success -> {
                        when (statusResult.data.status) {
                            JobStatus.COMPLETED -> {
                                Log.i(TAG, "Job $jobId completed successfully")
                                onProgress?.invoke(jobId, 100)
                                return@withContext statusResult
                            }
                            JobStatus.FAILED -> {
                                val error = statusResult.data.error ?: "Unknown error"
                                Log.e(TAG, "Job $jobId failed: $error")
                                return@withContext VeoApiResult.Error("Video generation failed: $error")
                            }
                            JobStatus.PENDING, JobStatus.PROCESSING -> {
                                val progress = statusResult.data.progress ?: 0
                                Log.d(TAG, "Job $jobId still processing (${progress}%)")
                                onProgress?.invoke(jobId, progress)
                                // Continue polling
                            }
                            JobStatus.UNKNOWN -> {
                                Log.w(TAG, "Job $jobId has unknown status")
                                // Continue polling but increase delay
                                delayMs = (delayMs * 1.5).toLong()
                            }
                        }
                    }
                    is VeoApiResult.Error -> {
                        Log.w(TAG, "Failed to get job status (attempt ${retryCount + 1}): ${statusResult.message}")
                        if (retryCount >= maxRetries - 1) {
                            return@withContext statusResult
                        }
                    }
                    else -> {
                        Log.w(TAG, "Unexpected result type for job status")
                    }
                }
                
                // Wait before next poll with exponential backoff
                delay(delayMs)
                delayMs = minOf(delayMs * 2, 30000) // Cap at 30 seconds
                retryCount++
                
            } catch (e: Exception) {
                Log.e(TAG, "Exception during job polling", e)
                if (retryCount >= maxRetries - 1) {
                    return@withContext VeoApiResult.Error("Polling failed: ${e.message}")
                }
                delay(delayMs)
                retryCount++
            }
        }
        
        VeoApiResult.Error("Job polling timeout after $maxRetries attempts")
    }
    
    /**
     * Download video with retry logic
     */
    private suspend fun downloadVideoWithRetry(
        signedUrl: String,
        maxRetries: Int = 3
    ): ByteArray = withContext(Dispatchers.IO) {
        var lastException: Exception? = null

        val providerConfig = ConfigManager.getProviderConfig()
        val apiKeyOrNull = if (providerConfig.provider == ApiProvider.GOOGLE_AI_STUDIO) {
            providerConfig.apiKey.takeIf { it.isNotBlank() }
        } else {
            null
        }
        
        repeat(maxRetries) { attempt ->
            try {
                Log.d(TAG, "Downloading video (attempt ${attempt + 1})")
                return@withContext downloadBytesWithRedirects(signedUrl, apiKeyOrNull)
                
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Download attempt ${attempt + 1} failed: ${e.message}")
                if (attempt < maxRetries - 1) {
                    delay(1000L * (attempt + 1)) // Progressive delay
                }
            }
        }
        
        throw lastException ?: Exception("Download failed after $maxRetries attempts")
    }
    
    /**
     * Check YouTube authentication status
     */
    private suspend fun checkYouTubeAuth(): VeoApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            suspend fun doRequest(firebaseToken: String): VeoApiResult<Unit> {
                val baseUrl = ConfigManager.veoBackendBaseUrl.trimEnd('/')
                val statusUrl = URL("$baseUrl/v1/youtube/status")
                val statusConnection = statusUrl.openConnection() as HttpURLConnection
                
                statusConnection.apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $firebaseToken")
                    connectTimeout = 10000
                    readTimeout = 30000
                }
                
                val statusCode = statusConnection.responseCode
                val statusBody = if (statusCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader(InputStreamReader(statusConnection.inputStream)).use { it.readText() }
                } else {
                    BufferedReader(InputStreamReader(statusConnection.errorStream ?: statusConnection.inputStream)).use { it.readText() }
                }

                return when (statusCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val json = JSONObject(statusBody)
                        val authorized = json.optJSONObject("data")?.optBoolean("authorized", false) ?: false
                        if (authorized) {
                            VeoApiResult.Success(Unit)
                        } else {
                            val authUrl = fetchYouTubeAuthUrl(baseUrl, firebaseToken)
                            VeoApiResult.RequiresYouTubeAuth(authUrl)
                        }
                    }
                    503 -> VeoApiResult.Error("YouTube upload feature is not configured on this server")
                    HttpURLConnection.HTTP_UNAUTHORIZED -> VeoApiResult.Error("Authentication failed", statusCode)
                    else -> VeoApiResult.Error("Failed to check YouTube auth status ($statusCode): $statusBody", statusCode)
                }
            }

            val firebaseToken = getFirebaseTokenWithRetry()
                ?: return@withContext VeoApiResult.Error("User not authenticated")

            val firstAttempt = doRequest(firebaseToken)
            if (firstAttempt is VeoApiResult.Error && firstAttempt.code == HttpURLConnection.HTTP_UNAUTHORIZED) {
                val refreshedToken = FirebaseAuthHelper.getFirebaseIdToken(forceRefresh = true)
                if (refreshedToken != null) {
                    return@withContext doRequest(refreshedToken)
                }
            }

            firstAttempt
            
        } catch (e: Exception) {
            VeoApiResult.Error("Network error: ${e.message}")
        }
    }

    private suspend fun fetchYouTubeAuthUrl(
        baseUrl: String,
        firebaseToken: String
    ): String = withContext(Dispatchers.IO) {
        suspend fun doRequest(token: String): Pair<Int, String> {
            val url = URL("$baseUrl/v1/youtube/auth")
            val connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = 10000
                readTimeout = 30000
            }

            val responseCode = connection.responseCode
            val responseBody = if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            } else {
                BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream)).use { it.readText() }
            }
            return responseCode to responseBody
        }

        var (responseCode, responseBody) = doRequest(firebaseToken)
        if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            val refreshedToken = FirebaseAuthHelper.getFirebaseIdToken(forceRefresh = true)
            if (refreshedToken != null) {
                val retried = doRequest(refreshedToken)
                responseCode = retried.first
                responseBody = retried.second
            }
        }

        if (responseCode == HttpURLConnection.HTTP_OK) {
            JSONObject(responseBody).optJSONObject("data")?.optString("authUrl")
                ?: "$baseUrl/v1/youtube/auth"
        } else {
            "$baseUrl/v1/youtube/auth"
        }
    }
    
    /**
     * Upload video to YouTube via backend
     */
    private suspend fun uploadToYouTubeViaBackend(
        signedVideoUrl: String,
        title: String
    ): String = withContext(Dispatchers.IO) {
        try {
            suspend fun doRequest(firebaseToken: String): Pair<Int, String> {
                val baseUrl = ConfigManager.veoBackendBaseUrl.trimEnd('/')
                val url = URL("$baseUrl/v1/youtube/upload")
                val connection = url.openConnection() as HttpURLConnection
                
                connection.apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer $firebaseToken")
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 30000
                    readTimeout = 300000 // 5 minutes for upload
                }
                
                val requestJson = JSONObject().apply {
                    put("title", title)
                    put("description", "Generated with VEO AI")
                    // Backend expects a downloadable URL (e.g., GCS signed URL from /v1/video/status/:jobId)
                    put("videoUrl", signedVideoUrl)
                    put("privacy", "public")
                }
                
                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(requestJson.toString())
                    writer.flush()
                }
                
                val responseCode = connection.responseCode
                val responseBody = if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                } else {
                    BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream)).use { it.readText() }
                }
                
                return responseCode to responseBody
            }

            val firebaseToken = getFirebaseTokenWithRetry()
                ?: throw Exception("User not authenticated")

            var (responseCode, responseBody) = doRequest(firebaseToken)
            if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                val refreshedToken = FirebaseAuthHelper.getFirebaseIdToken(forceRefresh = true)
                if (refreshedToken != null) {
                    val retried = doRequest(refreshedToken)
                    responseCode = retried.first
                    responseBody = retried.second
                }
            }

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = JSONObject(responseBody)
                val data = response.optJSONObject("data")
                    ?: throw Exception("Invalid YouTube response: $responseBody")
                data.optString("url")
            } else {
                throw Exception("YouTube upload failed ($responseCode): $responseBody")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "YouTube upload failed", e)
            throw e
        }
    }
    
}
