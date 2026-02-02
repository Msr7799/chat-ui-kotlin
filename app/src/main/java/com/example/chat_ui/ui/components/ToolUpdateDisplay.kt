package com.example.chat_ui.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat_ui.ui.theme.ThemeColors
import com.example.chat_ui.ui.theme.ThemeManager

/**
 * ToolUpdateDisplay - Component for displaying MCP tool call updates
 * 
 * Similar to: ToolUpdate.svelte in chat-ui
 * 
 * Features:
 * - Shows tool name and status (running, success, error)
 * - Expandable to show input parameters and output
 * - Supports different output types (text, images, JSON)
 */

enum class ToolCallStatus {
    RUNNING,
    SUCCESS,
    ERROR
}

data class ToolCallInfo(
    val id: String,
    val toolName: String,
    val serverId: String? = null,
    val input: Map<String, Any?> = emptyMap(),
    val output: String? = null,
    val error: String? = null,
    val status: ToolCallStatus = ToolCallStatus.RUNNING,
    val images: List<String> = emptyList() // Base64 image data
)

@Composable
fun ToolUpdateDisplay(
    toolCall: ToolCallInfo,
    modifier: Modifier = Modifier
) {
    val themeColors = ThemeManager.getThemeColors(
        ThemeManager.currentPreference,
        isSystemInDarkTheme()
    )
    
    var isExpanded by remember { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "rotation"
    )
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(themeColors.surfaceVariant.copy(alpha = 0.6f))
    ) {
        // Header - clickable to expand
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Status indicator
                ToolStatusIcon(
                    status = toolCall.status,
                    themeColors = themeColors
                )
                
                // Tool name
                Column {
                    Text(
                        text = toolCall.toolName,
                        color = themeColors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (toolCall.serverId != null) {
                        Text(
                            text = toolCall.serverId,
                            color = themeColors.textMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            
            // Expand icon
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = themeColors.textMuted,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(rotationAngle)
            )
        }
        
        // Expanded content
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Input section
                if (toolCall.input.isNotEmpty()) {
                    ToolSection(
                        title = "Input",
                        icon = Icons.Outlined.DataObject,
                        themeColors = themeColors
                    ) {
                        CodeBlock(
                            code = formatJson(toolCall.input),
                            themeColors = themeColors
                        )
                    }
                }
                
                // Output section
                if (toolCall.output != null) {
                    ToolSection(
                        title = "Output",
                        icon = Icons.Outlined.Terminal,
                        themeColors = themeColors
                    ) {
                        CodeBlock(
                            code = toolCall.output,
                            themeColors = themeColors,
                            maxLines = 10
                        )
                    }
                }
                
                // Error section
                if (toolCall.error != null) {
                    ToolSection(
                        title = "Error",
                        icon = Icons.Default.Close,
                        themeColors = themeColors,
                        isError = true
                    ) {
                        Text(
                            text = toolCall.error,
                            color = Color(0xFFEF4444),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                
                // Images section
                if (toolCall.images.isNotEmpty()) {
                    ToolSection(
                        title = "Images (${toolCall.images.size})",
                        icon = Icons.Outlined.Code,
                        themeColors = themeColors
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            toolCall.images.forEach { imageData ->
                                // Display base64 images
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(themeColors.surface)
                                ) {
                                    // Would use AsyncImage with base64 data URL here
                                    Text(
                                        text = "🖼️",
                                        modifier = Modifier.align(Alignment.Center),
                                        fontSize = 24.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolStatusIcon(
    status: ToolCallStatus,
    themeColors: ThemeColors
) {
    val (icon, color) = when (status) {
        ToolCallStatus.RUNNING -> null to themeColors.primary
        ToolCallStatus.SUCCESS -> Icons.Default.Check to Color(0xFF22C55E)
        ToolCallStatus.ERROR -> Icons.Default.Close to Color(0xFFEF4444)
    }
    
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        if (status == ToolCallStatus.RUNNING) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = color
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = status.name,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun ToolSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    themeColors: ThemeColors,
    isError: Boolean = false,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isError) Color(0xFFEF4444) else themeColors.textMuted,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = title,
                color = if (isError) Color(0xFFEF4444) else themeColors.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
        content()
    }
}

@Composable
private fun CodeBlock(
    code: String,
    themeColors: ThemeColors,
    maxLines: Int = Int.MAX_VALUE
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E293B))
            .padding(8.dp)
    ) {
        Text(
            text = code,
            color = Color(0xFF94A3B8),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.horizontalScroll(rememberScrollState())
        )
    }
}

private fun formatJson(map: Map<String, Any?>): String {
    return buildString {
        appendLine("{")
        map.entries.forEachIndexed { index, (key, value) ->
            val valueStr = when (value) {
                is String -> "\"$value\""
                is List<*> -> "[${value.joinToString(", ")}]"
                is Map<*, *> -> "{...}"
                null -> "null"
                else -> value.toString()
            }
            append("  \"$key\": $valueStr")
            if (index < map.size - 1) appendLine(",")
            else appendLine()
        }
        append("}")
    }
}

/**
 * Parse tool calls from message content
 * Looks for patterns like: [Tool: toolName] or ```tool:toolName```
 */
fun parseToolCallsFromContent(content: String): List<ToolCallInfo> {
    val toolCalls = mutableListOf<ToolCallInfo>()
    
    // Pattern 1: [Tool: name] ... [/Tool]
    val toolPattern = Regex("""\[Tool:\s*(\w+)\](.*?)\[/Tool\]""", RegexOption.DOT_MATCHES_ALL)
    toolPattern.findAll(content).forEach { match ->
        val toolName = match.groupValues[1]
        val toolContent = match.groupValues[2].trim()
        
        toolCalls.add(ToolCallInfo(
            id = "${System.currentTimeMillis()}_$toolName",
            toolName = toolName,
            output = toolContent,
            status = ToolCallStatus.SUCCESS
        ))
    }
    
    // Pattern 2: MCP tool call format
    val mcpPattern = Regex("""<tool_call>\s*(\w+)\s*\((.*?)\)\s*</tool_call>""", RegexOption.DOT_MATCHES_ALL)
    mcpPattern.findAll(content).forEach { match ->
        val toolName = match.groupValues[1]
        val args = match.groupValues[2].trim()
        
        toolCalls.add(ToolCallInfo(
            id = "${System.currentTimeMillis()}_$toolName",
            toolName = toolName,
            input = mapOf("args" to args),
            status = ToolCallStatus.RUNNING
        ))
    }
    
    return toolCalls
}
