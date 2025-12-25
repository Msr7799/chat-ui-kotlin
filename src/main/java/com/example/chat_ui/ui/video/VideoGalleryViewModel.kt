package com.example.chat_ui.ui.video

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.chat_ui.api.VeoVideoClient
import com.example.chat_ui.data.firebase.FirebaseManager
import com.example.chat_ui.utils.FirebaseAuthHelper
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * VideoGallery ViewModel - Manages video list and operations
 * 
 * Features:
 * - Load videos from Firebase Storage (Private) and YouTube (Public)
 * - Filter videos by visibility (All/Public/Private)
 * - Delete videos with confirmation
 * - Reactive UI updates with LiveData
 * - Error handling and loading states
 * - Pull-to-refresh support
 */
class VideoGalleryViewModel(application: Application) : AndroidViewModel(application) {
    
    private val _videos = MutableLiveData<List<VeoVideoClient.VideoGenerationResult>>()
    val videos: LiveData<List<VeoVideoClient.VideoGenerationResult>> = _videos
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    
    private val _errorType = MutableLiveData<ErrorType?>()
    val errorType: LiveData<ErrorType?> = _errorType
    
    private val _currentFilter = MutableLiveData<VideoFilter>()
    val currentFilter: LiveData<VideoFilter> = _currentFilter
    
    private var allVideos: List<VeoVideoClient.VideoGenerationResult> = emptyList()
    private var lastDocument: com.google.firebase.firestore.DocumentSnapshot? = null
    private var hasMorePages = true
    
    companion object {
        private const val PAGE_SIZE = 20
    }
    
    enum class VideoFilter {
        ALL, PUBLIC, PRIVATE
    }
    
    enum class ErrorType {
        GENERAL,
        INDEX_MISSING,
        AUTH_REQUIRED,
        NETWORK_ERROR
    }
    
    init {
        _currentFilter.value = VideoFilter.ALL
    }
    
    /**
     * Load videos from storage with pagination support
     * @param refresh If true, reset pagination and load from beginning
     */
    fun loadVideos(refresh: Boolean = true) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                _errorType.value = null

                val userId = FirebaseAuthHelper.getCurrentUserUid()
                if (userId == null) {
                    allVideos = emptyList()
                    applyFilter(_currentFilter.value ?: VideoFilter.ALL)
                    _errorMessage.value = "Please sign in to view your videos"
                    _errorType.value = ErrorType.AUTH_REQUIRED
                    return@launch
                }

                // Reset pagination on refresh
                if (refresh) {
                    lastDocument = null
                    hasMorePages = true
                    allVideos = emptyList()
                }

                // Build query with pagination
                // Try with orderBy first (requires index), fallback to simple query if index missing
                var snapshot: com.google.firebase.firestore.QuerySnapshot? = null
                var usedFallback = false
                
                try {
                    var query = FirebaseManager.firestore
                        .collection("generated_videos")
                        .whereEqualTo("userId", userId)
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .limit(PAGE_SIZE.toLong())

                    // Apply pagination cursor if loading more
                    if (!refresh && lastDocument != null) {
                        query = query.startAfter(lastDocument!!)
                    }

                    snapshot = query.get().await()
                } catch (e: Exception) {
                    // If index is missing, try fallback query without orderBy
                    if (e.message?.contains("FAILED_PRECONDITION") == true || 
                        e.message?.contains("index") == true ||
                        e.message?.contains("currently building") == true) {
                        Log.w("VideoGalleryVM", "Index missing, using fallback query without orderBy", e)
                        usedFallback = true
                        
                        // Fallback: Simple query without orderBy (no index required)
                        // Note: Pagination is disabled for fallback (loads all matching documents)
                        val fallbackQuery = FirebaseManager.firestore
                            .collection("generated_videos")
                            .whereEqualTo("userId", userId)
                            .limit(100) // Load more in fallback mode since we can't paginate efficiently
                        
                        snapshot = fallbackQuery.get().await()
                        
                        // Sort in memory (less efficient but works without index)
                        val sortedDocs = snapshot.documents.sortedByDescending { doc ->
                            (doc.data?.get("createdAt") as? Number)?.toLong() ?: 0L
                        }
                        
                        // Process sorted documents
                        val videos = sortedDocs.mapNotNull { doc ->
                            try {
                                val data = doc.data ?: return@mapNotNull null
                                val visibility =
                                    when (data["visibility"] as? String) {
                                        "PUBLIC" -> VeoVideoClient.VideoVisibility.PUBLIC
                                        "PRIVATE" -> VeoVideoClient.VideoVisibility.PRIVATE
                                        else -> VeoVideoClient.VideoVisibility.PRIVATE
                                    }
                                VeoVideoClient.VideoGenerationResult(
                                    id = (data["id"] as? String) ?: doc.id,
                                    url = (data["url"] as? String) ?: return@mapNotNull null,
                                    prompt = (data["prompt"] as? String) ?: "",
                                    visibility = visibility,
                                    duration = ((data["duration"] as? Number)?.toInt()) ?: 0,
                                    aspectRatio = (data["aspectRatio"] as? String) ?: "16:9",
                                    createdAt = ((data["createdAt"] as? Number)?.toLong()) ?: 0L,
                                    jobId = (data["jobId"] as? String) ?: ""
                                )
                            } catch (e: Exception) {
                                Log.e("VideoGalleryVM", "Failed to parse video doc ${doc.id}", e)
                                null
                            }
                        }
                        
                        // Update pagination state (disabled for fallback)
                        hasMorePages = false
                        lastDocument = null
                        
                        // Replace videos (fallback doesn't support pagination)
                        allVideos = videos
                        applyFilter(_currentFilter.value ?: VideoFilter.ALL)
                        
                        // Show warning about index (non-blocking)
                        _errorMessage.value = getApplication<Application>().getString(
                            com.example.chat_ui.R.string.firestore_index_building_error
                        )
                        _errorType.value = ErrorType.INDEX_MISSING
                        return@launch
                    } else {
                        throw e // Re-throw if it's not an index error
                    }
                }
                
                // Process normal query result
                if (snapshot == null) {
                    throw IllegalStateException("Query returned null")
                }

                val videos = snapshot.documents.mapNotNull { doc ->
                    try {
                        val data = doc.data ?: return@mapNotNull null
                        val visibility =
                            when (data["visibility"] as? String) {
                                "PUBLIC" -> VeoVideoClient.VideoVisibility.PUBLIC
                                "PRIVATE" -> VeoVideoClient.VideoVisibility.PRIVATE
                                else -> VeoVideoClient.VideoVisibility.PRIVATE
                            }
                        VeoVideoClient.VideoGenerationResult(
                            id = (data["id"] as? String) ?: doc.id,
                            url = (data["url"] as? String) ?: return@mapNotNull null,
                            prompt = (data["prompt"] as? String) ?: "",
                            visibility = visibility,
                            duration = ((data["duration"] as? Number)?.toInt()) ?: 0,
                            aspectRatio = (data["aspectRatio"] as? String) ?: "16:9",
                            createdAt = ((data["createdAt"] as? Number)?.toLong()) ?: 0L,
                            jobId = (data["jobId"] as? String) ?: ""
                        )
                    } catch (e: Exception) {
                        Log.e("VideoGalleryVM", "Failed to parse video doc ${doc.id}", e)
                        null
                    }
                }

                // Update pagination state
                if (snapshot.documents.isNotEmpty()) {
                    lastDocument = snapshot.documents.last()
                    hasMorePages = snapshot.documents.size == PAGE_SIZE
                } else {
                    hasMorePages = false
                }

                // Append or replace videos
                allVideos = if (refresh) videos else allVideos + videos
                applyFilter(_currentFilter.value ?: VideoFilter.ALL)
                
            } catch (e: Exception) {
                Log.e("VideoGalleryVM", "Failed to load videos", e)
                
                // Detect specific error types
                when {
                    e.message?.contains("FAILED_PRECONDITION") == true || 
                    e.message?.contains("index") == true ||
                    e.message?.contains("currently building") == true -> {
                        _errorMessage.value = getApplication<Application>().getString(
                            com.example.chat_ui.R.string.firestore_index_building_error
                        )
                        _errorType.value = ErrorType.INDEX_MISSING
                    }
                    e.message?.contains("PERMISSION_DENIED") == true ||
                    e.message?.contains("permission") == true -> {
                        _errorMessage.value = getApplication<Application>().getString(
                            com.example.chat_ui.R.string.firestore_permission_error
                        )
                        _errorType.value = ErrorType.AUTH_REQUIRED
                    }
                    e.message?.contains("network") == true ||
                    e.message?.contains("timeout") == true ||
                    e.message?.contains("UNAVAILABLE") == true -> {
                        _errorMessage.value = getApplication<Application>().getString(
                            com.example.chat_ui.R.string.network_error_message
                        )
                        _errorType.value = ErrorType.NETWORK_ERROR
                    }
                    else -> {
                        _errorMessage.value = getApplication<Application>().getString(
                            com.example.chat_ui.R.string.video_load_error,
                            e.message ?: "Unknown error"
                        )
                        _errorType.value = ErrorType.GENERAL
                    }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Load more videos (pagination)
     */
    fun loadMoreVideos() {
        if (!hasMorePages || _isLoading.value == true) return
        loadVideos(refresh = false)
    }
    
    /**
     * Filter videos by visibility
     */
    fun setFilter(filter: VideoFilter) {
        _currentFilter.value = filter
        applyFilter(filter)
    }
    
    private fun applyFilter(filter: VideoFilter) {
        val filteredVideos = when (filter) {
            VideoFilter.ALL -> allVideos
            VideoFilter.PUBLIC -> allVideos.filter { it.visibility == VeoVideoClient.VideoVisibility.PUBLIC }
            VideoFilter.PRIVATE -> allVideos.filter { it.visibility == VeoVideoClient.VideoVisibility.PRIVATE }
        }
        _videos.value = filteredVideos
    }
    
    /**
     * Delete video from storage and metadata
     * 
     * Order: Storage first, then Firestore (to prevent orphan files)
     */
    fun deleteVideo(videoResult: VeoVideoClient.VideoGenerationResult) {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                val userId = FirebaseAuthHelper.getCurrentUserUid()
                if (userId == null) {
                    _errorMessage.value = "Please sign in to delete videos"
                    return@launch
                }

                // 1. Delete from Firebase Storage first (if private and from Firebase)
                if (videoResult.visibility == VeoVideoClient.VideoVisibility.PRIVATE) {
                    try {
                        deleteFromStorageIfFirebaseUrl(videoResult.url)
                    } catch (e: Exception) {
                        Log.w("VideoGalleryVM", "Failed to delete storage object: ${e.message}")
                        // Continue anyway - maybe file was already deleted
                    }
                }
                
                // 2. Delete from YouTube if public (TODO: implement YouTube delete via API)
                if (videoResult.visibility == VeoVideoClient.VideoVisibility.PUBLIC) {
                    // TODO: YouTube Data API v3 delete requires OAuth
                    Log.w("VideoGalleryVM", "YouTube video delete not yet implemented - URL: ${videoResult.url}")
                }

                // 3. Delete metadata from Firestore (last, after storage cleanup)
                FirebaseManager.firestore
                    .collection("generated_videos")
                    .document(videoResult.id)
                    .delete()
                    .await()

                // Update local list
                allVideos = allVideos.filter { it.id != videoResult.id }
                applyFilter(_currentFilter.value ?: VideoFilter.ALL)
                
            } catch (e: Exception) {
                Log.e("VideoGalleryVM", "Failed to delete video", e)
                _errorMessage.value = "Failed to delete video: ${e.message}"
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
        _errorType.value = null
    }
    
    /**
     * Helper: Delete file from Firebase Storage if URL is from Firebase
     */
    private suspend fun deleteFromStorageIfFirebaseUrl(url: String) {
        if (!url.contains("firebasestorage.googleapis.com") && !url.startsWith("gs://")) {
            Log.d("VideoGalleryVM", "URL is not Firebase Storage, skipping: $url")
            return
        }
        
        try {
            val ref = FirebaseStorage.getInstance().getReferenceFromUrl(url)
            ref.delete().await()
            Log.i("VideoGalleryVM", "Successfully deleted storage file: $url")
        } catch (e: Exception) {
            Log.e("VideoGalleryVM", "Failed to delete from Storage: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Generate mock videos for testing
     * TODO: Replace with actual Firebase/YouTube data loading
     */
    private fun generateMockVideos(): List<VeoVideoClient.VideoGenerationResult> {
        return listOf(
            VeoVideoClient.VideoGenerationResult(
                id = "video_1",
                url = "https://example.com/video1.mp4",
                prompt = "A beautiful sunset over the ocean",
                visibility = VeoVideoClient.VideoVisibility.PRIVATE,
                duration = 8,
                aspectRatio = "9:16",
                createdAt = System.currentTimeMillis() - 3600000, // 1 hour ago
                jobId = "job_1"
            ),
            VeoVideoClient.VideoGenerationResult(
                id = "video_2",
                url = "https://youtube.com/watch?v=abc123",
                prompt = "Futuristic city with flying cars",
                visibility = VeoVideoClient.VideoVisibility.PUBLIC,
                duration = 6,
                aspectRatio = "16:9",
                createdAt = System.currentTimeMillis() - 7200000, // 2 hours ago
                jobId = "job_2"
            ),
            VeoVideoClient.VideoGenerationResult(
                id = "video_3",
                url = "https://example.com/video3.mp4",
                prompt = "Dancing robot in space",
                visibility = VeoVideoClient.VideoVisibility.PRIVATE,
                duration = 10,
                aspectRatio = "1:1",
                createdAt = System.currentTimeMillis() - 10800000, // 3 hours ago
                jobId = "job_3"
            ),
            VeoVideoClient.VideoGenerationResult(
                id = "video_4",
                url = "https://youtube.com/watch?v=xyz789",
                prompt = "Mountain landscape time-lapse",
                visibility = VeoVideoClient.VideoVisibility.PUBLIC,
                duration = 4,
                aspectRatio = "16:9",
                createdAt = System.currentTimeMillis() - 14400000, // 4 hours ago
                jobId = "job_4"
            )
        )
    }
}
