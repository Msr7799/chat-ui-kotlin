package com.example.chat_ui.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import com.example.chat_ui.data.Attachment
import com.example.chat_ui.data.AttachmentType
import com.example.chat_ui.data.cloud.CloudinaryManager
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Manager for handling file attachments Similar to src/lib/server/files/uploadFile.ts */
object FileAttachmentManager {
    private const val TAG = "FileAttachmentManager"

    // Supported MIME types matching JavaScript constants
    val IMAGE_MIME_TYPES =
            listOf("image/jpeg", "image/png", "image/gif", "image/webp", "image/svg+xml")

    val TEXT_MIME_TYPES =
            listOf(
                    "text/plain",
                    "text/markdown",
                    "text/csv",
                    "application/json",
                    "application/xml",
                    "text/html",
                    "text/css",
                    "text/javascript",
                    "application/pdf"
            )

    /** Get file info from URI */
    fun getFileInfo(context: Context, uri: Uri): FileInfo? {
        return try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)

                    val name = if (nameIndex >= 0) it.getString(nameIndex) else "unknown"
                    val size = if (sizeIndex >= 0) it.getLong(sizeIndex) else 0L
                    val mimeType = context.contentResolver.getType(uri) ?: getMimeType(name)

                    FileInfo(name = name, size = size, mimeType = mimeType, uri = uri)
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get file info: ${e.message}", e)
            null
        }
    }

    /** Get MIME type from file extension */
    private fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast(".", "")
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                ?: "application/octet-stream"
    }

    /** Check if file is an image */
    fun isImage(mimeType: String): Boolean {
        return mimeType.startsWith("image/") || IMAGE_MIME_TYPES.contains(mimeType)
    }

    /** Check if file is a text file */
    fun isTextFile(mimeType: String): Boolean {
        return mimeType.startsWith("text/") || TEXT_MIME_TYPES.contains(mimeType)
    }

    /** Upload file to Cloudinary and return Attachment */
    suspend fun uploadFile(context: Context, uri: Uri): Result<Attachment> =
            withContext(Dispatchers.IO) {
                try {
                    val fileInfo =
                            getFileInfo(context, uri)
                                    ?: return@withContext Result.failure(
                                            Exception("Could not read file info")
                                    )

                    Log.i(TAG, "Uploading file: ${fileInfo.name} (${fileInfo.mimeType})")

                    // Upload to Cloudinary
                    try {
                        val cloudinaryResult = CloudinaryManager.uploadImage(context, uri)

                        val attachmentType =
                                when {
                                    isImage(fileInfo.mimeType) -> AttachmentType.IMAGE
                                    fileInfo.mimeType.startsWith("audio/") -> AttachmentType.AUDIO
                                    else -> AttachmentType.FILE
                                }

                        val attachment =
                                Attachment(
                                        id = cloudinaryResult.publicId,
                                        name = fileInfo.name,
                                        type = attachmentType,
                                        url = cloudinaryResult.url,
                                        mime = fileInfo.mimeType
                                )

                        Log.i(TAG, "File uploaded successfully: ${attachment.url}")
                        Result.success(attachment)
                    } catch (uploadError: Exception) {
                        Log.e(TAG, "Upload failed: ${uploadError.message}", uploadError)
                        Result.failure(uploadError)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Upload error: ${e.message}", e)
                    Result.failure(e)
                }
            }

    /** Create Attachment from base64 data (for clipboard paste) */
    fun createBase64Attachment(
            base64Data: String,
            mimeType: String,
            fileName: String = "pasted_image_${System.currentTimeMillis()}"
    ): Attachment {
        val attachmentType =
                when {
                    mimeType.startsWith("image/") -> AttachmentType.IMAGE
                    mimeType.startsWith("audio/") -> AttachmentType.AUDIO
                    else -> AttachmentType.FILE
                }

        return Attachment(
                id = UUID.randomUUID().toString(),
                name = fileName,
                type = attachmentType,
                url = "data:$mimeType;base64,$base64Data",
                mime = mimeType
        )
    }

    /** Copy URI content to temp file */
    suspend fun copyToTempFile(context: Context, uri: Uri): File? =
            withContext(Dispatchers.IO) {
                try {
                    val fileInfo = getFileInfo(context, uri) ?: return@withContext null
                    val tempFile =
                            File(
                                    context.cacheDir,
                                    "temp_${System.currentTimeMillis()}_${fileInfo.name}"
                            )

                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                    }

                    tempFile
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to copy file: ${e.message}", e)
                    null
                }
            }

    data class FileInfo(val name: String, val size: Long, val mimeType: String, val uri: Uri)
}
