package com.example.chat_ui.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.chat_ui.data.MessageFile
import com.example.chat_ui.ui.theme.ThemeColors
import com.example.chat_ui.ui.theme.ThemeManager

/**
 * MessageFilePreview - Display pending files before sending
 * 
 * Similar to: UploadedFile.svelte in chat-ui
 */
@Composable
fun MessageFilePreviewRow(
    files: List<MessageFile>,
    onRemove: (MessageFile) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = files.isNotEmpty(),
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            files.forEach { file ->
                MessageFilePreviewItem(
                    file = file,
                    canRemove = true,
                    onRemove = { onRemove(file) }
                )
            }
        }
    }
}

/**
 * Single file preview item
 */
@Composable
fun MessageFilePreviewItem(
    file: MessageFile,
    canRemove: Boolean = true,
    onRemove: () -> Unit = {},
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val themeColors = ThemeManager.getThemeColors(
        ThemeManager.currentPreference,
        isSystemInDarkTheme()
    )
    
    var showFullPreview by remember { mutableStateOf(false) }
    
    // Full screen preview dialog
    if (showFullPreview && file.isImage()) {
        ImagePreviewDialog(
            file = file,
            onDismiss = { showFullPreview = false }
        )
    }
    
    Box(
        modifier = modifier
            .size(if (file.isImage()) 80.dp else 140.dp, 80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(themeColors.surfaceVariant)
            .clickable {
                if (onClick != null) {
                    onClick()
                } else if (file.isImage()) {
                    showFullPreview = true
                }
            }
    ) {
        if (file.isImage()) {
            // Image preview
            ImagePreview(file = file, modifier = Modifier.fillMaxSize())
        } else {
            // Document preview
            DocumentPreview(file = file, themeColors = themeColors)
        }
        
        // Remove button
        if (canRemove) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable { onRemove() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

/**
 * Image preview from base64
 */
@Composable
private fun ImagePreview(
    file: MessageFile,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(file.value) {
        try {
            val bytes = Base64.decode(file.value, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
    
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = file.name,
            modifier = modifier.clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        // Fallback for failed decode
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

/**
 * Document preview
 */
@Composable
private fun DocumentPreview(
    file: MessageFile,
    themeColors: ThemeColors
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon based on mime type
        val icon = when {
            file.mime.contains("pdf") -> Icons.Default.PictureAsPdf
            else -> Icons.Default.Description
        }
        
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = themeColors.textSecondary,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = truncateMiddle(file.name, 20),
                color = themeColors.textPrimary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = file.mime.substringAfter("/"),
                color = themeColors.textMuted,
                fontSize = 10.sp
            )
        }
    }
}

/**
 * Full screen image preview dialog
 */
@Composable
private fun ImagePreviewDialog(
    file: MessageFile,
    onDismiss: () -> Unit
) {
    val bitmap = remember(file.value) {
        try {
            val bytes = Base64.decode(file.value, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = Color.Black.copy(alpha = 0.9f)
        ) {
            Box {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = file.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat()),
                        contentScale = ContentScale.Fit
                    )
                }
                
                // Close button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Display files attached to a message (in chat history)
 */
@Composable
fun MessageFilesDisplay(
    files: List<MessageFile>,
    modifier: Modifier = Modifier
) {
    if (files.isEmpty()) return
    
    // Separate audio files from other files
    val audioFiles = files.filter { it.mime.startsWith("audio/") }
    val otherFiles = files.filter { !it.mime.startsWith("audio/") }
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Display audio files with AudioPlayer
        audioFiles.forEach { file ->
            AudioPlayer(
                audioSource = file.getDataUrl(),
                fileName = file.name,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // Display other files in horizontal scroll
        if (otherFiles.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                otherFiles.forEach { file ->
                    MessageFilePreviewItem(
                        file = file,
                        canRemove = false,
                        modifier = Modifier.height(72.dp)
                    )
                }
            }
        }
    }
}

/**
 * Truncate text in the middle (like chat-ui)
 */
private fun truncateMiddle(text: String, maxLength: Int): String {
    if (text.length <= maxLength) return text
    
    val halfLength = (maxLength - 1) / 2
    val start = text.substring(0, halfLength)
    val end = text.substring(text.length - halfLength)
    
    return "$start…$end"
}
