package com.example.chat_ui.ui.video

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.chat_ui.R
import com.example.chat_ui.api.VeoVideoClient
import com.example.chat_ui.databinding.ItemVideoGalleryBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * VideoGalleryAdapter - RecyclerView adapter for video gallery
 * 
 * Features:
 * - Grid layout support with video thumbnails
 * - Public/Private visibility indicators
 * - Video duration and timestamp display
 * - Action buttons: Play, Share, Delete, Regenerate
 * - Click listeners for all actions
 * - DiffUtil for efficient updates
 * - Glide for thumbnail loading
 */
class VideoGalleryAdapter(
    private val onVideoClick: (VeoVideoClient.VideoGenerationResult) -> Unit,
    private val onShareClick: (VeoVideoClient.VideoGenerationResult) -> Unit,
    private val onDeleteClick: (VeoVideoClient.VideoGenerationResult) -> Unit,
    private val onRegenerateClick: (VeoVideoClient.VideoGenerationResult) -> Unit
) : ListAdapter<VeoVideoClient.VideoGenerationResult, VideoGalleryAdapter.VideoViewHolder>(VideoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemVideoGalleryBinding.inflate(
            LayoutInflater.from(parent.context), 
            parent, 
            false
        )
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VideoViewHolder(
        private val binding: ItemVideoGalleryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(videoResult: VeoVideoClient.VideoGenerationResult) {
            // Video thumbnail
            Glide.with(binding.videoThumbnail.context)
                .load(generateThumbnailUrl(videoResult.url))
                .placeholder(R.drawable.ic_video)
                .error(R.drawable.ic_video)
                .centerCrop()
                .into(binding.videoThumbnail)
            
            // Video info
            binding.videoTitle.text = videoResult.prompt
            binding.videoDuration.text = "${videoResult.duration}s"
            binding.videoTimestamp.text = formatTimestamp(videoResult.createdAt)
            
            // Visibility indicator
            when (videoResult.visibility) {
                VeoVideoClient.VideoVisibility.PUBLIC -> {
                    binding.visibilityIndicator.visibility = View.VISIBLE
                    binding.visibilityIndicator.setImageResource(R.drawable.ic_public)
                    binding.visibilityIndicator.contentDescription = "Public Video"
                }
                VeoVideoClient.VideoVisibility.PRIVATE -> {
                    binding.visibilityIndicator.visibility = View.VISIBLE
                    binding.visibilityIndicator.setImageResource(R.drawable.ic_private)
                    binding.visibilityIndicator.contentDescription = "Private Video"
                }
                else -> {
                    binding.visibilityIndicator.visibility = View.GONE
                }
            }
            
            // Mode indicator (simplified since VideoGenerationResult doesn't have mode)
            binding.modeIndicator.text = "GEN"
            
            // Quality indicator (simplified since VideoGenerationResult doesn't have quality)
            binding.qualityIndicator.text = "HD"
            
            // Click listeners
            binding.root.setOnClickListener {
                onVideoClick(videoResult)
            }
            
            binding.shareButton.setOnClickListener {
                onShareClick(videoResult)
            }
            
            // Only show share button for public videos
            binding.shareButton.visibility = if (videoResult.visibility == VeoVideoClient.VideoVisibility.PUBLIC) {
                View.VISIBLE
            } else {
                View.GONE
            }
            
            binding.deleteButton.setOnClickListener {
                onDeleteClick(videoResult)
            }
            
            binding.regenerateButton.setOnClickListener {
                onRegenerateClick(videoResult)
            }
        }
        
        private fun generateThumbnailUrl(videoUrl: String?): String? {
            // Generate thumbnail URL based on video URL
            return when {
                videoUrl?.contains("youtube.com") == true || videoUrl?.contains("youtu.be") == true -> {
                    // Extract YouTube video ID and generate thumbnail
                    val videoId = extractYouTubeVideoId(videoUrl)
                    if (videoId != null) {
                        "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                    } else null
                }
                videoUrl?.contains("firebase") == true || videoUrl?.contains("storage.googleapis.com") == true -> {
                    // For Firebase Storage videos, we'd need to generate thumbnails
                    // For now, return null to show placeholder
                    null
                }
                else -> null
            }
        }
        
        private fun extractYouTubeVideoId(url: String): String? {
            val patterns = listOf(
                "(?<=watch\\?v=)[^&]+".toRegex(),
                "(?<=youtu.be/)[^?&]+".toRegex(),
                "(?<=embed/)[^?&]+".toRegex()
            )
            
            for (pattern in patterns) {
                val match = pattern.find(url)
                if (match != null) {
                    return match.value
                }
            }
            return null
        }
        
        private fun formatTimestamp(timestamp: Long?): String {
            if (timestamp == null) return ""
            
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            
            return when {
                diff < 60000 -> "Just now"
                diff < 3600000 -> "${diff / 60000}m ago"
                diff < 86400000 -> "${diff / 3600000}h ago"
                diff < 604800000 -> "${diff / 86400000}d ago"
                else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
            }
        }
    }

    class VideoDiffCallback : DiffUtil.ItemCallback<VeoVideoClient.VideoGenerationResult>() {
        override fun areItemsTheSame(
            oldItem: VeoVideoClient.VideoGenerationResult,
            newItem: VeoVideoClient.VideoGenerationResult
        ): Boolean {
            return oldItem.url == newItem.url
        }

        override fun areContentsTheSame(
            oldItem: VeoVideoClient.VideoGenerationResult,
            newItem: VeoVideoClient.VideoGenerationResult
        ): Boolean {
            return oldItem == newItem
        }
    }
}
