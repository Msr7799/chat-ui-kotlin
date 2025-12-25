package com.example.chat_ui.ui.video

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.example.chat_ui.R
import com.example.chat_ui.databinding.ActivityGenerateVideoBinding

/**
 * Generate Video Activity - Standalone video generation screen
 * 
 * This Activity hosts the GenerateVideoFragment and provides:
 * - Independent video generation interface
 * - Public/Private video support (YouTube OAuth + Firebase Storage)
 * - All 3 modes: Text-to-Video, Image-to-Video, Video-to-Video
 * - Advanced settings and real-time validation
 * - Progress tracking with job polling
 * 
 * Usage:
 * - Can be launched directly from navigation
 * - Completely independent from Chat UI
 * - Follows Material Design 3 guidelines
 */
class GenerateVideoActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityGenerateVideoBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        binding = ActivityGenerateVideoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply insets manually (edge-to-edge)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            binding.toolbar.updatePadding(top = systemBars.top)
            binding.fragmentContainer.updatePadding(bottom = systemBars.bottom)

            insets
        }
        
        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = getString(R.string.generate_video_title)
        }
        
        // Load GenerateVideoFragment if not already                                                loaded
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, GenerateVideoFragment())
                .commit()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
