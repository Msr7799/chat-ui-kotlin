package com.example.chat_ui.ui.video

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.chat_ui.R
import com.example.chat_ui.api.VeoVideoClient

class VideoPlayerActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)
        
        // Get video result from intent
        val videoResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(
                "video_result",
                VeoVideoClient.VideoGenerationResult::class.java
            )
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("video_result") as? VeoVideoClient.VideoGenerationResult
        }
        
        // TODO: Implement video player UI
        // For now, just show a placeholder
    }
}
