package com.example.chat_ui.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat_ui.R
import com.example.chat_ui.ui.video.GenerateVideoActivity
import com.example.chat_ui.ui.video.VideoGalleryActivity
import com.example.chat_ui.config.ConfigManager

/**
 * Advanced Video Generation Screen - Full-featured Compose UI
 * Supports tabs, modes, feature flags, and advanced settings
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoGenerationScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    
    // State management
    var selectedTabIndex by remember { mutableStateOf(0) }
    var prompt by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf("TEXT_TO_VIDEO") }
    var isPublic by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf(6) }
    var quality by remember { mutableStateOf("STANDARD") }
    var aspectRatio by remember { mutableStateOf("16:9") }
    
    // Feature flags
    val isImageToVideoEnabled = ConfigManager.isImageToVideoEnabled
    val isVideoToVideoEnabled = ConfigManager.isVideoToVideoEnabled
    val isPublicUploadEnabled = ConfigManager.isVideoPublicUploadEnabled
    val isPrivateUploadEnabled = ConfigManager.isVideoPrivateUploadEnabled
    
    val tabs = listOf("Basic", "Advanced")
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .systemBarsPadding()
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Text(
                    text = "AI Video Generation",
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colorScheme.onSurface
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = colorScheme.surface
            )
        )
        
        // Tab Layout
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = colorScheme.surface,
            contentColor = colorScheme.primary
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            color = if (selectedTabIndex == index) colorScheme.primary else colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }
        
        // Tab Content
        when (selectedTabIndex) {
            0 -> BasicVideoGenerationTab(
                prompt = prompt,
                onPromptChange = { prompt = it },
                selectedMode = selectedMode,
                onModeChange = { selectedMode = it },
                isPublic = isPublic,
                onPublicChange = { isPublic = it },
                duration = duration,
                onDurationChange = { duration = it },
                colorScheme = colorScheme,
                isImageToVideoEnabled = isImageToVideoEnabled,
                isVideoToVideoEnabled = isVideoToVideoEnabled,
                isPublicUploadEnabled = isPublicUploadEnabled,
                isPrivateUploadEnabled = isPrivateUploadEnabled
            )
            1 -> AdvancedVideoGenerationTab(
                quality = quality,
                onQualityChange = { quality = it },
                aspectRatio = aspectRatio,
                onAspectRatioChange = { aspectRatio = it },
                colorScheme = colorScheme
            )
        }
        
        // Generate Button
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Button(
                    onClick = {
                        // Launch GenerateVideoActivity with parameters
                        context.startActivity(Intent(context, GenerateVideoActivity::class.java))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary
                    ),
                    enabled = prompt.isNotBlank() && prompt.length >= 10
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoCall,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Generate Video",
                        color = colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                if (prompt.isNotBlank() && prompt.length < 10) {
                    Text(
                        text = "Prompt must be at least 10 characters",
                        color = colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BasicVideoGenerationTab(
    prompt: String,
    onPromptChange: (String) -> Unit,
    selectedMode: String,
    onModeChange: (String) -> Unit,
    isPublic: Boolean,
    onPublicChange: (Boolean) -> Unit,
    duration: Int,
    onDurationChange: (Int) -> Unit,
    colorScheme: ColorScheme,
    isImageToVideoEnabled: Boolean,
    isVideoToVideoEnabled: Boolean,
    isPublicUploadEnabled: Boolean,
    isPrivateUploadEnabled: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Mode Selection
        Text(
            text = "Generation Mode",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Text to Video (always enabled)
            FilterChip(
                onClick = { onModeChange("TEXT_TO_VIDEO") },
                label = { Text("Text → Video") },
                selected = selectedMode == "TEXT_TO_VIDEO",
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colorScheme.primary,
                    selectedLabelColor = colorScheme.onPrimary
                )
            )
            
            // Image to Video (feature flag)
            if (isImageToVideoEnabled) {
                FilterChip(
                    onClick = { onModeChange("IMAGE_TO_VIDEO") },
                    label = { Text("Image → Video") },
                    selected = selectedMode == "IMAGE_TO_VIDEO",
                    colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colorScheme.primary,
                    selectedLabelColor = colorScheme.onPrimary
                    )
                )
            }
            
            // Video to Video (feature flag)
            if (isVideoToVideoEnabled) {
                FilterChip(
                    onClick = { onModeChange("VIDEO_TO_VIDEO") },
                    label = { Text("Video → Video") },
                    selected = selectedMode == "VIDEO_TO_VIDEO",
                    colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colorScheme.primary,
                    selectedLabelColor = colorScheme.onPrimary
                    )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Prompt Input
        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChange,
            label = { Text("Video Description") },
            placeholder = { Text("Describe the video you want to generate...") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colorScheme.primary,
                focusedLabelColor = colorScheme.primary
            )
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Duration Slider
        Text(
            text = "Duration: ${duration}s",
            fontSize = 14.sp,
            color = colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Slider(
            value = duration.toFloat(),
            onValueChange = { onDurationChange(it.toInt()) },
            valueRange = 3f..10f,
            steps = 6,
            colors = SliderDefaults.colors(
                thumbColor = colorScheme.primary,
                activeTrackColor = colorScheme.primary
            )
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Privacy Toggle
        if (isPublicUploadEnabled && isPrivateUploadEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isPublic) "Public (YouTube)" else "Private (Firebase)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = if (isPublic) "Video will be uploaded to YouTube" else "Video will be stored privately",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                
                Switch(
                    checked = isPublic,
                    onCheckedChange = onPublicChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colorScheme.primary
                    )
                )
            }
        }
    }
}

@Composable
fun AdvancedVideoGenerationTab(
    quality: String,
    onQualityChange: (String) -> Unit,
    aspectRatio: String,
    onAspectRatioChange: (String) -> Unit,
    colorScheme: ColorScheme
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Quality Selection
        Text(
            text = "Video Quality",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("STANDARD", "HIGH", "ULTRA").forEach { qualityOption ->
                FilterChip(
                    onClick = { onQualityChange(qualityOption) },
                    label = { 
                        Text(
                            when (qualityOption) {
                                "STANDARD" -> "Standard (720p)"
                                "HIGH" -> "High (1080p)"
                                "ULTRA" -> "Ultra (1080p 60fps)"
                                else -> qualityOption
                            }
                        ) 
                    },
                    selected = quality == qualityOption,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colorScheme.primary,
                        selectedLabelColor = colorScheme.onPrimary
                    )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Aspect Ratio Selection
        Text(
            text = "Aspect Ratio",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("16:9", "9:16", "1:1").forEach { ratio ->
                FilterChip(
                    onClick = { onAspectRatioChange(ratio) },
                    label = { Text(ratio) },
                    selected = aspectRatio == ratio,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colorScheme.primary,
                        selectedLabelColor = colorScheme.onPrimary
                    )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Advanced Settings Info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Advanced Features",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
                Text(
                    text = "• Cinematic styles and lighting\n• Motion level control\n• Custom FPS settings\n• Negative prompts\n• Seed control",
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                
                Button(
                    onClick = {
                        // Launch full GenerateVideoActivity for advanced features
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary.copy(alpha = 0.8f)
                    )
                ) {
                    Text(
                        text = "Open Advanced Editor",
                        color = colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

/**
 * Video Gallery Screen - Simple Compose UI  
 * Redirects to full VideoGalleryActivity for advanced features
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoGalleryScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .systemBarsPadding()
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Text(
                    text = "Video Gallery",
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colorScheme.onSurface
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        val intent = Intent(context, VideoGalleryActivity::class.java)
                        context.startActivity(intent)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = "Full Gallery",
                        tint = colorScheme.onSurface
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = colorScheme.surface
            )
        )
        
        // Main Content - Empty State for now
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.VideoLibrary,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Video Gallery",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "View and manage your generated videos",
                fontSize = 14.sp,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )
            
            Button(
                onClick = {
                    val intent = Intent(context, VideoGalleryActivity::class.java)
                    context.startActivity(intent)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VideoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Full Gallery")
            }
        }
        
        // Floating Action Button
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomEnd
        ) {
            FloatingActionButton(
                onClick = {
                    val intent = android.content.Intent(context, com.example.chat_ui.ui.video.GenerateVideoActivity::class.java)
                    context.startActivity(intent)
                },
                modifier = Modifier.padding(16.dp),
                containerColor = colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Generate Video"
                )
            }
        }
    }
}
