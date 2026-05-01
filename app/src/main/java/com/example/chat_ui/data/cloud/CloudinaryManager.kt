package com.example.chat_ui.data.cloud

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.chat_ui.config.ConfigManager
import com.example.chat_ui.utils.FirebaseAuthHelper
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Result of a Cloudinary upload */
data class CloudinaryUploadResult(
        val url: String,
        val publicId: String,
        val width: Int,
        val height: Int,
        val format: String,
        val bytes: Long
)

/** Cloudinary Manager for image uploads Similar to src/lib/server/cloudinary.ts in Svelte */
object CloudinaryManager {
    private const val TAG = "CloudinaryManager"
    private var isInitialized = false

    /** Initialize Cloudinary SDK */
    fun init(context: Context) {
        if (isInitialized) return

        context.applicationContext.hashCode()
        isInitialized = true
        Log.i(TAG, "Cloudinary uploads are routed through backend")
    }

    /**
     * Upload image to Cloudinary
     * @param context Android context
     * @param imageUri URI of the image to upload
     * @param folder Folder in Cloudinary (default: chat-ui/kotlin)
     * @param tags Optional tags for the image
     */
    suspend fun uploadImage(
            context: Context,
            imageUri: Uri,
            folder: String? = null,
            tags: List<String>? = null
    ): CloudinaryUploadResult = withContext(Dispatchers.IO) {
        val uploadFolder =
                folder
                        ?: ConfigManager.get(
                                ConfigManager.Keys.CLOUDINARY_UPLOAD_FOLDER,
                                "chat-ui/kotlin"
                        )

        uploadViaBackend(
                context = context,
                uri = imageUri,
                resourceType = "image",
                folder = uploadFolder,
                tags = tags?.joinToString(",")
        )
    }

    /**
     * Upload a video to Cloudinary.
     *
     * Notes:
     * - Cloudinary video uploads require resource_type="video".
     * - If you prefer safer handling of secrets, use an unsigned upload preset or upload via your
     * backend.
     */
    suspend fun uploadVideo(
            context: Context,
            videoUri: Uri,
            folder: String? = null,
            tags: List<String>? = null
    ): CloudinaryUploadResult = withContext(Dispatchers.IO) {
        val uploadFolder =
                folder
                        ?: ConfigManager.get(
                                ConfigManager.Keys.CLOUDINARY_UPLOAD_FOLDER,
                                "chat-ui/kotlin/videos"
                        )
        uploadViaBackend(
                context = context,
                uri = videoUri,
                resourceType = "video",
                folder = uploadFolder,
                tags = tags?.joinToString(",")
                )
    }

    /** Upload image from byte array */
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
     * Delete image from Cloudinary Note: This requires server-side implementation for security In
     * production, this should go through your backend
     */
    fun deleteImage(@Suppress("UNUSED_PARAMETER") publicId: String) {
        Log.w(TAG, "Delete image requires server-side implementation for security")
        // MediaManager.get().cloudinary().uploader().destroy(publicId, emptyMap())
    }

    /** Generate optimized URL for image */
    fun getOptimizedUrl(publicId: String, width: Int? = null, height: Int? = null): String {
        val cloudName = ConfigManager.get(ConfigManager.Keys.CLOUDINARY_CLOUD_NAME)

        val transforms = mutableListOf<String>()
        width?.let { transforms.add("w_$it") }
        height?.let { transforms.add("h_$it") }
        transforms.add("c_fill")
        transforms.add("q_auto")
        transforms.add("f_auto")

        val transformStr =
                if (transforms.isNotEmpty()) {
                    transforms.joinToString(",") + "/"
                } else ""

        return "https://res.cloudinary.com/$cloudName/image/upload/$transformStr$publicId"
    }

    private suspend fun uploadViaBackend(
            context: Context,
            uri: Uri,
            resourceType: String,
            folder: String,
            tags: String? = null
    ): CloudinaryUploadResult = withContext(Dispatchers.IO) {
        val token = FirebaseAuthHelper.getFirebaseIdToken(forceRefresh = false)
                ?: throw IllegalStateException("Please sign in before uploading files")

        val backendBaseUrl = ConfigManager.getBaseUrlForProvider(com.example.chat_ui.data.ApiProvider.HUGGINGFACE).trimEnd('/')
        val boundary = "----ChatUI${UUID.randomUUID()}"
        val connection = (URL("$backendBaseUrl/cloudinary/upload").openConnection() as HttpURLConnection)

        connection.apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 120_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        val fileName = resolveFileName(uri, resourceType)
        connection.outputStream.use { out ->
            writeFormField(out, boundary, "resource_type", resourceType)
            writeFormField(out, boundary, "folder", folder)
            if (!tags.isNullOrBlank()) {
                writeFormField(out, boundary, "tags", tags)
            }
            writeFileField(out, boundary, "file", fileName, context.contentResolver.getType(uri) ?: "application/octet-stream")
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.copyTo(out)
            } ?: throw IllegalArgumentException("Unable to read URI: $uri")
            out.write("\r\n--$boundary--\r\n".toByteArray())
            out.flush()
        }

        val code = connection.responseCode
        val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.readText()
                .orEmpty()

        if (code !in 200..299) {
            throw RuntimeException("Backend Cloudinary upload failed: HTTP $code")
        }

        val json = JSONObject(response)
        CloudinaryUploadResult(
                url = json.optString("secure_url"),
                publicId = json.optString("public_id"),
                width = json.optInt("width", 0),
                height = json.optInt("height", 0),
                format = json.optString("format"),
                bytes = json.optLong("bytes", 0)
        )
    }

    private fun resolveFileName(uri: Uri, resourceType: String): String {
        val last = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        return last ?: "upload-${System.currentTimeMillis()}.$resourceType"
    }

    private fun writeFormField(out: OutputStream, boundary: String, name: String, value: String) {
        out.write("--$boundary\r\n".toByteArray())
        out.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
        out.write(value.toByteArray(Charsets.UTF_8))
        out.write("\r\n".toByteArray())
    }

    private fun writeFileField(out: OutputStream, boundary: String, name: String, filename: String, contentType: String) {
        out.write("--$boundary\r\n".toByteArray())
        out.write("Content-Disposition: form-data; name=\"$name\"; filename=\"$filename\"\r\n".toByteArray())
        out.write("Content-Type: $contentType\r\n\r\n".toByteArray())
    }
}
