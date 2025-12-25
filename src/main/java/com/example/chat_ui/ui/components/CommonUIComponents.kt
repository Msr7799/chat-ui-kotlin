package com.example.chat_ui.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat_ui.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Copy to Clipboard Button with tooltip feedback
 * Matches CopyToClipBoardBtn.svelte
 */
@Composable
fun CopyToClipboardButton(
    value: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 16.dp,
    showTooltip: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var isSuccess by remember { mutableStateOf(false) }
    
    LaunchedEffect(isSuccess) {
        if (isSuccess) {
            delay(1500)
            isSuccess = false
        }
    }
    
    Box(modifier = modifier) {
        IconButton(
            onClick = {
                copyToClipboard(context, value)
                isSuccess = true
                onClick?.invoke()
            },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = if (isSuccess) Icons.Default.Check else Icons.Outlined.ContentCopy,
                contentDescription = if (isSuccess) "Copied!" else "Copy to clipboard",
                tint = if (isSuccess) Color(0xFF4ADE80) else Color(0xFF9CA3AF),
                modifier = Modifier.size(iconSize)
            )
        }
        
        // Tooltip
        if (showTooltip) {
            androidx.compose.animation.AnimatedVisibility(
                visible = isSuccess,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = (-28).dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF1F2937))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Copied!",
                        color = Color.White,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

/**
 * Retry Button with rotate icon
 * Matches RetryBtn.svelte
 */
@Composable
fun RetryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Retry",
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(32.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF374151),
            contentColor = Color(0xFFD1D5DB)
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Refresh,
            contentDescription = null,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Stop Generating Button with spinning border animation
 * Matches StopGeneratingBtn.svelte
 */
@Composable
fun StopGeneratingButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showSpinningBorder: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "stop_border")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "stop_rotation"
    )
    
    Box(
        modifier = modifier.size(36.dp),
        contentAlignment = Alignment.Center
    ) {
        // Spinning border (conic gradient simulation)
        if (showSpinningBorder) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(rotation)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Transparent,
                                Color(0xFF6B7280).copy(alpha = 0.5f),
                                Color(0xFF6B7280)
                            )
                        )
                    )
            )
        }
        
        // Inner button
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(0xFF1F2937))
        ) {
            Icon(
                imageVector = Icons.Outlined.Stop,
                contentDescription = "Stop generating",
                tint = Color(0xFF9CA3AF),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Toggle Switch component
 * Matches Switch.svelte
 */
@Composable
fun ToggleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 14.dp else 0.dp,
        animationSpec = tween(durationMillis = 200),
        label = "switch_thumb"
    )
    
    val trackColor by animateColorAsState(
        targetValue = if (checked) PrimaryBlue else Color(0xFF4B5563),
        animationSpec = tween(durationMillis = 200),
        label = "switch_track"
    )
    
    Box(
        modifier = modifier
            .width(36.dp)
            .height(20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(trackColor)
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                role = Role.Switch
            ) { onCheckedChange(!checked) }
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(14.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/**
 * Hover Tooltip component
 * Matches HoverTooltip.svelte
 */
@Composable
fun TooltipWrapper(
    tooltip: String,
    position: TooltipPosition = TooltipPosition.Bottom,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var showTooltip by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { showTooltip = !showTooltip }
    ) {
        content()
        
        androidx.compose.animation.AnimatedVisibility(
            visible = showTooltip,
            modifier = Modifier.align(
                when (position) {
                    TooltipPosition.Top -> Alignment.TopCenter
                    TooltipPosition.Bottom -> Alignment.BottomCenter
                    TooltipPosition.Left -> Alignment.CenterStart
                    TooltipPosition.Right -> Alignment.CenterEnd
                }
            ).offset(
                y = when (position) {
                    TooltipPosition.Top -> (-8).dp
                    TooltipPosition.Bottom -> 8.dp
                    else -> 0.dp
                },
                x = when (position) {
                    TooltipPosition.Left -> (-8).dp
                    TooltipPosition.Right -> 8.dp
                    else -> 0.dp
                }
            )
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1F2937))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .widthIn(max = 200.dp)
            ) {
                Text(
                    text = tooltip,
                    color = Color.White,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
    
    // Auto-hide tooltip
    LaunchedEffect(showTooltip) {
        if (showTooltip) {
            delay(3000)
            showTooltip = false
        }
    }
}

enum class TooltipPosition {
    Top, Bottom, Left, Right
}

/**
 * Audio Waveform Visualization
 * Matches AudioWaveform.svelte
 */
@Composable
fun AudioWaveform(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier,
    barColor: Color = Color(0xFF9CA3AF),
    minHeight: Dp = 4.dp,
    maxHeight: Dp = 40.dp,
    barWidth: Dp = 2.dp,
    barSpacing: Dp = 2.dp
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(barSpacing)
    ) {
        amplitudes.forEach { amplitude ->
            val height = minHeight + (amplitude.coerceIn(0f, 1f) * (maxHeight - minHeight).value).dp
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(height)
                    .clip(RoundedCornerShape(1.dp))
                    .background(barColor)
            )
        }
    }
}

/**
 * Simple Audio Player component
 * Matches AudioPlayer.svelte (basic controls)
 */
@Composable
fun AudioPlayerControls(
    isPlaying: Boolean,
    currentTime: Float,
    duration: Float,
    fileName: String,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1F2937))
            .border(1.dp, Color(0xFF374151), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Play/Pause button
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFF374151))
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color(0xFFD1D5DB),
                modifier = Modifier.size(20.dp)
            )
        }
        
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // File name
            Text(
                text = fileName,
                color = Color(0xFFD1D5DB),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            // Progress bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = formatTime(currentTime),
                    color = Color(0xFF9CA3AF),
                    fontSize = 11.sp
                )
                Slider(
                    value = currentTime.coerceIn(0f, duration),
                    onValueChange = { onSeek(it) },
                    enabled = duration > 0f,
                    modifier = Modifier.weight(1f),
                    valueRange = 0f..duration.coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFD1D5DB),
                        activeTrackColor = Color(0xFF6B7280),
                        inactiveTrackColor = Color(0xFF374151)
                    )
                )
                Text(
                    text = formatTime(duration),
                    color = Color(0xFF9CA3AF),
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Format time in seconds to MM:SS
 */
private fun formatTime(seconds: Float): String {
    if (seconds.isNaN() || seconds < 0) return "--:--"
    val mins = (seconds / 60).toInt()
    val secs = (seconds % 60).toInt()
    return "%d:%02d".format(mins, secs)
}

/**
 * Action button for message actions (copy, retry, thumbs up/down)
 */
@Composable
fun MessageActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isActive) Color(0xFF374151) else Color.Transparent)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isActive) PrimaryBlue else Color(0xFF9CA3AF),
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * Animated typing cursor
 */
@Composable
fun TypingCursor(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF9CA3AF)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_blink"
    )
    
    Text(
        text = "▊",
        color = color.copy(alpha = alpha),
        fontSize = 14.sp,
        modifier = modifier
    )
}

/**
 * Pill badge for tags or status
 */
@Composable
fun PillBadge(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF374151),
    textColor: Color = Color(0xFFD1D5DB)
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("copied_text", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}
