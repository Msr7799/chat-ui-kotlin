package com.example.chat_ui.api

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.chat_ui.config.ConfigManager
import com.example.chat_ui.data.ApiProvider
import com.example.chat_ui.data.ProviderConfig
import com.example.chat_ui.data.firebase.FirebaseManager
import com.example.chat_ui.utils.FirebaseAuthHelper
import com.google.firebase.storage.FirebaseStorage
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.Serializable
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject

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
    // We keep a mapping from our synthetic jobId -> operation name so the existing polling loop can
    // work.
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
        PRIVATE, // Firebase Storage
        PUBLIC // YouTube
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
    data class QuotaUsage(val used: Int, val limit: Int, val remaining: Int)

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
            val id: String = "",
            val url: String = "", // Final storage URL (Firebase Storage or YouTube)
            val prompt: String = "",
            val visibility: VideoVisibility = VideoVisibility.PRIVATE,
            val duration: Int = 0,
            val aspectRatio: String = "16:9",
            val createdAt: Long = 0L,
            val jobId: String = ""
    ) : Serializable

    sealed class VeoApiResult<out T> {
        data class Success<T>(val data: T) : VeoApiResult<T>()
        data class Error(val message: String, val code: Int? = null) : VeoApiResult<Nothing>()
        data class RequiresYouTubeAuth(val authUrl: String) : VeoApiResult<Nothing>()
    }

    /** Generate video from text prompt */
    suspend fun generateTextToVideo(
            context: Context,
            params: TextToVideoParams,
            visibility: VideoVisibility = VideoVisibility.PUBLIC
    ): VeoApiResult<VideoGenerationResult> =
            withContext(Dispatchers.IO) {
                try {
                    // Step 1: Start video generation job
                    val jobResult =
                            startVideoGeneration(VideoMode.TEXT_TO_VIDEO, params, null, null)
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
                    val tempFile =
                            File(context.cacheDir, "veo_video_${System.currentTimeMillis()}.mp4")
                    FileOutputStream(tempFile).use { it.write(videoBytes) }

                    Log.i(TAG, "Video downloaded, size: ${videoBytes.size} bytes")

                    // Step 5: Upload to final destination
                    val finalUrl =
                            when (visibility) {
                                VideoVisibility.PRIVATE ->
                                        uploadToFirebaseStorage(tempFile, params.prompt)
                                VideoVisibility.PUBLIC -> uploadToYouTube(tempFile, params.prompt)
                            }

                    Log.i(TAG, "Video uploaded to ${visibility.name}: $finalUrl")

                    // Step 6: Save metadata to Firestore
                    val videoId = UUID.randomUUID().toString()
                    val result =
                            VideoGenerationResult(
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

    /** Generate video from image + text prompt */
    suspend fun generateImageToVideo(
            context: Context,
            params: ImageToVideoParams,
            visibility: VideoVisibility = VideoVisibility.PUBLIC
    ): VeoApiResult<VideoGenerationResult> =
            withContext(Dispatchers.IO) {
                try {
                    // Similar workflow but with image parameters
                    val jobResult =
                            startVideoGeneration(VideoMode.IMAGE_TO_VIDEO, null, params, null)
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
                    val tempFile =
                            File(context.cacheDir, "veo_video_${System.currentTimeMillis()}.mp4")
                    FileOutputStream(tempFile).use { it.write(videoBytes) }

                    val finalUrl =
                            when (visibility) {
                                VideoVisibility.PRIVATE ->
                                        uploadToFirebaseStorage(tempFile, params.prompt)
                                VideoVisibility.PUBLIC -> uploadToYouTube(tempFile, params.prompt)
                            }

                    val videoId = UUID.randomUUID().toString()
                    val result =
                            VideoGenerationResult(
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

    /** Generate video from video + text prompt (transformation) */
    suspend fun generateVideoToVideo(
            context: Context,
            params: VideoToVideoParams,
            visibility: VideoVisibility = VideoVisibility.PUBLIC
    ): VeoApiResult<VideoGenerationResult> =
            withContext(Dispatchers.IO) {
                try {
                    val jobResult =
                            startVideoGeneration(VideoMode.VIDEO_TO_VIDEO, null, null, params)
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
                    val tempFile =
                            File(context.cacheDir, "veo_video_${System.currentTimeMillis()}.mp4")
                    FileOutputStream(tempFile).use { it.write(videoBytes) }

                    val finalUrl =
                            when (visibility) {
                                VideoVisibility.PRIVATE ->
                                        uploadToFirebaseStorage(tempFile, params.prompt)
                                VideoVisibility.PUBLIC -> uploadToYouTube(tempFile, params.prompt)
                            }

                    val videoId = UUID.randomUUID().toString()
                    val result =
                            VideoGenerationResult(
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

    /** Start video generation job on Veo backend */
    private suspend fun startVideoGeneration(
            mode: VideoMode,
            textParams: TextToVideoParams?,
            imageParams: ImageToVideoParams?,
            videoParams: VideoToVideoParams?
    ): VeoApiResult<VideoJobResponse> =
            withContext(Dispatchers.IO) {
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

                    return@withContext VeoApiResult.Error("Veo backend is disabled. Please use Google AI Studio provider.")
/*
                    suspend fun doRequest(firebaseToken: String): VeoApiResult<VideoJobResponse> {
                        // Backend logic removed
                        return VeoApiResult.Error("Backend disabled")
                    }

                    val firebaseToken =
                            getFirebaseTokenWithRetry()
                                    ?: return@withContext VeoApiResult.Error(
                                            "User not authenticated"
                                    )

                    val firstAttempt = doRequest(firebaseToken)
                    if (firstAttempt is VeoApiResult.Error &&
                                    firstAttempt.code == HttpURLConnection.HTTP_UNAUTHORIZED
                    ) {
                        val refreshedToken =
                                FirebaseAuthHelper.getFirebaseIdToken(forceRefresh = true)
                        if (refreshedToken != null) {
                            return@withContext doRequest(refreshedToken)
                        }
                    }

                    firstAttempt
*/
                } catch (e: Exception) {
                    VeoApiResult.Error(e.message ?: "Network error occurred")
                }
            }

    /** Poll job status until completion */
    private suspend fun pollJobCompletion(
            jobId: String,
            maxAttempts: Int = 60, // 5 minutes max (5s intervals)
            intervalMs: Long = 5000
    ): VeoApiResult<JobStatusResponse> =
            withContext(Dispatchers.IO) {
                repeat(maxAttempts) { attempt ->
                    try {
                        val statusResult = getJobStatus(jobId)

                        if (statusResult is VeoApiResult.Success) {
                            val status = statusResult.data

                            Log.d(
                                    TAG,
                                    "Job $jobId status: ${status.status} (attempt ${attempt + 1})"
                            )

                            when (status.status) {
                                JobStatus.COMPLETED ->
                                        return@withContext VeoApiResult.Success(status)
                                JobStatus.FAILED ->
                                        return@withContext VeoApiResult.Error(
                                                status.error ?: "Video generation failed"
                                        )
                                JobStatus.PENDING, JobStatus.PROCESSING -> {
                                    // Continue polling
                                    delay(intervalMs)
                                }
                                JobStatus.UNKNOWN ->
                                        return@withContext VeoApiResult.Error("Unknown job status")
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

                VeoApiResult.Error(
                        "Job polling timeout after ${maxAttempts * intervalMs / 1000} seconds"
                )
            }

    /** Get current job status */
    private suspend fun getJobStatus(jobId: String): VeoApiResult<JobStatusResponse> =
            withContext(Dispatchers.IO) {
                // 1) If we already have a terminal cached status, return it fast.
                synchronized(localJobStatusCache) {
                    localJobStatusCache[jobId]?.let {
                        if (it.status == JobStatus.COMPLETED || it.status == JobStatus.FAILED) {
                            return@withContext VeoApiResult.Success(it)
                        }
                    }
                }

                // 2) Google AI Studio operations polling path (Gemini API).
                val operationName =
                        synchronized(localGoogleOperations) { localGoogleOperations[jobId] }
                if (operationName != null) {
                    val providerConfig = ConfigManager.getProviderConfig()
                    val apiKey = providerConfig.apiKey
                    val baseUrl = providerConfig.baseUrl.trimEnd('/')

                    if (apiKey.isBlank()) {
                        return@withContext VeoApiResult.Error("Google AI Studio API key is missing")
                    }

                    val status =
                            pollGoogleAiStudioOperation(
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
                return@withContext VeoApiResult.Error("Veo backend is disabled.")
/*
                // 3) Default: poll the Veo backend.
                try {
                    suspend fun doRequest(firebaseToken: String): VeoApiResult<JobStatusResponse> {
                        // Backend logic removed
                         return VeoApiResult.Error("Backend disabled")
                    }
                     val firebaseToken =
                            getFirebaseTokenWithRetry()
                                    ?: return@withContext VeoApiResult.Error(
                                            "User not authenticated"
                                    )

                    val firstAttempt = doRequest(firebaseToken)
                     if (firstAttempt is VeoApiResult.Error &&
                                    firstAttempt.code == HttpURLConnection.HTTP_UNAUTHORIZED
                    ) {
                        val refreshedToken =
                                FirebaseAuthHelper.getFirebaseIdToken(forceRefresh = true)
                        if (refreshedToken != null) {
                            return@withContext doRequest(refreshedToken)
                        }
                    }

                    firstAttempt
                } catch (e: Exception) {
                    VeoApiResult.Error(e.message ?: "Network error occurred")
                }
*/
            }

    /**
     * Start a long-running generation using Google AI Studio (Gemini API). We return a synthetic
     * jobId and store the operation name in memory, so the existing polling loop
     * (pollJobCompletionWithRetry -> getJobStatus) can continue to work without a backend.
     */
    private suspend fun startGoogleAiStudioOperation(
            mode: VideoMode,
            providerConfig: ProviderConfig,
            textParams: TextToVideoParams?,
            imageParams: ImageToVideoParams?,
            videoParams: VideoToVideoParams?
    ): VeoApiResult<VideoJobResponse> =
            withContext(Dispatchers.IO) {
                try {
                    val apiKey = providerConfig.apiKey
                    if (apiKey.isBlank()) {
                        return@withContext VeoApiResult.Error("Google AI Studio API key is missing")
                    }

                    if (mode == VideoMode.VIDEO_TO_VIDEO) {
                        return@withContext VeoApiResult.Error(
                                "Video-to-video is not supported via Google AI Studio"
                        )
                    }

                    val modelId =
                            when (mode) {
                                VideoMode.TEXT_TO_VIDEO -> textParams!!.modelId
                                VideoMode.IMAGE_TO_VIDEO -> imageParams!!.modelId
                                VideoMode.VIDEO_TO_VIDEO -> videoParams!!.modelId
                            }

                    val baseUrl = providerConfig.baseUrl.trimEnd('/')

                    // ✅ Endpoint for Veo video generation via Gemini API
                    val modelsBaseUrl =
                            if (baseUrl.endsWith("/models")) baseUrl else "$baseUrl/models"
                    val endpoint = "$modelsBaseUrl/$modelId:predictLongRunning"
                    val requestBody =
                            buildGoogleAiStudioPredictLongRunningRequest(
                                    mode = mode,
                                    textParams = textParams,
                                    imageParams = imageParams
                            )

                    Log.i(TAG, "Google AI Studio video generation request -> $endpoint")
                    Log.d(TAG, "Request body: ${requestBody.toString(2)}")

                    val conn =
                            (URL(endpoint).openConnection() as HttpURLConnection).apply {
                                requestMethod = "POST"
                                setRequestProperty("Content-Type", "application/json")
                                setRequestProperty("x-goog-api-key", apiKey)
                                connectTimeout = 30000
                                readTimeout = 120000
                                doOutput = true
                            }

                    OutputStreamWriter(conn.outputStream).use {
                        it.write(requestBody.toString())
                        it.flush()
                    }

                    val code = conn.responseCode
                    val body =
                            BufferedReader(
                                            InputStreamReader(
                                                    if (code in 200..299) conn.inputStream
                                                    else conn.errorStream ?: conn.inputStream
                                            )
                                    )
                                    .use { it.readText() }

                    Log.d(TAG, "Response code: $code")
                    Log.d(TAG, "Response body: $body")

                    if (code !in 200..299) {
                        val errorMessage =
                                try {
                                    val errorJson = JSONObject(body)
                                    errorJson.optJSONObject("error")?.optString("message")
                                            ?: errorJson.optString("error") ?: body
                                } catch (_: Exception) {
                                    body
                                }
                        return@withContext VeoApiResult.Error(
                                "Google AI Studio error (HTTP $code): $errorMessage",
                                code
                        )
                    }

                    // Parse response - البحث عن operation name
                    val json =
                            try {
                                JSONObject(body)
                            } catch (e: Exception) {
                                return@withContext VeoApiResult.Error(
                                        "Invalid JSON response: ${e.message}"
                                )
                            }

                    // ✅ الـ operation name يأتي في حقل "name"
                    val operationName = json.optString("name")
                    if (operationName.isBlank()) {
                        // ربما الفيديو جاهز مباشرة (synchronous)؟
                        // تحقق من وجود video URI في الـ response
                        val videoUri =
                                json.optJSONObject("response")
                                        ?.optJSONArray("generatedVideos")
                                        ?.optJSONObject(0)
                                        ?.optString("uri")

                        if (!videoUri.isNullOrBlank()) {
                            // فيديو جاهز فوراً!
                            val jobId = "google_sync_${UUID.randomUUID()}"
                            synchronized(localJobStatusCache) {
                                localJobStatusCache[jobId] =
                                        JobStatusResponse(
                                                jobId = jobId,
                                                status = JobStatus.COMPLETED,
                                                signedUrl = videoUri
                                        )
                            }

                            return@withContext VeoApiResult.Success(
                                    VideoJobResponse(
                                            jobId = jobId,
                                            status = JobStatus.COMPLETED,
                                            mode = mode,
                                            message = "Video generated immediately",
                                            quota = QuotaUsage(0, 0, 0)
                                    )
                            )
                        }

                        return@withContext VeoApiResult.Error(
                                "Google AI Studio returned no operation name or video"
                        )
                    }

                    // حفظ operation name للـ polling
                    val jobId = "google_${UUID.randomUUID()}"
                    synchronized(localGoogleOperations) {
                        localGoogleOperations[jobId] = operationName
                    }

                    Log.i(TAG, "Google AI Studio operation started: $operationName")

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
        val requestBody = JSONObject()
        val instance = JSONObject()

        when (mode) {
            VideoMode.TEXT_TO_VIDEO -> {
                val p = textParams!!
                instance.put("prompt", p.prompt)

                val params =
                        JSONObject().apply {
                            put("aspectRatio", p.aspectRatio)
                            put("durationSeconds", sanitizeDurationSeconds(p.durationSeconds))
                            put(
                                    "resolution",
                                    if (p.quality == VideoQuality.STANDARD) "720p" else "1080p"
                            )

                            p.negativePrompt?.takeIf { it.isNotBlank() }?.let {
                                put("negativePrompt", it)
                            }
                            p.seed?.let { put("seed", it) }
                        }
                requestBody.put("parameters", params)
            }
            VideoMode.IMAGE_TO_VIDEO -> {
                val p = imageParams!!
                instance.put("prompt", p.prompt)

                val imageObj =
                        JSONObject().apply {
                            put("imageBytes", p.imageBase64)
                            put("mimeType", p.imageMimeType)
                        }
                instance.put("image", imageObj)

                val params =
                        JSONObject().apply {
                            put("aspectRatio", p.aspectRatio)
                            put("durationSeconds", sanitizeDurationSeconds(p.durationSeconds))
                            put(
                                    "resolution",
                                    if (p.quality == VideoQuality.STANDARD) "720p" else "1080p"
                            )
                            p.seed?.let { put("seed", it) }
                        }

                requestBody.put("parameters", params)
            }
            VideoMode.VIDEO_TO_VIDEO -> {
                throw IllegalArgumentException("VIDEO_TO_VIDEO not supported via Google AI Studio")
            }
        }

        requestBody.put("instances", org.json.JSONArray().put(instance))
        return requestBody
    }

    private suspend fun pollGoogleAiStudioOperation(
            jobId: String,
            operationName: String,
            baseUrl: String,
            apiKey: String
    ): JobStatusResponse =
            withContext(Dispatchers.IO) {
                // Build operation URL (do NOT include the API key in the URL)
                val opPath = operationName.trimStart('/')
                val opUrl =
                        if (opPath.startsWith("http")) {
                            opPath
                        } else {
                            "$baseUrl/$opPath"
                        }

                Log.d(TAG, "Polling operation: $opUrl")

                val conn =
                        (URL(opUrl).openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            setRequestProperty("Content-Type", "application/json")
                            setRequestProperty("x-goog-api-key", apiKey)
                            connectTimeout = 15000
                            readTimeout = 120000
                        }

                val code = conn.responseCode
                val body =
                        BufferedReader(
                                        InputStreamReader(
                                                if (code in 200..299) conn.inputStream
                                                else conn.errorStream ?: conn.inputStream
                                        )
                                )
                                .use { it.readText() }

                Log.d(TAG, "Poll response code: $code")
                Log.d(TAG, "Poll response body: $body")

                if (code !in 200..299) {
                    val message =
                            try {
                                JSONObject(body).optJSONObject("error")?.optString("message")
                                        ?: body
                            } catch (_: Exception) {
                                body
                            }

                    val terminal = code in 400..499
                    return@withContext JobStatusResponse(
                            jobId = jobId,
                            status = if (terminal) JobStatus.FAILED else JobStatus.PROCESSING,
                            progress = null,
                            error = "Poll error (HTTP $code): ${message.take(300)}"
                    )
                }

                val json =
                        try {
                            JSONObject(body)
                        } catch (_: Exception) {
                            return@withContext JobStatusResponse(
                                    jobId = jobId,
                                    status = JobStatus.PROCESSING
                            )
                        }

                // تحقق من وجود خطأ
                json.optJSONObject("error")?.let { err ->
                    val msg = err.optString("message", "Operation failed")
                    return@withContext JobStatusResponse(
                            jobId = jobId,
                            status = JobStatus.FAILED,
                            error = msg
                    )
                }

                // تحقق من اكتمال العملية
                val done = json.optBoolean("done", false)
                if (!done) {
                    // العملية ما زالت قيد المعالجة
                    return@withContext JobStatusResponse(
                            jobId = jobId,
                            status = JobStatus.PROCESSING
                    )
                }

                // ✅ استخراج video URI من الـ response
                // جرب عدة مسارات محتملة في الـ JSON
                val response = json.optJSONObject("response")

                val videoUri =
                        response?.optJSONArray("generatedVideos")
                                ?.optJSONObject(0)
                                ?.optString("uri")
                                ?: response?.optJSONObject("generateVideoResponse")
                                        ?.optJSONArray("generatedSamples")
                                        ?.optJSONObject(0)
                                        ?.optJSONObject("video")
                                        ?.optString("uri")
                                        ?: response?.optString("videoUri")

                if (videoUri.isNullOrBlank()) {
                    Log.e(TAG, "No video URI found in response: ${response?.toString() ?: "null"}")
                    return@withContext JobStatusResponse(
                            jobId = jobId,
                            status = JobStatus.FAILED,
                            error = "No video URI in completed operation"
                    )
                }

                Log.i(TAG, "Video ready: $videoUri")

                JobStatusResponse(jobId = jobId, status = JobStatus.COMPLETED, signedUrl = videoUri)
            }
    /** Download video from signed URL */
    private suspend fun downloadVideo(signedUrl: String): ByteArray {
        val providerConfig = ConfigManager.getProviderConfig()
        val apiKey =
                if (providerConfig.provider == ApiProvider.GOOGLE_AI_STUDIO) providerConfig.apiKey
                else null
        return downloadBytesWithRedirects(signedUrl, apiKey.takeIf { !it.isNullOrBlank() })
    }

    /** Upload video to Firebase Storage (private videos) */
    private suspend fun uploadToFirebaseStorage(videoFile: File, prompt: String): String =
            withContext(Dispatchers.IO) {
                val userId =
                        FirebaseAuthHelper.getCurrentUserUid()
                                ?: throw Exception("User not authenticated")

                val storage = FirebaseStorage.getInstance()
                val fileName = "video_${System.currentTimeMillis()}.mp4"
                // Keep Storage path consistent with storage.rules (generated_videos).
                val videoRef = storage.reference.child("generated_videos/$userId/$fileName")

                // Set metadata
                val metadata =
                        com.google.firebase.storage.StorageMetadata.Builder()
                                .setContentType("video/mp4")
                                .setCustomMetadata("prompt", prompt.take(100))
                                .setCustomMetadata(
                                        "generated_at",
                                        System.currentTimeMillis().toString()
                                )
                                .build()

                val uploadTask = videoRef.putFile(android.net.Uri.fromFile(videoFile), metadata)
                val taskSnapshot = uploadTask.await()

                // Get download URL
                val downloadUrl = taskSnapshot.storage.downloadUrl.await()
                downloadUrl.toString()
            }

    /**
     * Upload video to YouTube (public videos) Note: This is a simplified implementation. Full
     * YouTube upload requires OAuth flow and YouTube Data API setup.
     */
    private suspend fun uploadToYouTube(videoFile: File, prompt: String): String =
            withContext(Dispatchers.IO) {
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

    /** Save video metadata to Firestore */
    private suspend fun saveVideoMetadata(result: VideoGenerationResult) {
        try {
            val userId = FirebaseAuthHelper.getCurrentUserUid() ?: return

            val videoData =
                    mapOf(
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

            val jobResponse =
                    VideoJobResponse(
                            jobId = data.getString("jobId"),
                            status = parseJobStatus(data.getString("status")),
                            mode = parseVideoMode(data.getString("mode")),
                            message = data.optString("message", ""),
                            quota =
                                    QuotaUsage(
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

            val statusResponse =
                    JobStatusResponse(
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
     * Unified video generation method supporting all modes and Public/Private flow This is the main
     * method for the new Generate Video Screen with async job polling
     */
    suspend fun generateVideo(
            context: Context,
            request: GenerateVideoRequest,
            onProgress: ((jobId: String, progress: Int) -> Unit)? = null
    ): VeoApiResult<VideoGenerationResult> =
            withContext(Dispatchers.IO) {
                try {
                    Log.i(TAG, "Starting video generation for project ${ConfigManager.appName}: ${request.mode} - ${request.visibility}")

                    // Step 1: YouTube auth check removed (Cloudinary enforced)

                    // Step 2: Start video generation job
                    // Backend repo (Msr7799/veo_backend) exposes /v1/video/* routes.
                    // Use the legacy routes directly to avoid a guaranteed 404 on
                    // /api/video/generate.
                    val jobResult = startLegacyVideoGeneration(request)
                    val jobId =
                            when (jobResult) {
                                is VeoApiResult.Success -> {
                                    Log.i(
                                            TAG,
                                            "Video generation job started: ${jobResult.data.jobId}"
                                    )
                                    jobResult.data.jobId
                                }
                                is VeoApiResult.RequiresYouTubeAuth -> return@withContext jobResult
                                is VeoApiResult.Error -> return@withContext jobResult
                            }

                    onProgress?.invoke(jobId, 0)

                    // Step 3: Poll until completion with retry logic
                    val completedStatus =
                            when (val polled = pollJobCompletionWithRetry(jobId, onProgress)) {
                                is VeoApiResult.Success -> polled.data
                                is VeoApiResult.Error -> return@withContext polled
                                is VeoApiResult.RequiresYouTubeAuth ->
                                        return@withContext VeoApiResult.Error(
                                                "Unexpected polling result"
                                        )
                            }

                    onProgress?.invoke(jobId, 100)

                    if (completedStatus.signedUrl == null) {
                        return@withContext VeoApiResult.Error("No video URL returned from backend")
                    }

                    Log.i(TAG, "Video generation completed, processing final storage...")

                    // Step 4/5/6: Store based on visibility.
                    // - PRIVATE: download then upload to Firebase Storage.
                    // - PUBLIC: do NOT download to device; send signedUrl to backend which uploads
                    // to YouTube.
                    // Step 4: Download and Upload to Cloudinary
                    val videoBytes = downloadVideoWithRetry(completedStatus.signedUrl)
                    val tempFile =
                            File(context.cacheDir, "veo_video_${System.currentTimeMillis()}.mp4")
                    val finalUrl: String

                    try {
                        FileOutputStream(tempFile).use { it.write(videoBytes) }
                        Log.i(TAG, "Video downloaded, size: ${videoBytes.size} bytes")

                        // Save to local KotlinGeneratedVideo folder
                        try {
                            val videosDir = File(
                                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                                "KotlinGeneratedVideo"
                            )
                            if (!videosDir.exists()) {
                                videosDir.mkdirs()
                            }
                            val savedFile = File(videosDir, "video_${System.currentTimeMillis()}.mp4")
                            FileOutputStream(savedFile).use { it.write(videoBytes) }
                            Log.i(TAG, "Video saved to local storage: ${savedFile.absolutePath}")
                        } catch (localSaveError: Exception) {
                            Log.w(TAG, "Failed to save video locally, continuing with cloud upload: ${localSaveError.message}")
                        }

                        val cloudinaryFolder = "${ConfigManager.cloudinaryUploadFolder}/veo_generated_videos"
                        Log.i(TAG, "Uploading to Cloudinary folder: $cloudinaryFolder")
                        
                        val cloudinaryResult =
                                com.example.chat_ui.data.cloud.CloudinaryManager.uploadVideo(
                                        context = context,
                                        videoUri = android.net.Uri.fromFile(tempFile),
                                        folder = cloudinaryFolder,
                                        tags = listOf("veo", "ai-generated", request.mode.name)
                                )
                        finalUrl = cloudinaryResult.url
                        Log.i(TAG, "Video uploaded to Cloudinary: $finalUrl")
                    } catch (e: Exception) {
                        Log.e(TAG, "Cloudinary upload failed", e)
                        throw Exception("Failed to upload to Cloudinary: ${e.message}")
                    } finally {
                        tempFile.delete()
                    }

                    Log.i(TAG, "Video uploaded to ${request.visibility.name}: $finalUrl")

                    // Step 7: Save metadata to Firestore
                    val videoId = UUID.randomUUID().toString()
                    val result =
                            VideoGenerationResult(
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

    /** Start unified video generation job supporting all modes */
    private suspend fun startUnifiedVideoGeneration(
            _request: GenerateVideoRequest
    ): VeoApiResult<VideoJobResponse> =
            withContext(Dispatchers.IO) {
                return@withContext VeoApiResult.Error("Veo backend is disabled.")

            }

    private suspend fun startLegacyVideoGeneration(
            request: GenerateVideoRequest
    ): VeoApiResult<VideoJobResponse> {
        return when (request.mode) {
            VideoMode.TEXT_TO_VIDEO -> {
                val params =
                        TextToVideoParams(
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
                val imageBase64 =
                        request.imageBase64 ?: return VeoApiResult.Error("Please select an image")
                val imageMimeType = request.imageMimeType ?: "image/jpeg"
                val params =
                        ImageToVideoParams(
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
                val videoBase64 =
                        request.videoBase64 ?: return VeoApiResult.Error("Please select a video")
                val videoMimeType = request.videoMimeType ?: "video/mp4"
                val params =
                        VideoToVideoParams(
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

    /** Poll job completion with retry logic and exponential backoff */
    private suspend fun pollJobCompletionWithRetry(
            jobId: String,
            onProgress: ((jobId: String, progress: Int) -> Unit)? = null,
            maxRetries: Int = 60,
            initialDelayMs: Long = 2000
    ): VeoApiResult<JobStatusResponse> =
            withContext(Dispatchers.IO) {
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
                                        return@withContext VeoApiResult.Error(
                                                "Video generation failed: $error"
                                        )
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
                                Log.w(
                                        TAG,
                                        "Failed to get job status (attempt ${retryCount + 1}): ${statusResult.message}"
                                )
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

    /** Download video with retry logic */
    private suspend fun downloadVideoWithRetry(signedUrl: String, maxRetries: Int = 3): ByteArray =
            withContext(Dispatchers.IO) {
                var lastException: Exception? = null

                val providerConfig = ConfigManager.getProviderConfig()
                val apiKeyOrNull =
                        if (providerConfig.provider == ApiProvider.GOOGLE_AI_STUDIO) {
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

    /** Check YouTube authentication status */
    private suspend fun checkYouTubeAuth(): VeoApiResult<Unit> =
            withContext(Dispatchers.IO) {
                return@withContext VeoApiResult.Error("YouTube integration is disabled.")

            }

    private suspend fun fetchYouTubeAuthUrl(baseUrl: String, firebaseToken: String): String =
            withContext(Dispatchers.IO) {
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
                    val responseBody =
                            if (responseCode == HttpURLConnection.HTTP_OK) {
                                BufferedReader(InputStreamReader(connection.inputStream)).use {
                                    it.readText()
                                }
                            } else {
                                BufferedReader(
                                                InputStreamReader(
                                                        connection.errorStream
                                                                ?: connection.inputStream
                                                )
                                        )
                                        .use { it.readText() }
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

    /** Upload video to YouTube via backend */
    private suspend fun uploadToYouTubeViaBackend(_signedVideoUrl: String, _title: String): String =
            withContext(Dispatchers.IO) {
                throw Exception("YouTube upload is disabled.")

            }

    /** Download bytes with manual redirect handling */
    private suspend fun downloadBytesWithRedirects(
            signedUrl: String,
            apiKeyOrNull: String?
    ): ByteArray =
            withContext(Dispatchers.IO) {
                var currentUrl = signedUrl
                var attachKey = apiKeyOrNull != null && shouldAttachGoogleApiKey(currentUrl)

                Log.d(TAG, "=== VIDEO DOWNLOAD DEBUG ===")
                Log.d(TAG, "Project: ${ConfigManager.appName}")
                Log.d(TAG, "Initial URL: $currentUrl")
                if (apiKeyOrNull != null) {
                    val keySuffix = apiKeyOrNull.takeLast(3)
                    Log.d(TAG, "API Key provided (ends with ...$keySuffix)")
                } else {
                    Log.d(TAG, "API Key not provided")
                }
                Log.d(TAG, "Attach key to URL: $attachKey")

                repeat(6) { redirectCount ->
                    Log.d(TAG, "Download attempt $redirectCount: $currentUrl (attachKey=$attachKey)")
                    
                    val conn =
                            (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                                requestMethod = "GET"
                                instanceFollowRedirects = false
                                connectTimeout = 30000
                                readTimeout = 120000
                                if (attachKey && apiKeyOrNull != null) {
                                    setRequestProperty("x-goog-api-key", apiKeyOrNull)
                                    Log.d(TAG, "Added x-goog-api-key header")
                                }
                            }

                    val code = conn.responseCode
                    when {
                        code in 200..299 -> return@withContext conn.inputStream.readBytes()
                        code in 300..399 -> {
                            val location =
                                    conn.getHeaderField("Location")
                                            ?: throw Exception("Redirect without Location header")
                            currentUrl = location
                            // After first redirect, URL is usually signed
                            attachKey = false
                        }
                        else -> {
                            val err =
                                    BufferedReader(
                                                    InputStreamReader(
                                                            conn.errorStream ?: conn.inputStream
                                                    )
                                            )
                                            .use { it.readText() }
                            throw Exception(
                                    "Failed to download video: HTTP $code: ${err.take(300)}"
                            )
                        }
                    }

                    if (redirectCount == 5) {
                        throw Exception("Too many redirects while downloading video")
                    }
                }

                throw Exception("Failed to download video after redirects")
            }

    private fun shouldAttachGoogleApiKey(url: String): Boolean {
        return url.startsWith("https://generativelanguage.googleapis.com/")
    }

    /** Enhance prompt using Gemini 2.0 Flash for better translation and optimization */
    suspend fun enhancePrompt(prompt: String): String =
            withContext(Dispatchers.IO) {
                try {
                    val providerConfig = ConfigManager.getProviderConfig()
                    val apiKey = providerConfig.apiKey
                    if (apiKey.isBlank()) {
                        Log.e(TAG, "No API key configured for prompt enhancement")
                        return@withContext prompt
                    }

                    // Use Gemini 2.0 Flash for better translation and enhancement
                    val url =
                            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey"

                    val systemInstruction = """You are an expert video prompt translator and optimizer for Google Veo 3.1 AI video generation.

YOUR ONLY JOB: Take the user's input (in ANY language) and output a professional English video prompt optimized for Veo 3.1.

CRITICAL RULES:
1. ALWAYS translate to English - never output Arabic, Chinese, or any other language
2. Output ONLY the enhanced prompt - no explanations, no labels, no introductions
3. Keep it under 300 words and under 1000 characters
4. Use professional cinematography terminology

STRUCTURE YOUR OUTPUT LIKE THIS EXAMPLE:
"A cinematic shot of [subject description], [action/pose], in a [environment/setting]. [Lighting description]. [Camera movement if applicable]. [Style keywords: realistic, 4K, detailed, cinematic, etc.]"

INCLUDE THESE ELEMENTS:
- Subject: Age, appearance, clothing, expression, pose
- Environment: Location, time of day, weather, atmosphere
- Technical: Lighting (soft, dramatic, natural), camera angle, movement
- Style: 4K, cinematic, realistic, detailed, film grain, bokeh

DO NOT:
- Keep any non-English text
- Add labels like "Enhanced prompt:" or "Here is the prompt:"
- Include explanations or commentary
- Repeat the original prompt"""

                    val jsonBody = JSONObject().apply {
                        put("system_instruction", JSONObject().apply {
                            put("parts", org.json.JSONArray().put(
                                JSONObject().put("text", systemInstruction)
                            ))
                        })
                        put("contents", org.json.JSONArray().put(
                            JSONObject().put("parts", org.json.JSONArray().put(
                                JSONObject().put("text", "Translate and enhance this video prompt for Veo 3.1:\n\n$prompt")
                            ))
                        ))
                        put("generationConfig", JSONObject().apply {
                            put("temperature", 0.7)
                            put("maxOutputTokens", 500)
                        })
                    }

                    Log.d(TAG, "Sending prompt enhancement request to Gemini 2.0 Flash")

                    val conn =
                            (URL(url).openConnection() as HttpURLConnection).apply {
                                requestMethod = "POST"
                                setRequestProperty("Content-Type", "application/json")
                                doOutput = true
                                connectTimeout = 30000
                                readTimeout = 30000
                            }

                    OutputStreamWriter(conn.outputStream).use { it.write(jsonBody.toString()) }

                    if (conn.responseCode == 200) {
                        val response =
                                BufferedReader(InputStreamReader(conn.inputStream)).use {
                                    it.readText()
                                }
                        val responseJson = JSONObject(response)
                        val enhancedText =
                                responseJson
                                        .optJSONArray("candidates")
                                        ?.optJSONObject(0)
                                        ?.optJSONObject("content")
                                        ?.optJSONArray("parts")
                                        ?.optJSONObject(0)
                                        ?.optString("text")

                        val result = enhancedText?.trim() ?: prompt
                        Log.d(TAG, "Enhanced prompt result: $result")
                        return@withContext result
                    } else {
                        val errorBody = BufferedReader(InputStreamReader(conn.errorStream)).use { it.readText() }
                        Log.e(TAG, "Enhance prompt failed: code ${conn.responseCode}, error: $errorBody")
                        return@withContext prompt
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Enhance prompt failed with exception", e)
                    return@withContext prompt
                }
            }
}
