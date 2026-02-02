
package com.example.chat_ui.data.firebase

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Firebase Storage Manager
 */
object FirebaseStorageManager {
    private const val TAG = "FirebaseStorageManager"
    
    /**
     * Upload image to Firebase Storage
     */
    suspend fun uploadImage(imageUri: Uri, folder: String = "images"): Result<String> {
        return try {
            val userId = FirebaseManager.getCurrentUserId() ?: return Result.failure(
                Exception("User not signed in")
            )
            
            val filename = "${UUID.randomUUID()}.jpg"
            val storageRef = FirebaseManager.storage
                .getReference("$folder/$userId/$filename")
            
            // Upload file
            storageRef.putFile(imageUri).await()
            
            // Get download URL
            val downloadUrl = storageRef.downloadUrl.await()
            
            Log.i(TAG, "Image uploaded successfully: $downloadUrl")
            Result.success(downloadUrl.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload image: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Upload file to Firebase Storage
     */
    suspend fun uploadFile(
        fileUri: Uri,
        folder: String = "files",
        filename: String? = null
    ): Result<String> {
        return try {
            val userId = FirebaseManager.getCurrentUserId() ?: return Result.failure(
                Exception("User not signed in")
            )
            
            val finalFilename = filename ?: "${UUID.randomUUID()}"
            val storageRef = FirebaseManager.storage
                .getReference("$folder/$userId/$finalFilename")
            
            // Upload file
            storageRef.putFile(fileUri).await()
            
            // Get download URL
            val downloadUrl = storageRef.downloadUrl.await()
            
            Log.i(TAG, "File uploaded successfully: $downloadUrl")
            Result.success(downloadUrl.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload file: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete file from Firebase Storage
     */
    suspend fun deleteFile(fileUrl: String): Result<Unit> {
        return try {
            val storageRef = FirebaseManager.storage.getReferenceFromUrl(fileUrl)
            storageRef.delete().await()
            
            Log.i(TAG, "File deleted successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete file: ${e.message}", e)
            Result.failure(e)
        }
    }
}
