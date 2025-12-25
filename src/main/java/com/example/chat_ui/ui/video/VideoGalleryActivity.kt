package com.example.chat_ui.ui.video

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.example.chat_ui.R
import com.example.chat_ui.databinding.ActivityVideoGalleryBinding
import com.example.chat_ui.ui.theme.ThemeManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Video Gallery Activity - Display generated videos
 * 
 * Features:
 * - Grid layout of generated videos (Public & Private)
 * - Filter by Public/Private visibility
 * - Play videos in VideoPlayerActivity
 * - Re-generate option for videos
 * - Share videos (Public only)
 * - Delete videos with confirmation
 * - Pull-to-refresh support
 * 
 * Integration:
 * - Accessible from Navigation Drawer
 * - Connected to Firebase Storage for Private videos
 * - Connected to YouTube API for Public videos
 * - Uses ThemeManager for consistent styling
 * - Supports RTL/LTR languages
 */
class VideoGalleryActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityVideoGalleryBinding
    private lateinit var viewModel: VideoGalleryViewModel
    private lateinit var videoAdapter: VideoGalleryAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        binding = ActivityVideoGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply insets manually (edge-to-edge)
        val fabLayoutParams = binding.generateVideoFab.layoutParams as ViewGroup.MarginLayoutParams
        val fabBaseBottomMargin = fabLayoutParams.bottomMargin
        val fabBaseEndMargin = fabLayoutParams.marginEnd

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            binding.toolbar.updatePadding(top = systemBars.top)
            binding.swipeRefresh.updatePadding(bottom = systemBars.bottom)

            (binding.generateVideoFab.layoutParams as ViewGroup.MarginLayoutParams).apply {
                bottomMargin = fabBaseBottomMargin + systemBars.bottom
                marginEnd = fabBaseEndMargin + systemBars.right
            }
            binding.generateVideoFab.requestLayout()

            insets
        }
        
        viewModel = ViewModelProvider(this)[VideoGalleryViewModel::class.java]
        
        // Apply theme colors
        applyThemeColors()
        
        setupToolbar()
        setupRecyclerView()
        setupObservers()
        setupSwipeRefresh()
        setupErrorState()
        
        // Load videos
        viewModel.loadVideos(refresh = true)
    }
    
    private fun applyThemeColors() {
        try {
            // Initialize ThemeManager if not already initialized
            ThemeManager.init(this)
            
            // Get current theme state - use AppCompatDelegate to get the actual night mode state
            val nightMode = androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode()
            val isDark = when (nightMode) {
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES -> true
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO -> false
                else -> {
                    // Fallback to system configuration
                    resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
                }
            }
            
            val themeColors = ThemeManager.getThemeColors(ThemeManager.currentPreference, isDark)
            
            // Convert Compose Color to Android Color Int using toArgb() extension
            val backgroundColor = themeColors.background.toArgb()
            val primaryColor = themeColors.primary.toArgb()
            val surfaceColor = themeColors.surface.toArgb()
            val onSurfaceColor = themeColors.textPrimary.toArgb()
            val onSurfaceVariantColor = themeColors.textSecondary.toArgb()
            
            // Apply background colors
            binding.root.setBackgroundColor(backgroundColor)
            binding.swipeRefresh.setBackgroundColor(backgroundColor)
            
            // Apply FAB color
            binding.generateVideoFab.backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)
            binding.generateVideoFab.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
            
            // Apply empty state button color
            binding.emptyStateGenerateButton.backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)
            binding.emptyStateGenerateButton.setTextColor(android.graphics.Color.WHITE)
            
            // Apply toolbar colors
            binding.toolbar.setBackgroundColor(surfaceColor)
            binding.toolbar.setTitleTextColor(onSurfaceColor)
            binding.toolbar.navigationIcon?.setTint(onSurfaceColor)
            
            // Apply empty state colors
            binding.emptyStateLayout.setBackgroundColor(backgroundColor)

            // These IDs don't exist in activity_video_gallery.xml anymore.
            // Keep styling minimal and rely on XML theme attributes.
            // (If you want, we can add ids and style them explicitly later.)
            
            // Apply error state colors
            binding.errorStateLayout.setBackgroundColor(backgroundColor)
            binding.errorStateMessage.setTextColor(onSurfaceColor)
            binding.errorStateRetryButton.backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)
            binding.errorStateRetryButton.setTextColor(android.graphics.Color.WHITE)
        } catch (e: Exception) {
            android.util.Log.e("VideoGalleryActivity", "Error applying theme colors: ${e.message}", e)
            // Fallback to default colors if theme application fails
            binding.root.setBackgroundColor(android.graphics.Color.BLACK)
            binding.swipeRefresh.setBackgroundColor(android.graphics.Color.BLACK)
        }
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = getString(R.string.video_gallery_title)
        }
    }
    
    private fun setupRecyclerView() {
        videoAdapter = VideoGalleryAdapter(
            onVideoClick = { videoResult ->
                openVideoPlayer(videoResult)
            },
            onShareClick = { videoResult ->
                shareVideo(videoResult)
            },
            onDeleteClick = { videoResult ->
                showDeleteConfirmDialog(videoResult)
            },
            onRegenerateClick = { videoResult ->
                regenerateVideo(videoResult)
            }
        )
        
        binding.videosRecyclerView.apply {
            layoutManager = GridLayoutManager(this@VideoGalleryActivity, 2)
            adapter = videoAdapter
            addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (dy <= 0) return

                    val lm = recyclerView.layoutManager as? GridLayoutManager ?: return
                    val lastVisible = lm.findLastVisibleItemPosition()
                    val total = lm.itemCount

                    // Trigger pagination when close to the end
                    if (total > 0 && lastVisible >= total - 6) {
                        viewModel.loadMoreVideos()
                    }
                }
            })
        }
    }
    
    private fun setupObservers() {
        viewModel.videos.observe(this) { videos ->
            videoAdapter.submitList(videos)
            updateContentVisibility()
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
        }

        viewModel.errorType.observe(this) {
            updateContentVisibility()
        }

        viewModel.errorMessage.observe(this) { error ->
            if (error != null) {
                // Show error snackbar (non-blocking)
                com.google.android.material.snackbar.Snackbar.make(
                    binding.root, error,
                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                ).show()

                // Also show full-screen error state
                binding.errorStateMessage.text = error
            }
        }
    }
    
    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadVideos(refresh = true)
        }

        // Generate Video FAB
        binding.generateVideoFab.setOnClickListener {
            startActivity(Intent(this, GenerateVideoActivity::class.java))
        }

        // Empty state Generate button
        binding.emptyStateGenerateButton.setOnClickListener {
            startActivity(Intent(this, GenerateVideoActivity::class.java))
        }
    }

    private fun setupErrorState() {
        binding.errorStateRetryButton.setOnClickListener {
            viewModel.clearError()
            viewModel.loadVideos(refresh = true)
        }
    }

    private fun updateContentVisibility() {
        val hasError = viewModel.errorType.value != null
        val videos = viewModel.videos.value ?: emptyList()

        binding.errorStateLayout.visibility = if (hasError) View.VISIBLE else View.GONE
        binding.videosRecyclerView.visibility = if (!hasError && videos.isNotEmpty()) View.VISIBLE else View.GONE
        binding.emptyStateLayout.visibility = if (!hasError && videos.isEmpty()) View.VISIBLE else View.GONE
    }
    
    private fun openVideoPlayer(videoResult: com.example.chat_ui.api.VeoVideoClient.VideoGenerationResult) {
        val intent = Intent(this, VideoPlayerActivity::class.java).apply {
            putExtra("video_url", videoResult.url)
            putExtra("video_title", videoResult.prompt)
            putExtra("video_duration", videoResult.duration)
            putExtra("is_public", videoResult.visibility == com.example.chat_ui.api.VeoVideoClient.VideoVisibility.PUBLIC)
        }
        startActivity(intent)
    }
    
    private fun shareVideo(videoResult: com.example.chat_ui.api.VeoVideoClient.VideoGenerationResult) {
        if (videoResult.visibility == com.example.chat_ui.api.VeoVideoClient.VideoVisibility.PUBLIC) {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, getString(R.string.share_video_text, videoResult.url))
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_video_subject))
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_video_chooser_title)))
        } else {
            com.google.android.material.snackbar.Snackbar.make(
                binding.root,
                getString(R.string.only_public_videos_can_be_shared),
                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private fun showDeleteConfirmDialog(videoResult: com.example.chat_ui.api.VeoVideoClient.VideoGenerationResult) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_video))
            .setMessage(getString(R.string.delete_video_confirm))
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                viewModel.deleteVideo(videoResult)
            }
            .show()
    }
    
    private fun regenerateVideo(videoResult: com.example.chat_ui.api.VeoVideoClient.VideoGenerationResult) {
        val intent = Intent(this, GenerateVideoActivity::class.java).apply {
            putExtra("preset_prompt", videoResult.prompt)
            putExtra("preset_visibility", videoResult.visibility.name)
        }
        startActivity(intent)
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh videos when returning to gallery
        viewModel.loadVideos(refresh = true)
    }
}
