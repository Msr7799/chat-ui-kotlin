package com.example.chat_ui.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * MessageFile - Matches chat-ui's MessageFile type
 * Used for sending files (images, documents) to AI models
 * 
 * Similar to: src/lib/types/Message.ts in chat-ui
 */
data class MessageFile(
    val type: FileDataType,
    val name: String,
    val value: String, // base64 data or hash
    val mime: String
) {
    enum class FileDataType {
        HASH,   // File stored on server, value is hash
        BASE64  // File as base64, value is base64 encoded data
    }
    
    /**
     * Check if this is an image file
     */
    fun isImage(): Boolean = mime.startsWith("image/")
    
    /**
     * Check if this is a text file
     */
    fun isTextFile(): Boolean = TEXT_MIME_ALLOWLIST.any { allowed ->
        val (aType, aSubtype) = allowed.lowercase().split("/")
        val (fType, fSubtype) = mime.lowercase().split("/").let { 
            if (it.size >= 2) it[0] to it[1] else it[0] to "*"
        }
        (aType == "*" || aType == fType) && (aSubtype == "*" || aSubtype == fSubtype)
    }
    
    /**
     * Check if this is a PDF file
     */
    fun isPdf(): Boolean = mime.lowercase() == "application/pdf"
    
    /**
     * Get base64 data URL for display
     */
    fun getDataUrl(): String {
        return if (type == FileDataType.BASE64) {
            "data:$mime;base64,$value"
        } else {
            value // Return hash/URL as-is
        }
    }
    
    /**
     * Get text content (for text files)
     */
    fun getTextContent(): String? {
        return if (type == FileDataType.BASE64 && isTextFile()) {
            try {
                String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8)
            } catch (e: Exception) {
                null
            }
        } else null
    }
    
    companion object {
        private const val TAG = "MessageFile"
        
        // Maximum file size: 8MB (prevents OOM errors)
        private const val MAX_FILE_SIZE_BYTES = 8 * 1024 * 1024
        
        // Supported image MIME types (matching chat-ui)
        val IMAGE_MIME_ALLOWLIST = listOf(
            "image/png",
            "image/jpeg",
            "image/jpg",
            "image/gif",
            "image/webp",
            "image/svg+xml"
        )
        
        // Supported text MIME types (matching chat-ui TEXT_MIME_ALLOWLIST)
        val TEXT_MIME_ALLOWLIST = listOf(
            "text/plain",
            "text/markdown",
            "text/csv",
            "text/html",
            "text/css",
            "text/javascript",
            "text/xml",
            "application/json",
            "application/xml",
            "application/javascript",
            "application/typescript",
            "application/x-yaml",
            "application/x-sh",
            "application/x-python",
            "application/pdf",  // PDF support - will be converted to text
            "text/x-python",
            "text/x-java",
            "text/x-kotlin",
            "text/x-c",
            "text/x-cpp",
            "text/x-csharp",
            "text/x-go",
            "text/x-rust",
            "text/x-swift"
        )
        
        // PDF MIME types for special handling
        val PDF_MIME_TYPES = listOf(
            "application/pdf"
        )
        
        /**
         * Create MessageFile from Android Uri
         */
        suspend fun fromUri(
            context: Context,
            uri: Uri,
            fileName: String,
            mimeType: String
        ): MessageFile? = withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext null
                
                // Read file with size limit to prevent OOM
                val bytes = inputStream.use { stream ->
                    val buffer = ByteArray(64 * 1024) // 64KB buffer
                    val output = ByteArrayOutputStream()
                    var totalRead = 0
                    
                    while (true) {
                        val read = stream.read(buffer)
                        if (read <= 0) break
                        
                        totalRead += read
                        if (totalRead > MAX_FILE_SIZE_BYTES) {
                            Log.e(TAG, "File too large: $totalRead bytes (max: $MAX_FILE_SIZE_BYTES)")
                            return@withContext null
                        }
                        
                        output.write(buffer, 0, read)
                    }
                    
                    output.toByteArray()
                }
                
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                
                MessageFile(
                    type = FileDataType.BASE64,
                    name = fileName,
                    value = base64,
                    mime = mimeType
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create MessageFile from Uri: ${e.message}", e)
                null
            }
        }
        
        /**
         * Create MessageFile from Bitmap (for camera captures)
         */
        suspend fun fromBitmap(
            bitmap: Bitmap,
            fileName: String = "camera_${System.currentTimeMillis()}.jpg"
        ): MessageFile = withContext(Dispatchers.IO) {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            val bytes = outputStream.toByteArray()
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            
            MessageFile(
                type = FileDataType.BASE64,
                name = fileName,
                value = base64,
                mime = "image/jpeg"
            )
        }
        
        /**
         * Create MessageFile from clipboard text
         */
        fun fromClipboardText(text: String): MessageFile {
            val base64 = Base64.encodeToString(
                text.toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP
            )
            return MessageFile(
                type = FileDataType.BASE64,
                name = "clipboard_${System.currentTimeMillis()}.txt",
                value = base64,
                mime = "application/vnd.chatui.clipboard"
            )
        }
    }
}
