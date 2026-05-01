package com.example.chat_ui.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.example.chat_ui.utils.CloudinaryUrlUtils
import com.example.chat_ui.data.Message
import com.example.chat_ui.ui.theme.ThemeColors
import com.example.chat_ui.ui.theme.ThemeManager
import java.text.SimpleDateFormat
import java.util.*

/**
 * Chat message bubble component
 * Similar to ChatMessage.svelte in the Svelte app
 */
@Composable
fun ChatMessageBubble(
    message: Message,
    onRetry: () -> Unit = {},
    onAlternativeChange: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showActions by remember { mutableStateOf(false) }
    val themeColors: ThemeColors =
        ThemeManager.getThemeColors(ThemeManager.currentPreference, isSystemInDarkTheme())
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!message.isUser) {
            // AI Avatar
            MessageAvatar(
                isUser = false,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        
        Column(
            horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            // Display attached files (images, documents) if any
            if (message.files.isNotEmpty()) {
                MessageFilesDisplay(
                    files = message.files,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            // Display generated images if any
            if (message.generatedImages.isNotEmpty()) {
                GeneratedImagesDisplay(
                    imageUrls = message.generatedImages,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            // Message Bubble
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (message.isUser) 16.dp else 4.dp,
                            bottomEnd = if (message.isUser) 4.dp else 16.dp
                        )
                    )
                    .background(
                        if (message.isUser) themeColors.userBubble
                        else themeColors.assistantBubble
                    )
                    .clickable { showActions = !showActions }
                    .padding(12.dp)
                    .widthIn(max = 320.dp)
            ) {
                // Use the new MarkdownRenderer with Think blocks support
                MarkdownRenderer(
                    content = message.getDisplayContent(),
                    isUser = message.isUser,
                    isLoading = false
                )
            }
            
            // Alternatives navigation (for AI messages with alternatives)
            if (!message.isUser && message.hasAlternatives()) {
                MessageAlternatives(
                    currentIndex = message.currentAlternativeIndex,
                    totalCount = message.getAlternativesCount(),
                    onPrevious = { onAlternativeChange?.invoke(message.currentAlternativeIndex - 1) },
                    onNext = { onAlternativeChange?.invoke(message.currentAlternativeIndex + 1) },
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            // Quick action buttons row (always visible at bottom)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Timestamp and Model info
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(
                        text = formatMessageTime(message.timestamp),
                        color = themeColors.textMuted,
                        fontSize = 11.sp
                    )
                    
                    // Show model name for AI messages
                    if (!message.isUser && message.model.isNotBlank()) {
                        Text(
                            text = " • ",
                            color = themeColors.textMuted,
                            fontSize = 11.sp
                        )
                        Text(
                            text = message.model.substringAfter("/"), // Remove "google/" prefix
                            color = themeColors.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Action buttons
                if (message.isUser) {
                    // User message: Resend button
                    IconButton(
                        onClick = onRetry,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "إعادة إرسال",
                            tint = themeColors.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    // AI message: Copy and Refresh buttons
                    IconButton(
                        onClick = {
                            copyToClipboard(context, message.getDisplayContent())
                            Toast.makeText(context, "تم النسخ", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "نسخ",
                            tint = themeColors.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    
                    IconButton(
                        onClick = onRetry,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "إعادة التوليد",
                            tint = themeColors.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        
        if (message.isUser) {
            // User Avatar
            MessageAvatar(
                isUser = true,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun MessageAvatar(
    isUser: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                if (isUser)
                    ThemeManager.getThemeColors(ThemeManager.currentPreference, isSystemInDarkTheme()).primary
                else
                    ThemeManager.getThemeColors(ThemeManager.currentPreference, isSystemInDarkTheme()).primary
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isUser) {
            Text(
                text = "U",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        } else {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun MessageContent(
    content: String,
    isUser: Boolean
) {
    // Simple markdown-like rendering
    val themeColors: ThemeColors =
        ThemeManager.getThemeColors(ThemeManager.currentPreference, isSystemInDarkTheme())
    val lines = content.split("\n")
    var inCodeBlock = false
    var codeLanguage = ""
    val codeContent = StringBuilder()
    
    Column {
        for (line in lines) {
            when {
                line.startsWith("```") -> {
                    if (!inCodeBlock) {
                        inCodeBlock = true
                        codeLanguage = line.removePrefix("```").trim()
                    } else {
                        // End of code block - render it
                        CodeBlock(
                            code = codeContent.toString().trimEnd(),
                            language = codeLanguage
                        )
                        codeContent.clear()
                        inCodeBlock = false
                    }
                }
                inCodeBlock -> {
                    codeContent.appendLine(line)
                }
                line.startsWith("# ") -> {
                    Text(
                        text = line.removePrefix("# "),
                        color = if (isUser) Color.White else themeColors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                line.startsWith("## ") -> {
                    Text(
                        text = line.removePrefix("## "),
                        color = if (isUser) Color.White else themeColors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    Row {
                        Text(
                            text = "•  ",
                            color = if (isUser) Color.White else themeColors.textPrimary
                        )
                        Text(
                            text = line.removePrefix("- ").removePrefix("* "),
                            color = if (isUser) Color.White else themeColors.textPrimary,
                            fontSize = 14.sp
                        )
                    }
                }
                line.contains("`") && !line.startsWith("```") -> {
                    // Inline code
                    InlineCodeText(
                        text = line,
                        isUser = isUser
                    )
                }
                else -> {
                    Text(
                        text = line,
                        color = if (isUser) Color.White else themeColors.textPrimary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineCodeText(text: String, isUser: Boolean) {
    val themeColors: ThemeColors =
        ThemeManager.getThemeColors(ThemeManager.currentPreference, isSystemInDarkTheme())
    val parts = text.split("`")
    Row(modifier = Modifier.fillMaxWidth()) {
        parts.forEachIndexed { index, part ->
            if (index % 2 == 1) {
                // Code part
                Text(
                    text = part,
                    color = if (isUser) Color.White else themeColors.primary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .background(
                            if (isUser) Color.White.copy(alpha = 0.2f)
                            else themeColors.primary.copy(alpha = 0.1f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            } else {
                Text(
                    text = part,
                    color = if (isUser) Color.White else themeColors.textPrimary,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun CodeBlock(
    code: String,
    language: String
) {
    val context = LocalContext.current
    val themeColors: ThemeColors =
        ThemeManager.getThemeColors(ThemeManager.currentPreference, isSystemInDarkTheme())
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(themeColors.surface)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(themeColors.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.ifEmpty { "code" },
                color = themeColors.textMuted,
                fontSize = 12.sp
            )
            IconButton(
                onClick = { copyToClipboard(context, code) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy",
                    tint = themeColors.textMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        
        // Code content
        Text(
            text = code,
            color = themeColors.textPrimary,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        )
    }
}

@Composable
private fun MessageActions(
    onCopy: () -> Unit,
    onRetry: () -> Unit,
    onThumbUp: () -> Unit,
    onThumbDown: () -> Unit
) {
    Row(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ActionButton(
            icon = Icons.Outlined.ContentCopy,
            onClick = onCopy,
            contentDescription = "Copy"
        )
        ActionButton(
            icon = Icons.Outlined.Refresh,
            onClick = onRetry,
            contentDescription = "Retry"
        )
        ActionButton(
            icon = Icons.Outlined.ThumbUp,
            onClick = onThumbUp,
            contentDescription = "Good response"
        )
        ActionButton(
            icon = Icons.Outlined.ThumbDown,
            onClick = onThumbDown,
            contentDescription = "Bad response"
        )
    }
}

/**
 * Display generated images in a grid with fullscreen capability
 */
@Composable
private fun GeneratedImagesDisplay(
    imageUrls: List<String>,
    modifier: Modifier = Modifier
) {
    var selectedImageUrl by remember { mutableStateOf<String?>(null) }
    
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.heightIn(max = 400.dp),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(imageUrls.size) { index ->
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { selectedImageUrl = imageUrls[index] }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                            .data(CloudinaryUrlUtils.galleryThumbnailUrl(imageUrls[index]))
                            .size(360, 360)
                            .precision(Precision.INEXACT)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .allowHardware(true)
                            .allowRgb565(true)
                            .crossfade(false)
                            .build(),
                    contentDescription = "Generated image ${index + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
    
    // Fullscreen viewer
    selectedImageUrl?.let { url ->
        FullscreenImageViewer(
            imageUrl = url,
            onDismiss = { selectedImageUrl = null }
        )
    }
}

/**
 * Fullscreen image viewer dialog
 */
@Composable
private fun FullscreenImageViewer(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { onDismiss() }
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(CloudinaryUrlUtils.previewUrl(imageUrl))
                    .size(1200, 1200)
                    .precision(Precision.INEXACT)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .allowHardware(true)
                    .allowRgb565(true)
                    .crossfade(false)
                    .build(),
                contentDescription = "Fullscreen image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            
            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
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

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    contentDescription: String
) {
    val themeColors: ThemeColors =
        ThemeManager.getThemeColors(ThemeManager.currentPreference, isSystemInDarkTheme())
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(themeColors.surfaceVariant)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = themeColors.textMuted,
            modifier = Modifier.size(16.dp)
        )
    }
}

private fun formatMessageTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("message", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}
