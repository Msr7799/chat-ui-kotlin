package com.example.chat_ui.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.draw.scale
import androidx.compose.ui.zIndex
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.chat_ui.mcp.MCPManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.chat_ui.R
import com.example.chat_ui.data.Attachment
import com.example.chat_ui.data.Conversation
import com.example.chat_ui.data.Message
import com.example.chat_ui.data.MessageFile
import com.example.chat_ui.ui.theme.ThemeColors
import com.example.chat_ui.ui.theme.ThemeManager
import java.io.File
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

@Composable
@Suppress("UNUSED_PARAMETER")
fun ChatScreen(
        messages: List<Message>,
        currentConversation: Conversation?,
        isLoading: Boolean = false,
        attachments: List<Attachment> = emptyList(),
        isUploadingAttachment: Boolean = false,
        onMenuClick: () -> Unit,
        onSendMessage: (String) -> Unit,
        onAttachImage: () -> Unit = {},
        onAttachFile: () -> Unit = {},
        onRemoveAttachment: (Attachment) -> Unit = {},
        onShareClick: () -> Unit = {},
        onCaptureImage: () -> Unit = {},
        onVoiceInput: () -> Unit = {},
        voiceInputText: String = "",
        voiceInputKey: Int = 0,
        currentModelId: String = "",
        onCurrentModelClick: () -> Unit = {},
        onMCPSettingsClick: () -> Unit = {},
        pendingFiles: List<MessageFile> = emptyList(),
        onRemovePendingFile: (MessageFile) -> Unit = {},
        onVoiceRecordingSend: ((File) -> Unit)? = null,
        onAlternativeChange: ((String, Int) -> Unit)? = null, // messageId, newIndex
        onUrlFileAdded: ((MessageFile) -> Unit)? = null,
        onRegenerate: (() -> Unit)? = null,
        onStopGeneration: () -> Unit = {},
        onGenerateImage: () -> Unit = {}
) {
        val listState = rememberLazyListState()

        // Get theme colors dynamically
        val themeColors =
                ThemeManager.getThemeColors(ThemeManager.currentPreference, isSystemInDarkTheme())

        // Auto-scroll to bottom when new messages arrive or when loading
        LaunchedEffect(messages.size, isLoading) {
                if (messages.isNotEmpty()) {
                        // Scroll to last item + 1 if loading (to show typing indicator)
                        val canShowTypingIndicator = messages.last().isUser
                        val targetIndex =
                                if (isLoading && canShowTypingIndicator) messages.size
                                else messages.size - 1
                        listState.animateScrollToItem(targetIndex.coerceAtLeast(0))
                }
        }
        Box(
                modifier =
                        Modifier
                                .fillMaxSize()
                                .background(themeColors.background)
        ) {
                Column(modifier = Modifier.fillMaxSize()) {
                        // Top Bar
                        TopBar(
                                modelName = currentModelId,
                                onMenuClick = onMenuClick,
                                onShareClick = onShareClick,
                                onModelClick = onCurrentModelClick,
                                onMCPSettingsClick = onMCPSettingsClick,
                                themeColors = themeColors
                        )

                        // Messages Area
                        Box(
                                modifier =
                                        Modifier
                                                .weight(1f)
                                                .fillMaxWidth()
                        ) {
                                if (messages.isEmpty()) {
                                        WelcomeScreen(
                                                onPromptClick = onSendMessage,
                                                themeColors = themeColors
                                        )
                                } else {
                                        LazyColumn(
                                                state = listState,
                                                modifier = Modifier.fillMaxSize(),
                                                contentPadding = PaddingValues(
                                                        start = 16.dp,
                                                        end = 16.dp,
                                                        top = 16.dp,
                                                        bottom = 100.dp // Extra space for MessageInput
                                                ),
                                                verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                                items(messages.size) { index ->
                                                        val message = messages[index]
                                                        val isLastMessage = index == messages.lastIndex
                                                        MessageBubble(
                                                                message = message,
                                                                isLoading =
                                                                        isLoading &&
                                                                                isLastMessage &&
                                                                                !message.isUser,
                                                                themeColors = themeColors,
                                                                currentModelId = currentModelId,
                                                                onRegenerate = onRegenerate
                                                        )
                                                }

                                                // Show typing indicator when loading and last message is
                                                // from user
                                                if (
                                                        isLoading &&
                                                                (messages.isEmpty() || messages.last().isUser)
                                                ) {
                                                        item {
                                                                TypingIndicator(
                                                                        modifier =
                                                                                Modifier.padding(
                                                                                        start = 8.dp,
                                                                                        top = 8.dp
                                                                                )
                                                                )
                                                        }
                                                }
                                        }
                                }
                        }
                }

                // Message Input anchored at the bottom of the screen.
                Box(
                        modifier =
                                Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .background(themeColors.background)
                                        .zIndex(10f)
                                        .imePadding() 
                                ) {
                        MessageInput(
                                onSendMessage = onSendMessage,
                                isLoading = isLoading,
                                onStopGeneration = onStopGeneration,
                                attachments = attachments,
                                onAttachImage = onAttachImage,
                                onAttachFile = onAttachFile,
                                onRemoveAttachment = onRemoveAttachment,
                                isUploadingAttachment = isUploadingAttachment,
                                onCaptureImage = onCaptureImage,
                                onVoiceInput = onVoiceInput,
                                voiceInputText = voiceInputText,
                                voiceInputKey = voiceInputKey,
                                pendingFiles = pendingFiles,
                                onRemovePendingFile = onRemovePendingFile,
                                onVoiceRecordingSend = onVoiceRecordingSend,
                                onUrlFileAdded = onUrlFileAdded,
                                onGenerateImage = onGenerateImage
                        )
                }
        }

}
@Composable
private fun TopBar(
        modelName: String,
        onMenuClick: () -> Unit,
        onShareClick: () -> Unit,
        onModelClick: () -> Unit,
        onMCPSettingsClick: () -> Unit,
        themeColors: com.example.chat_ui.ui.theme.ThemeColors
) {
        val servers by MCPManager.servers.collectAsState()
        val tools by MCPManager.tools.collectAsState()
        var showMCPDropdown by remember { mutableStateOf(false) }
        
        val enabledServers = servers.filter { it.enabled }
        val enabledToolsCount = tools.count { tool -> 
                enabledServers.any { it.id == tool.serverId }
        }
        
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .background(themeColors.background)
                                .padding(top = 20.dp, start = 8.dp, end = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
                IconButton(onClick = onMenuClick) {
                        Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menu",
                                tint = themeColors.textPrimary
                        )
                }

                // Model name in center with green indicator
                Row(
                        modifier = Modifier
                                .weight(1f)
                                .clickable { onModelClick() },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        // Green status indicator
                        Box(
                                modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF4CAF50))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Model name with underline
                        Text(
                                text = if (modelName.isNotBlank()) modelName else "Select Model",
                                color = themeColors.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                                maxLines = 1
                        )
                }

                // MCP Tools dropdown
                Box {
                        IconButton(onClick = { showMCPDropdown = true }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                                imageVector = Icons.Default.Build,
                                                contentDescription = "MCP Tools",
                                                tint = if (enabledToolsCount > 0) Color(0xFF4CAF50) else themeColors.textSecondary,
                                                modifier = Modifier.size(20.dp)
                                        )
                                        if (enabledToolsCount > 0) {
                                                Text(
                                                        text = "$enabledToolsCount",
                                                        color = Color(0xFF4CAF50),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                )
                                        }
                                }
                        }
                        
                        DropdownMenu(
                                expanded = showMCPDropdown,
                                onDismissRequest = { showMCPDropdown = false },
                                modifier = Modifier.widthIn(max = 220.dp)
                        ) {
                                // Header
                                Text(
                                        text = "${servers.size} MCPs | $enabledToolsCount tools",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                                
                                HorizontalDivider()
                                
                                // Server list with toggles
                                if (servers.isEmpty()) {
                                        Text(
                                                text = "No MCP servers configured",
                                                fontSize = 12.sp,
                                                color = themeColors.textMuted,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                }
                                
                                servers.forEach { server ->
                                        val serverTools = tools.filter { it.serverId == server.id }
                                        Row(
                                                modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { MCPManager.toggleServerEnabled(server.id) }
                                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.weight(1f)
                                                ) {
                                                        // Status indicator
                                                        Box(
                                                                modifier = Modifier
                                                                        .size(6.dp)
                                                                        .clip(CircleShape)
                                                                        .background(
                                                                                if (server.enabled) Color(0xFF4CAF50)
                                                                                else themeColors.textMuted
                                                                        )
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Column {
                                                                Text(
                                                                        text = server.name,
                                                                        fontSize = 12.sp,
                                                                        maxLines = 1,
                                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                                )
                                                                Text(
                                                                        text = "${serverTools.size} tools",
                                                                        fontSize = 10.sp,
                                                                        color = themeColors.textMuted
                                                                )
                                                        }
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Switch(
                                                        checked = server.enabled,
                                                        onCheckedChange = { MCPManager.toggleServerEnabled(server.id) },
                                                        modifier = Modifier
                                                                .height(20.dp)
                                                                .scale(0.7f)
                                                )
                                        }
                                }
                                
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                
                                // MCP Settings button
                                Row(
                                        modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                        showMCPDropdown = false
                                                        onMCPSettingsClick()
                                                }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.Settings,
                                                contentDescription = "MCP Settings",
                                                tint = themeColors.textSecondary,
                                                modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                                text = "MCP Settings",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = themeColors.textSecondary
                                        )
                                }
                        }
                }

                IconButton(onClick = onShareClick) {
                        Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = themeColors.textSecondary
                        )
                }
        }
}

@Composable
@Suppress("UNUSED_PARAMETER")
private fun WelcomeScreen(
        onPromptClick: (String) -> Unit,
        themeColors: com.example.chat_ui.ui.theme.ThemeColors
) {
        val isDarkTheme = themeColors.isDark
        
        Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
        ) {
                // Logo with text - white for dark theme, black for light theme
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                ) {
                        Image(
                                painter = painterResource(
                                        id = if (isDarkTheme) R.drawable.chatui_logo else R.drawable.chatui_logo_black
                                ),
                                contentDescription = "ChatUI Logo",
                                modifier = Modifier.size(72.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Text(
                                text = "ChatUI",
                                color = themeColors.textPrimary,
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Bold
                        )
                }
        }
}

@Composable
private fun MessageBubble(
        message: Message,
        isLoading: Boolean = false,
        themeColors: ThemeColors,
        currentModelId: String = "",
        onAlternativeChange: ((Int) -> Unit)? = null,
        onRegenerate: (() -> Unit)? = null
) {
        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
        ) {
                if (!message.isUser) {
                        // AI Avatar (uses theme primary color)
                        Box(
                                modifier =
                                        Modifier.size(32.dp)
                                                .clip(CircleShape)
                                                .background(themeColors.primary),
                                contentAlignment = Alignment.Center
                        ) {
                                Text(
                                        text = "AI",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                }

                Column(
                        modifier = Modifier.widthIn(max = 320.dp),
                        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
                ) {
                        Box(
                                modifier =
                                        Modifier.clip(
                                                        RoundedCornerShape(
                                                                topStart = 16.dp,
                                                                topEnd = 16.dp,
                                                                bottomStart =
                                                                        if (message.isUser) 16.dp
                                                                        else 4.dp,
                                                                bottomEnd =
                                                                        if (message.isUser) 4.dp
                                                                        else 16.dp
                                                        )
                                                )
                                                .background(
                                                        if (message.isUser) themeColors.userBubble
                                                        else themeColors.assistantBubble
                                                )
                                                .padding(12.dp)
                        ) {
                                // Use MarkdownRenderer for proper markdown formatting
                                MarkdownRenderer(
                                        content = message.getDisplayContent(),
                                        isUser = message.isUser,
                                        isLoading = isLoading,
                                        themeColors = themeColors
                                )
                        }
                        
                        // Alternatives navigation (for AI messages with alternatives)
                        if (!message.isUser && message.hasAlternatives()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                MessageAlternatives(
                                        currentIndex = message.currentAlternativeIndex,
                                        totalCount = message.getAlternativesCount(),
                                        onPrevious = { onAlternativeChange?.invoke(message.currentAlternativeIndex - 1) },
                                        onNext = { onAlternativeChange?.invoke(message.currentAlternativeIndex + 1) },
                                        onRegenerate = { onRegenerate?.invoke() },
                                        isLoading = isLoading
                                )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Time + Copy button + optional model name for AI messages
                        val context = LocalContext.current
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                        ) {
                                Text(
                                        text = formatMessageTime(message.timestamp),
                                        color = themeColors.textMuted,
                                        fontSize = 11.sp
                                )
                                
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                // Copy button for both user and AI messages
                                IconButton(
                                        onClick = {
                                                copyToClipboard(context, message.getDisplayContent())
                                                Toast.makeText(context, "تم النسخ", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                ) {
                                        Icon(
                                                imageVector = Icons.Outlined.ContentCopy,
                                                contentDescription = "نسخ",
                                                tint = themeColors.textMuted,
                                                modifier = Modifier.size(14.dp)
                                        )
                                }
                                
                                // Regenerate button for AI messages only
                                if (!message.isUser) {
                                        IconButton(
                                                onClick = { onRegenerate?.invoke() },
                                                modifier = Modifier.size(24.dp)
                                        ) {
                                                Icon(
                                                        imageVector = Icons.Outlined.Refresh,
                                                        contentDescription = "إعادة التوليد",
                                                        tint = themeColors.textMuted,
                                                        modifier = Modifier.size(14.dp)
                                                )
                                        }
                                }
                                
                                if (!message.isUser && currentModelId.isNotBlank()) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                                text = currentModelId,
                                                color = themeColors.textSecondary,
                                                fontSize = 11.sp
                                        )
                                }
                        }
                }

                if (message.isUser) {
                        Spacer(modifier = Modifier.width(8.dp))
                        // User Avatar (uses theme primary color)
                        Box(
                                modifier =
                                        Modifier.size(32.dp)
                                                .clip(CircleShape)
                                                .background(themeColors.primary),
                                contentAlignment = Alignment.Center
                        ) {
                                Text(
                                        text = "U",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                )
                        }
                }
        }
}

private fun formatMessageTime(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
}

private fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("message", text)
        clipboard.setPrimaryClip(clip)
}
