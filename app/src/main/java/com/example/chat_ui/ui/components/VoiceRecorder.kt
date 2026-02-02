package com.example.chat_ui.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.chat_ui.ui.theme.ThemeColors
import com.example.chat_ui.ui.theme.ThemeManager
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max

/**
 * VoiceRecorder - Advanced voice recording with waveform visualization
 * 
 * Similar to: VoiceRecorder.svelte + AudioWaveform.svelte in chat-ui
 * 
 * Features:
 * - Real-time waveform visualization
 * - Recording state management
 * - Audio capture to file
 * - Permission handling
 */

private const val TAG = "VoiceRecorder"
private const val SAMPLE_RATE = 16000 // Whisper prefers 16kHz
private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

@Composable
fun VoiceRecorderOverlay(
    isVisible: Boolean,
    isTranscribing: Boolean = false,
    onCancel: () -> Unit,
    onSend: (File) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val themeColors = ThemeManager.getThemeColors(
        ThemeManager.currentPreference,
        isSystemInDarkTheme()
    )
    
    var hasPermission by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var audioFile by remember { mutableStateOf<File?>(null) }
    var frequencyData by remember { mutableStateOf(FloatArray(32) { 0f }) }
    
    val scope = rememberCoroutineScope()
    var recordingJob by remember { mutableStateOf<Job?>(null) }
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) {
            onError("Microphone permission denied")
        }
    }
    
    // Check permission on mount
    LaunchedEffect(isVisible) {
        if (isVisible) {
            hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            
            if (!hasPermission) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
    
    // Start recording when visible and has permission
    LaunchedEffect(isVisible, hasPermission) {
        if (isVisible && hasPermission && !isRecording) {
            isRecording = true
            recordingJob = scope.launch(Dispatchers.IO) {
                try {
                    val result = startRecording(context) { data ->
                        frequencyData = data
                    }
                    audioFile = result
                } catch (e: Exception) {
                    Log.e(TAG, "Recording error: ${e.message}", e)
                    // IMPORTANT: onError updates UI state - must be called from Main thread
                    withContext(Dispatchers.Main) {
                        onError("Recording failed: ${e.message}")
                    }
                }
            }
        }
    }
    
    // Cleanup when hidden
    DisposableEffect(isVisible) {
        onDispose {
            if (!isVisible) {
                recordingJob?.cancel()
                isRecording = false
            }
        }
    }
    
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(themeColors.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cancel button
                IconButton(
                    onClick = {
                        recordingJob?.cancel()
                        isRecording = false
                        onCancel()
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(themeColors.surface)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = themeColors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                // Waveform / Loading
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isTranscribing) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = themeColors.primary
                            )
                            Text(
                                text = "Transcribing...",
                                color = themeColors.textMuted,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        AudioWaveform(
                            frequencyData = frequencyData,
                            color = themeColors.primary,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                
                // Confirm/Send button
                IconButton(
                    onClick = {
                        recordingJob?.cancel()
                        isRecording = false
                        audioFile?.let { file ->
                            onSend(file)
                        } ?: onError("No audio recorded")
                    },
                    enabled = !isTranscribing,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(themeColors.primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Audio Waveform visualization component
 * Similar to: AudioWaveform.svelte in chat-ui
 */
@Composable
fun AudioWaveform(
    frequencyData: FloatArray,
    color: Color,
    modifier: Modifier = Modifier,
    barCount: Int = 32,
    minHeight: Float = 4f,
    maxHeight: Float = 40f
) {
    Canvas(modifier = modifier) {
        val barWidth = size.width / (barCount * 2f)
        val spacing = barWidth
        
        for (i in 0 until barCount) {
            val dataIndex = (i * frequencyData.size / barCount).coerceIn(0, frequencyData.lastIndex)
            val amplitude = frequencyData.getOrElse(dataIndex) { 0f }
            
            // Normalize amplitude to height range
            val normalizedHeight = minHeight + (amplitude * (maxHeight - minHeight))
            val barHeight = normalizedHeight.coerceIn(minHeight, maxHeight)
            
            val x = i * (barWidth + spacing)
            val y = (size.height - barHeight) / 2
            
            drawRoundRect(
                color = color.copy(alpha = 0.6f + (amplitude * 0.4f)),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}

/**
 * Start audio recording and return frequency data updates
 */
private suspend fun startRecording(
    context: Context,
    onFrequencyUpdate: (FloatArray) -> Unit
): File = withContext(Dispatchers.IO) {
    val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
    
    val audioRecord = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AUDIO_FORMAT,
        bufferSize
    )
    
    val outputFile = File(context.cacheDir, "voice_recording_${System.currentTimeMillis()}.pcm")
    val outputStream = FileOutputStream(outputFile)
    val buffer = ShortArray(bufferSize)
    
    try {
        audioRecord.startRecording()
        Log.d(TAG, "Recording started")
        
        while (currentCoroutineContext().isActive) {
            val readCount = audioRecord.read(buffer, 0, buffer.size)
            if (readCount > 0) {
                // Write to file
                val byteBuffer = ByteArray(readCount * 2)
                for (i in 0 until readCount) {
                    byteBuffer[i * 2] = (buffer[i].toInt() and 0xFF).toByte()
                    byteBuffer[i * 2 + 1] = (buffer[i].toInt() shr 8 and 0xFF).toByte()
                }
                outputStream.write(byteBuffer)
                
                // Calculate frequency data for visualization
                val frequencyData = calculateFrequencyData(buffer, readCount)
                // CRITICAL: onFrequencyUpdate updates UI state - MUST be on Main thread
                withContext(Dispatchers.Main) {
                    onFrequencyUpdate(frequencyData)
                }
            }
            
            delay(50) // Update ~20 times per second
        }
    } finally {
        audioRecord.stop()
        audioRecord.release()
        outputStream.close()
        Log.d(TAG, "Recording stopped, file: ${outputFile.absolutePath}")
    }
    
    outputFile
}

/**
 * Calculate frequency data from audio samples for waveform visualization
 */
private fun calculateFrequencyData(samples: ShortArray, count: Int): FloatArray {
    val bins = 32
    val binSize = count / bins
    val result = FloatArray(bins)
    
    for (i in 0 until bins) {
        val start = i * binSize
        val end = minOf(start + binSize, count)
        
        var sum = 0f
        for (j in start until end) {
            sum += abs(samples[j].toFloat())
        }
        
        // Normalize to 0-1 range
        val avg = sum / (end - start)
        result[i] = (avg / Short.MAX_VALUE).coerceIn(0f, 1f)
    }
    
    return result
}

/**
 * Simple voice input button that triggers the recorder overlay
 */
@Composable
fun VoiceInputButton(
    onClick: () -> Unit,
    isRecording: Boolean = false,
    modifier: Modifier = Modifier
) {
    val themeColors = ThemeManager.getThemeColors(
        ThemeManager.currentPreference,
        isSystemInDarkTheme()
    )
    
    IconButton(
        onClick = onClick,
        modifier = modifier.size(36.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = if (isRecording) "Recording..." else "Voice input",
            tint = if (isRecording) Color.Red else themeColors.textSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}
