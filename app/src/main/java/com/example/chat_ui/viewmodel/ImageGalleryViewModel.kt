package com.example.chat_ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat_ui.data.cloud.CloudinaryManager
import com.example.chat_ui.data.firebase.FirebaseDatabaseManager
import com.example.chat_ui.data.firebase.FirestoreManager
import com.example.chat_ui.data.models.GeneratedImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Memory-safe image gallery ViewModel.
 *
 * The source of truth for the gallery is Realtime Database:
 * /images/{uid}/{imageId}
 *
 * The ViewModel intentionally stores metadata only. Bitmaps are never kept in ViewModel state.
 */
class ImageGalleryViewModel : ViewModel() {

    companion object {
        private const val TAG = "ImageGalleryViewModel"
        private const val MAX_IMAGES_IN_MEMORY = 60
    }

    private val _images = MutableStateFlow<List<GeneratedImage>>(emptyList())
    val images: StateFlow<List<GeneratedImage>> = _images.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _modelFilter = MutableStateFlow<String?>(null)

    private var allImages: List<GeneratedImage> = emptyList()
    private var galleryJob: Job? = null

    init {
        startListening()
    }

    private fun startListening() {
        galleryJob?.cancel()
        galleryJob = viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            FirebaseDatabaseManager.getGeneratedImages(limit = MAX_IMAGES_IN_MEMORY)
                .catch { e ->
                    Log.e(TAG, "Error loading images from Realtime Database", e)
                    _errorMessage.value = "Failed to load images: ${e.message}"
                    _isLoading.value = false
                }
                .collect { rawImages ->
                    allImages = rawImages
                        .asSequence()
                        .mapNotNull(::mapRealtimeImage)
                        .filter { it.cloudinaryUrl.isNotBlank() }
                        .distinctBy { it.id.ifBlank { it.cloudinaryUrl } }
                        .sortedByDescending { it.createdAt }
                        .take(MAX_IMAGES_IN_MEMORY)
                        .toList()

                    applyFilter()
                    _isLoading.value = false
                    Log.d(TAG, "Loaded ${allImages.size} generated images")
                }
        }
    }

    fun refreshImages() {
        startListening()
    }

    fun setModelFilter(model: String?) {
        _modelFilter.value = model
        applyFilter()
    }

    private fun applyFilter() {
        val filter = _modelFilter.value
        _images.value = if (filter.isNullOrBlank()) {
            allImages
        } else {
            allImages.filter { it.modelUsed == filter }
        }
    }

    fun deleteImage(image: GeneratedImage) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null

                if (image.cloudinaryPublicId.isNotBlank()) {
                    try {
                        CloudinaryManager.deleteImage(image.cloudinaryPublicId)
                    } catch (e: Exception) {
                        Log.w(TAG, "Cloudinary delete failed; continuing: ${e.message}")
                    }
                }

                val realtimeDeleted = FirebaseDatabaseManager.deleteGeneratedImage(image.id)
                val firestoreDeleted = runCatching { FirestoreManager.deleteGeneratedImage(image.id) }.getOrDefault(false)

                if (!realtimeDeleted && !firestoreDeleted) {
                    throw IllegalStateException("Failed to delete image metadata")
                }

                allImages = allImages.filterNot { it.id == image.id }
                applyFilter()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete image", e)
                _errorMessage.value = "Failed to delete image: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun getImageCountByModel(): Map<String, Int> {
        return allImages.groupBy { it.modelUsed }.mapValues { it.value.size }
    }

    fun getTotalImagesCount(): Int = allImages.size

    override fun onCleared() {
        galleryJob?.cancel()
        super.onCleared()
    }

    private fun mapRealtimeImage(data: Map<String, Any>): GeneratedImage? {
        val id = data.stringValue("id").ifBlank { data.stringValue("imageId") }
        val url = data.stringValue("imageUrl").ifBlank {
            data.stringValue("cloudinaryUrl").ifBlank { data.stringValue("url") }
        }

        if (id.isBlank() || url.isBlank()) return null

        return GeneratedImage(
            id = id,
            userId = data.stringValue("userId").ifBlank { null },
            prompt = data.stringValue("prompt"),
            cloudinaryUrl = url,
            cloudinaryPublicId = data.stringValue("cloudinaryPublicId"),
            firebaseUrl = data.stringValue("firebaseUrl").ifBlank { null },
            width = data.intValue("width"),
            height = data.intValue("height"),
            modelUsed = data.stringValue("model").ifBlank { data.stringValue("modelUsed") },
            createdAt = data.longValue("createdAt") ?: 0L,
            updatedAt = data.longValue("updatedAt") ?: data.longValue("createdAt") ?: 0L
        )
    }

    private fun Map<String, Any>.stringValue(key: String): String {
        return (this[key] as? String)?.trim().orEmpty()
    }

    private fun Map<String, Any>.longValue(key: String): Long? {
        return when (val value = this[key]) {
            is Long -> value
            is Int -> value.toLong()
            is Double -> value.toLong()
            is Float -> value.toLong()
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }

    private fun Map<String, Any>.intValue(key: String): Int? {
        return when (val value = this[key]) {
            is Int -> value
            is Long -> value.toInt()
            is Double -> value.toInt()
            is Float -> value.toInt()
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }
}
