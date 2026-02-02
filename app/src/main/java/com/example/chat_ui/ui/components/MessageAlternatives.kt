package com.example.chat_ui.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat_ui.ui.theme.ThemeColors
import com.example.chat_ui.ui.theme.ThemeManager

/**
 * MessageAlternatives - Navigation between alternative message responses
 * 
 * Similar to: Alternatives.svelte in chat-ui
 * 
 * Features:
 * - Navigate between alternatives (< 1/3 >)
 * - Regenerate button to create new alternative
 * - Only shows for assistant messages with alternatives
 */

@Composable
fun MessageAlternatives(
    currentIndex: Int,
    totalCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRegenerate: () -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    val themeColors = ThemeManager.getThemeColors(
        ThemeManager.currentPreference,
        isSystemInDarkTheme()
    )
    
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(themeColors.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Previous button
        IconButton(
            onClick = onPrevious,
            enabled = currentIndex > 0 && !isLoading,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = "Previous alternative",
                tint = if (currentIndex > 0 && !isLoading) 
                    themeColors.textSecondary 
                else 
                    themeColors.textMuted.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
        }
        
        // Counter display
        Text(
            text = "${currentIndex + 1}/$totalCount",
            color = themeColors.textMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        
        // Next button
        IconButton(
            onClick = onNext,
            enabled = currentIndex < totalCount - 1 && !isLoading,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Next alternative",
                tint = if (currentIndex < totalCount - 1 && !isLoading) 
                    themeColors.textSecondary 
                else 
                    themeColors.textMuted.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
        }
        
        // Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(16.dp)
                .background(themeColors.border)
        )
        
        // Regenerate button
        IconButton(
            onClick = onRegenerate,
            enabled = !isLoading,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Regenerate response",
                tint = if (!isLoading) 
                    themeColors.textSecondary 
                else 
                    themeColors.textMuted.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Compact alternatives indicator - just shows the counter
 */
@Composable
fun AlternativesIndicator(
    currentIndex: Int,
    totalCount: Int,
    modifier: Modifier = Modifier
) {
    val themeColors = ThemeManager.getThemeColors(
        ThemeManager.currentPreference,
        isSystemInDarkTheme()
    )
    
    AnimatedVisibility(
        visible = totalCount > 1,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(themeColors.surfaceVariant.copy(alpha = 0.6f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = "${currentIndex + 1}/$totalCount",
                color = themeColors.textMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
