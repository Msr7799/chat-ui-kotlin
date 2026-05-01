package com.example.chat_ui.ui.video

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.example.chat_ui.R
import com.example.chat_ui.data.firebase.FirebaseManager
import com.example.chat_ui.databinding.ActivityVideoPlayerBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoPlayerBinding
    private var player: ExoPlayer? = null
    private var videoUrl: String? = null
    private var videoTitle: String? = null
    private var videoId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get extras
        videoUrl = intent.getStringExtra("video_url")
        videoTitle = intent.getStringExtra("video_title")
        videoId = intent.getStringExtra("video_id")

        if (videoUrl.isNullOrBlank()) {
            Toast.makeText(this, "Error: Invalid video URL", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupToolbar()
        initializePlayer()
        setupButtons()
    }

    private fun setupToolbar() {
        binding.toolbar.title = videoTitle ?: "Video Player"
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build()
        binding.playerView.player = player

        val mediaItem = MediaItem.fromUri(videoUrl!!)
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.play()
    }

    private fun setupButtons() {
        binding.btnSave.setOnClickListener {
            downloadVideo()
        }

        binding.btnDelete.setOnClickListener {
            showDeleteConfirmation()
        }
    }

    private fun downloadVideo() {
        try {
            val request = DownloadManager.Request(Uri.parse(videoUrl))
            val filename = "VeoVideo_${System.currentTimeMillis()}.mp4"
            
            request.setTitle(videoTitle ?: "Downloaded Video")
            request.setDescription("Downloading generated video...")
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, filename)
            
            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
            
            Toast.makeText(this, "Downloading to Movies...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun showDeleteConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Video?")
            .setMessage("This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteVideo()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteVideo() {
        if (videoId == null) {
            Toast.makeText(this, "Error: Cannot delete (missing ID)", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.controlsContainer.visibility = View.GONE
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Delete from Firestore
                FirebaseManager.firestore
                    .collection("generated_videos")
                    .document(videoId!!)
                    .delete()
                    .await()

                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Video deleted", Toast.LENGTH_SHORT).show()
                    finish() // Close activity to return to gallery
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.controlsContainer.visibility = View.VISIBLE
                    Toast.makeText(applicationContext, "Delete failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }
}
