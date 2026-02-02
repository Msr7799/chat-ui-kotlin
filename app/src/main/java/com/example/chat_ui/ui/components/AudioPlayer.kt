package com.example.chat_ui.ui.components

import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat_ui.ui.theme.ThemeColors
import com.example.chat_ui.ui.theme.ThemeManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import java.io.FileOutputStream

private const val TAG = "AudioPlayer"

/**
 * AudioPlayer - Embedded audio player component
 * 
 * Similar to: Audio player in chat-ui's UploadedFile.svelte
 * 
 * Features:
 * - Play/Pause controls
 * - Progress slider
 * - Duration display
 * - Supports base64 and file URLs
 */

@Composable
fun AudioPlayer(
    audioSource: String, // base64 data URL or file path
    fileName: String = "Audio",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val themeColors = ThemeManager.getThemeColors(
        ThemeManager.currentPreference,
        isSystemInDarkTheme()
    )
    
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0f) }
    var duration by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    
    // Initialize media player
    DisposableEffect(audioSource) {
        try {
            val player = MediaPlayer()
            
            // Handle different audio source types
            val audioFile = when {
                audioSource.startsWith("data:audio") -> {
                    // Base64 data URL
                    val base64Data = audioSource.substringAfter(",")
                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                    val tempFile = File.createTempFile("audio_", ".mp3", context.cacheDir)
                    FileOutputStream(tempFile).use { it.write(bytes) }
                    tempFile
                }
                audioSource.startsWith("/") || audioSource.startsWith("file://") -> {
                    // Local file path
                    File(audioSource.removePrefix("file://"))
                }
                else -> {
                    // Try as base64 directly
                    try {
                        val bytes = Base64.decode(audioSource, Base64.DEFAULT)
                        val tempFile = File.createTempFile("audio_", ".mp3", context.cacheDir)
                        FileOutputStream(tempFile).use { it.write(bytes) }
                        tempFile
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            
            if (audioFile != null && audioFile.exists()) {
                player.setDataSource(audioFile.absolutePath)
                player.prepareAsync()
                player.setOnPreparedListener {
                    duration = it.duration
                    isLoading = false
                }
                player.setOnCompletionListener {
                    isPlaying = false
                    currentPosition = 0f
                }
                player.setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: $what, $extra")
                    error = "Playback error"
                    isLoading = false
                    true
                }
                mediaPlayer = player
            } else {
                error = "Audio file not found"
                isLoading = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing audio: ${e.message}", e)
            error = "Failed to load audio"
            isLoading = false
        }
        
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }
    
    // Update progress while playing
    LaunchedEffect(isPlaying) {
        while (isActive && isPlaying && mediaPlayer != null) {
            val player = mediaPlayer
            if (player != null && player.isPlaying) {
                currentPosition = player.currentPosition.toFloat() / player.duration.toFloat()
            }
            delay(100)
        }
    }
    
    // UI
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(themeColors.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Audio icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(themeColors.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = themeColors.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        
        // Player controls and info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // File name
            Text(
                text = fileName,
                color = themeColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            
            if (error != null) {
                Text(
                    text = error!!,
                    color = Color(0xFFEF4444),
                    fontSize = 11.sp
                )
            } else if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = themeColors.primary
                )
            } else {
                // Progress slider
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Current time
                    Text(
                        text = formatTime((currentPosition * duration).toInt()),
                        color = themeColors.textMuted,
                        fontSize = 10.sp
                    )
                    
                    // Slider
                    Slider(
                        value = currentPosition,
                        onValueChange = { newValue ->
                            currentPosition = newValue
                            mediaPlayer?.seekTo((newValue * duration).toInt())
                        },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = themeColors.primary,
                            activeTrackColor = themeColors.primary,
                            inactiveTrackColor = themeColors.border
                        )
                    )
                    
                    // Total time
                    Text(
                        text = formatTime(duration),
                        color = themeColors.textMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }
        
        // Play/Pause button
        IconButton(
            onClick = {
                mediaPlayer?.let { player ->
                    if (isPlaying) {
                        player.pause()
                    } else {
                        player.start()
                    }
                    isPlaying = !isPlaying
                }
            },
            enabled = !isLoading && error == null,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (!isLoading && error == null) themeColors.primary 
                    else themeColors.border
                )
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Compact audio player for inline display
 */
@Composable
fun AudioPlayerCompact(
    audioSource: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val themeColors = ThemeManager.getThemeColors(
        ThemeManager.currentPreference,
        isSystemInDarkTheme()
    )
    
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isReady by remember { mutableStateOf(false) }
    
    DisposableEffect(audioSource) {
        try {
            val player = MediaPlayer()
            
            // Handle base64 audio
            if (audioSource.startsWith("data:audio") || !audioSource.contains("/")) {
                val base64Data = if (audioSource.contains(",")) {
                    audioSource.substringAfter(",")
                } else {
                    audioSource
                }
                val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                val tempFile = File.createTempFile("audio_", ".mp3", context.cacheDir)
                FileOutputStream(tempFile).use { it.write(bytes) }
                player.setDataSource(tempFile.absolutePath)
            } else {
                player.setDataSource(audioSource)
            }
            
            player.prepareAsync()
            player.setOnPreparedListener { isReady = true }
            player.setOnCompletionListener { isPlaying = false }
            mediaPlayer = player
        } catch (e: Exception) {
            Log.e(TAG, "Compact player error: ${e.message}")
        }
        
        onDispose {
            mediaPlayer?.release()
        }
    }
    
    IconButton(
        onClick = {
            mediaPlayer?.let {
                if (isPlaying) it.pause() else it.start()
                isPlaying = !isPlaying
            }
        },
        enabled = isReady,
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(themeColors.primary.copy(alpha = if (isReady) 1f else 0.5f))
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun formatTime(millis: Int): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / 1000) / 60
    return "%d:%02d".format(minutes, seconds)
}

/**
 * Check if a MIME type is audio
 */
fun isAudioMime(mime: String): Boolean {
    return mime.startsWith("audio/")
}

/**
 * Extract audio files from message content
 */
fun extractAudioFromDataUrl(dataUrl: String): String? {
    if (dataUrl.startsWith("data:audio")) {
        return dataUrl
    }
    return null
}
