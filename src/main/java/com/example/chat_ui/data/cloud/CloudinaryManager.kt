package com.example.chat_ui.data.cloud

import android.content.Context
import android.net.Uri
import android.util.Log
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.chat_ui.config.ConfigManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Result of a Cloudinary upload
 */
data class CloudinaryUploadResult(
    val url: String,
    val publicId: String,
    val width: Int,
    val height: Int,
    val format: String,
    val bytes: Long
)

/**
 * Cloudinary Manager for image uploads
 * Similar to src/lib/server/cloudinary.ts in Svelte
 */
object CloudinaryManager {
    private const val TAG = "CloudinaryManager"
    private var isInitialized = false
    
    /**
     * Initialize Cloudinary SDK
     */
    fun init(context: Context) {
        if (isInitialized) return
        
        try {
            val config = mapOf(
                "cloud_name" to ConfigManager.get(ConfigManager.Keys.CLOUDINARY_CLOUD_NAME),
                "api_key" to ConfigManager.get(ConfigManager.Keys.CLOUDINARY_API_KEY),
                "api_secret" to ConfigManager.get(ConfigManager.Keys.CLOUDINARY_API_SECRET)
            )
            
            MediaManager.init(context, config)
            isInitialized = true
            Log.i(TAG, "Cloudinary initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Cloudinary: ${e.message}", e)
        }
    }
    
    /**
     * Upload image to Cloudinary
     * @param context Android context
     * @param imageUri URI of the image to upload
     * @param folder Folder in Cloudinary (default: chat-ui/kotlin)
     * @param tags Optional tags for the image
     */
    suspend fun uploadImage(
        @Suppress("UNUSED_PARAMETER") context: Context,
        imageUri: Uri,
        folder: String? = null,
        tags: List<String>? = null
    ): CloudinaryUploadResult = suspendCancellableCoroutine { continuation ->
        
        val uploadFolder = folder ?: ConfigManager.get(
            ConfigManager.Keys.CLOUDINARY_UPLOAD_FOLDER,
            "chat-ui/kotlin"
        )
        
        val requestId = MediaManager.get().upload(imageUri)
            .option("folder", uploadFolder)
            .apply {
                tags?.let { option("tags", it.joinToString(",")) }
            }
            .callback(object : UploadCallback {
                override fun onStart(requestId: String) {
                    Log.d(TAG, "Upload started: $requestId")
                }
                
                override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {
                    val progress = if (totalBytes > 0) {
                        ((bytes * 100) / totalBytes).toInt()
                    } else {
                        0
                    }
                    Log.d(TAG, "Upload progress: $progress% (bytes=$bytes total=$totalBytes)")
                }
                
                override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                    Log.i(TAG, "Upload success: $resultData")
                    
                    val result = CloudinaryUploadResult(
                        url = resultData["secure_url"] as? String ?: "",
                        publicId = resultData["public_id"] as? String ?: "",
                        width = (resultData["width"] as? Number)?.toInt() ?: 0,
                        height = (resultData["height"] as? Number)?.toInt() ?: 0,
                        format = resultData["format"] as? String ?: "",
                        bytes = (resultData["bytes"] as? Number)?.toLong() ?: 0
                    )
                    
                    continuation.resume(result)
                }
                
                override fun onError(requestId: String, error: ErrorInfo) {
                    Log.e(TAG, "Upload error: ${error.description}")
                    continuation.resumeWithException(
                        Exception("Cloudinary upload failed: ${error.description}")
                    )
                }
                
                override fun onReschedule(requestId: String, error: ErrorInfo) {
                    Log.w(TAG, "Upload rescheduled: ${error.description}")
                }
            })
            .dispatch()
        
        continuation.invokeOnCancellation {
            MediaManager.get().cancelRequest(requestId)
        }
    }
    
    /**
     * Upload image from byte array
     */
    suspend fun uploadImageBytes(
        context: Context,
        imageBytes: ByteArray,
        fileName: String,
        folder: String? = null,
        tags: List<String>? = null
    ): CloudinaryUploadResult {
        // Save bytes to temp file and upload
        val tempFile = java.io.File(context.cacheDir, fileName)
        tempFile.writeBytes(imageBytes)
        
        return try {
            uploadImage(
                context = context,
                imageUri = Uri.fromFile(tempFile),
                folder = folder,
                tags = tags
            )
        } finally {
            tempFile.delete()
        }
    }
    
    /**
     * Delete image from Cloudinary
     * Note: This requires server-side implementation for security
     * In production, this should go through your backend
     */
    fun deleteImage(@Suppress("UNUSED_PARAMETER") publicId: String) {
        Log.w(TAG, "Delete image requires server-side implementation for security")
        // MediaManager.get().cloudinary().uploader().destroy(publicId, emptyMap())
    }
    
    /**
     * Generate optimized URL for image
     */
    fun getOptimizedUrl(publicId: String, width: Int? = null, height: Int? = null): String {
        val cloudName = ConfigManager.get(ConfigManager.Keys.CLOUDINARY_CLOUD_NAME)
        
        val transforms = mutableListOf<String>()
        width?.let { transforms.add("w_$it") }
        height?.let { transforms.add("h_$it") }
        transforms.add("c_fill")
        transforms.add("q_auto")
        transforms.add("f_auto")
        
        val transformStr = if (transforms.isNotEmpty()) {
            transforms.joinToString(",") + "/"
        } else ""
        
        return "https://res.cloudinary.com/$cloudName/image/upload/$transformStr$publicId"
    }
}
