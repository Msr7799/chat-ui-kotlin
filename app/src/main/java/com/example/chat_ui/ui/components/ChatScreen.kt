package com.example.chat_ui.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.scale
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private const val USER_MESSAGE_COLLAPSE_CHAR_LIMIT = 280
private const val USER_MESSAGE_COLLAPSE_LINE_LIMIT = 8
private const val USER_MESSAGE_PREVIEW_CHAR_LIMIT = 220
private const val USER_MESSAGE_PREVIEW_LINE_LIMIT = 5
private const val CHAT_ZOOM_MIN = 0.75f
private const val CHAT_ZOOM_MAX = 1.45f
private const val CHAT_ZOOM_STEP = 0.1f

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
        onRegenerate: ((String) -> Unit)? = null,
        onStopGeneration: () -> Unit = {},
        onGenerateImage: () -> Unit = {},
        mcpToolsEnabled: Boolean = true,
        onToggleMCPTools: () -> Unit = {}
) {
        val listState = rememberLazyListState()

        // Get theme colors dynamically
        val themeColors =
                ThemeManager.getThemeColors(ThemeManager.currentPreference, isSystemInDarkTheme())
        val lastUserPrompt = messages.lastOrNull { it.isUser }?.content
        val canRegenerate = messages.any { !it.isUser } && !lastUserPrompt.isNullOrBlank()
        var showRegenerateDialog by remember { mutableStateOf(false) }
        var regeneratePrompt by remember { mutableStateOf("") }
        var chatZoom by rememberSaveable { mutableStateOf(1f) }
        val baseDensity = LocalDensity.current

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
                                themeColors = themeColors,
                                mcpToolsEnabled = mcpToolsEnabled,
                                onToggleMCPTools = onToggleMCPTools
                        )

                        // Messages Area
                        Box(
                                modifier =
                                        Modifier
                                                .weight(1f)
                                                .fillMaxWidth()
                                                .pointerInput(Unit) {
                                                        detectTransformGestures { _, _, zoomChange, _ ->
                                                                if (zoomChange != 1f) {
                                                                        chatZoom =
                                                                                (chatZoom * zoomChange)
                                                                                        .coerceIn(
                                                                                                CHAT_ZOOM_MIN,
                                                                                                CHAT_ZOOM_MAX
                                                                                        )
                                                                }
                                                        }
                                                }
                        ) {
                                if (messages.isEmpty()) {
                                        WelcomeScreen(
                                                onPromptClick = onSendMessage,
                                                themeColors = themeColors
                                        )
                                } else {
                                        CompositionLocalProvider(
                                                LocalDensity provides Density(
                                                        density = baseDensity.density * chatZoom,
                                                        fontScale = baseDensity.fontScale
                                                )
                                        ) {
                                                LazyColumn(
                                                        state = listState,
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentPadding = PaddingValues(
                                                                start = 16.dp,
                                                                end = 16.dp,
                                                                top = 16.dp,
                                                                bottom = 10.dp
                                                        ),
                                                        verticalArrangement = Arrangement.spacedBy(14.dp)
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
                                                                        currentModelId = currentModelId
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

                        // Keep the composer in normal layout flow so it stays above the
                        // navigation bar and moves with the keyboard.
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .background(themeColors.background)
                                                .navigationBarsPadding()
                                                .imePadding()
                        ) {
                                Column {
                                        Row(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(horizontal = 16.dp)
                                                                .padding(top = 2.dp, bottom = 2.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                ChatZoomControls(
                                                        zoom = chatZoom,
                                                        onZoomOut = {
                                                                chatZoom =
                                                                        (chatZoom - CHAT_ZOOM_STEP)
                                                                                .coerceAtLeast(CHAT_ZOOM_MIN)
                                                        },
                                                        onZoomIn = {
                                                                chatZoom =
                                                                        (chatZoom + CHAT_ZOOM_STEP)
                                                                                .coerceAtMost(CHAT_ZOOM_MAX)
                                                        },
                                                        themeColors = themeColors
                                                )

                                                if (canRegenerate) {
                                                        Button(
                                                                onClick = {
                                                                        regeneratePrompt = lastUserPrompt.orEmpty()
                                                                        showRegenerateDialog = true
                                                                },
                                                                colors =
                                                                        ButtonDefaults.buttonColors(
                                                                                containerColor = themeColors.primary,
                                                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                                                        ),
                                                                contentPadding =
                                                                        PaddingValues(
                                                                                horizontal = 14.dp,
                                                                                vertical = 8.dp
                                                                        ),
                                                                shape = RoundedCornerShape(14.dp)
                                                        ) {
                                                                Icon(
                                                                        imageVector = Icons.Default.Edit,
                                                                        contentDescription = null,
                                                                        modifier = Modifier.size(17.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                Text(
                                                                        text = "Edit Prompt & Regenerate",
                                                                        fontSize = 13.sp
                                                                )
                                                        }
                                                } else {
                                                        Spacer(modifier = Modifier.width(1.dp))
                                                }
                                        }

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
        }

        if (showRegenerateDialog) {
                AlertDialog(
                        onDismissRequest = { showRegenerateDialog = false },
                        title = { Text("Edit Prompt") },
                        text = {
                                OutlinedTextField(
                                        value = regeneratePrompt,
                                        onValueChange = { regeneratePrompt = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 4,
                                        maxLines = 8,
                                        placeholder = { Text("Edit the prompt before regenerating") }
                                )
                        },
                        confirmButton = {
                                Button(
                                        onClick = {
                                                onRegenerate?.invoke(regeneratePrompt)
                                                showRegenerateDialog = false
                                        },
                                        enabled = regeneratePrompt.isNotBlank()
                                ) {
                                        Text("Regenerate")
                                }
                        },
                        dismissButton = {
                                TextButton(onClick = { showRegenerateDialog = false }) {
                                        Text("Cancel")
                                }
                        }
                )
        }

}

@Composable
private fun ChatZoomControls(
        zoom: Float,
        onZoomOut: () -> Unit,
        onZoomIn: () -> Unit,
        themeColors: ThemeColors
) {
        Row(
                modifier =
                        Modifier.clip(RoundedCornerShape(9.dp))
                                .background(themeColors.surfaceVariant)
                                .border(1.dp, themeColors.border, RoundedCornerShape(9.dp))
                                .padding(1.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
                IconButton(
                        onClick = onZoomOut,
                        enabled = zoom > CHAT_ZOOM_MIN,
                        modifier = Modifier.size(28.dp)
                ) {
                        Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Zoom out chat",
                                tint = if (zoom > CHAT_ZOOM_MIN) themeColors.primary else themeColors.textMuted,
                                modifier = Modifier.size(17.dp)
                        )
                }
                IconButton(
                        onClick = onZoomIn,
                        enabled = zoom < CHAT_ZOOM_MAX,
                        modifier = Modifier.size(28.dp)
                ) {
                        Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Zoom in chat",
                                tint = if (zoom < CHAT_ZOOM_MAX) themeColors.primary else themeColors.textMuted,
                                modifier = Modifier.size(17.dp)
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
        themeColors: com.example.chat_ui.ui.theme.ThemeColors,
        mcpToolsEnabled: Boolean,
        onToggleMCPTools: () -> Unit
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
                                        .background(themeColors.primary)
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
                                                tint = if (enabledToolsCount > 0) themeColors.primary else themeColors.textSecondary,
                                                modifier = Modifier.size(20.dp)
                                        )
                                        if (enabledToolsCount > 0) {
                                                Text(
                                                        text = "$enabledToolsCount",
                                                        color = themeColors.primary,
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

                                Row(
                                        modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                        text = "Use MCP Tools",
                                                        fontWeight = FontWeight.Medium,
                                                        fontSize = 12.sp,
                                                        color = themeColors.textPrimary
                                                )
                                                Text(
                                                        text = if (mcpToolsEnabled) "Enabled for this chat" else "Disabled for this chat",
                                                        fontSize = 11.sp,
                                                        color = themeColors.textMuted
                                                )
                                        }
                                        Switch(
                                                checked = mcpToolsEnabled,
                                                onCheckedChange = { onToggleMCPTools() }
                                        )
                                }

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
                                                                                if (server.enabled) themeColors.primary
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
        onAlternativeChange: ((Int) -> Unit)? = null
) {
        val messageContent = message.getDisplayContent()
        val shouldCollapseUserMessage =
                message.isUser &&
                        (messageContent.length > USER_MESSAGE_COLLAPSE_CHAR_LIMIT ||
                                messageContent.lineSequence().count() > USER_MESSAGE_COLLAPSE_LINE_LIMIT)
        var isExpanded by rememberSaveable(message.id) { mutableStateOf(!shouldCollapseUserMessage) }

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
                        modifier =
                                if (message.isUser) {
                                        Modifier.widthIn(max = 320.dp)
                                } else {
                                        Modifier.weight(1f)
                                },
                        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
                ) {
                        Box(
                                modifier =
                                        (if (message.isUser) Modifier else Modifier.fillMaxWidth())
                                                .clip(
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
                                if (shouldCollapseUserMessage && !isExpanded) {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(
                                                        text = buildCollapsedUserPreview(messageContent),
                                                        color = themeColors.userBubbleText,
                                                        fontSize = 14.sp,
                                                        lineHeight = 22.sp
                                                )
                                                TextButton(
                                                        onClick = { isExpanded = true },
                                                        contentPadding = PaddingValues(0.dp)
                                                ) {
                                                        Text(
                                                                text = "عرض الرسالة كاملة",
                                                                color = themeColors.userBubbleText,
                                                                fontWeight = FontWeight.SemiBold
                                                        )
                                                }
                                        }
                                } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                // Use MarkdownRenderer for proper markdown formatting
                                                MarkdownRenderer(
                                                        content = messageContent,
                                                        isUser = message.isUser,
                                                        isLoading = isLoading,
                                                        themeColors = themeColors
                                                )
                                                if (shouldCollapseUserMessage) {
                                                        TextButton(
                                                                onClick = { isExpanded = false },
                                                                contentPadding = PaddingValues(0.dp)
                                                        ) {
                                                                Text(
                                                                        text = "تصغير الرسالة",
                                                                        color = themeColors.userBubbleText,
                                                                        fontWeight = FontWeight.SemiBold
                                                                )
                                                        }
                                                }
                                        }
                                }
                        }

                        if (message.files.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                MessageFilesDisplay(
                                        files = message.files,
                                        modifier =
                                                if (message.isUser) {
                                                        Modifier.widthIn(max = 360.dp)
                                                } else {
                                                        Modifier.fillMaxWidth()
                                                }
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
                                val displayedModelId =
                                        if (!message.isUser && message.model.isNotBlank()) {
                                                message.model
                                        } else {
                                                currentModelId
                                        }
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
                                if (!message.isUser && displayedModelId.isNotBlank()) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                                text = displayedModelId,
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

private fun buildCollapsedUserPreview(text: String): String {
        val lines = text.lines().take(USER_MESSAGE_PREVIEW_LINE_LIMIT)
        val joined = lines.joinToString("\n").trim()
        val shortened =
                if (joined.length > USER_MESSAGE_PREVIEW_CHAR_LIMIT) {
                        joined.take(USER_MESSAGE_PREVIEW_CHAR_LIMIT).trimEnd()
                } else {
                        joined
                }
        return if (shortened.length < text.trim().length) "$shortened..." else shortened
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
