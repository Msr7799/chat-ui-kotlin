package com.example.chat_ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat_ui.data.cloud.CloudinaryManager
import com.example.chat_ui.data.firebase.FirestoreManager
import com.example.chat_ui.data.models.GeneratedImage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ImageGallery ViewModel - Manages image list and operations
 * 
 * Features:
 * - Load images from Firestore with real-time updates
 * - Filter images by model
 * - Delete images (Cloudinary + Firestore)
 * - Refresh images
 * - Error handling and loading states
 * - Reactive UI updates with StateFlow
 */
class ImageGalleryViewModel : ViewModel() {
    
    companion object {
        private const val TAG = "ImageGalleryViewModel"
    }
    
    private val _images = MutableStateFlow<List<GeneratedImage>>(emptyList())
    val images: StateFlow<List<GeneratedImage>> = _images.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _modelFilter = MutableStateFlow<String?>(null)
    
    private var allImages: List<GeneratedImage> = emptyList()
    
    init {
        loadImages()
    }
    
    /**
     * Load all images from Firestore with real-time updates
     */
    private fun loadImages() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                FirestoreManager.getGeneratedImagesFlow()
                    .catch { e ->
                        Log.e(TAG, "Error loading images", e)
                        _errorMessage.value = "Failed to load images: ${e.message}"
                        _isLoading.value = false
                    }
                    .collect { imageList ->
                        allImages = imageList
                        applyFilter()
                        _isLoading.value = false
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load images", e)
                _errorMessage.value = "Failed to load images: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Refresh images (force reload)
     */
    fun refreshImages() {
        loadImages()
    }
    
    /**
     * Set model filter
     */
    fun setModelFilter(model: String?) {
        _modelFilter.value = model
        applyFilter()
    }
    
    /**
     * Apply current filter to images
     */
    private fun applyFilter() {
        val filter = _modelFilter.value
        _images.value = if (filter != null) {
            allImages.filter { it.modelUsed == filter }
        } else {
            allImages
        }
    }
    
    /**
     * Delete image from Cloudinary and Firestore
     */
    fun deleteImage(image: GeneratedImage) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                // 1. Delete from Cloudinary
                try {
                    CloudinaryManager.deleteImage(image.cloudinaryPublicId)
                    Log.i(TAG, "Image deleted from Cloudinary: ${image.cloudinaryPublicId}")
                } catch (e: Exception) {
                    // Log but don't fail - image might already be deleted
                    Log.w(TAG, "Cloudinary delete failed (continuing): ${e.message}")
                }
                
                // 2. Delete from Firestore
                val success = FirestoreManager.deleteGeneratedImage(image.id)
                if (!success) {
                    throw Exception("Failed to delete image from Firestore")
                }
                
                // 3. Update local list
                allImages = allImages.filter { it.id != image.id }
                applyFilter()
                
                Log.i(TAG, "Image deleted successfully: ${image.id}")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete image", e)
                _errorMessage.value = "Failed to delete image: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }
    
    /**
     * Get image count by model (for statistics)
     */
    fun getImageCountByModel(): Map<String, Int> {
        return allImages.groupBy { it.modelUsed }
            .mapValues { it.value.size }
    }
    
    /**
     * Get total images count
     */
    fun getTotalImagesCount(): Int {
        return allImages.size
    }
    
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ImageGalleryViewModel cleared")
    }
}

