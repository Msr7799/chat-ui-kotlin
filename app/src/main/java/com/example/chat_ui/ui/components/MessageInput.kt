package com.example.chat_ui.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.chat_ui.data.Attachment
import com.example.chat_ui.data.AttachmentType
import com.example.chat_ui.data.MessageFile
import com.example.chat_ui.ui.theme.AccentRed
import com.example.chat_ui.ui.theme.ThemeColors
import com.example.chat_ui.ui.theme.ThemeManager
import com.example.chat_ui.ui.theme.TextMuted
import com.example.chat_ui.ui.theme.TextSecondary
import java.io.File
import androidx.compose.ui.platform.LocalContext
import com.example.chat_ui.utils.PromptPreferences

@Composable
fun MessageInput(
        onSendMessage: (String) -> Unit,
        isLoading: Boolean = false,
        onStopGeneration: () -> Unit = {},
        attachments: List<Attachment> = emptyList(),
        onAttachImage: () -> Unit = {},
        onAttachFile: () -> Unit = {},
        onRemoveAttachment: (Attachment) -> Unit = {},
        isUploadingAttachment: Boolean = false,
        onCaptureImage: () -> Unit = {},
        onVoiceInput: () -> Unit = {},
        voiceInputText: String = "",
        voiceInputKey: Int = 0,
        pendingFiles: List<MessageFile> = emptyList(),
        onRemovePendingFile: (MessageFile) -> Unit = {},
        onVoiceRecordingSend: ((File) -> Unit)? = null,
        onUrlFileAdded: ((MessageFile) -> Unit)? = null,
        onGenerateImage: () -> Unit = {}
) {
        var messageText by remember { mutableStateOf("") }
        var showAttachmentMenu by remember { mutableStateOf(false) }
        var showVoiceRecorder by remember { mutableStateOf(false) }
        var isTranscribing by remember { mutableStateOf(false) }
        var showUrlFetchModal by remember { mutableStateOf(false) }
        var showPromptHistory by remember { mutableStateOf(false) }
        val themeColors: ThemeColors =
                ThemeManager.getThemeColors(
                        ThemeManager.currentPreference,
                        isSystemInDarkTheme()
                )
        
        val context = LocalContext.current
        
        // Restore saved draft
        LaunchedEffect(Unit) {
            val savedDraft = PromptPreferences.getChatDraft(context)
            if (savedDraft.isNotBlank() && messageText.isBlank()) {
                messageText = savedDraft
            }
        }

        // Append voice input text when key changes (new voice input received)
        LaunchedEffect(voiceInputKey) {
                if (voiceInputKey > 0 && voiceInputText.isNotBlank()) {
                        messageText = if (messageText.isBlank()) {
                                voiceInputText
                        } else {
                                "$messageText $voiceInputText"
                        }
                }
        }
        val focusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current

        val canSend =
                (messageText.isNotBlank() || attachments.isNotEmpty() || pendingFiles.isNotEmpty()) &&
                        !isLoading &&
                        !isUploadingAttachment

        Column(
                modifier =
                        Modifier.fillMaxWidth()
                                .background(themeColors.surface)
                                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
                // Pending Files Preview Row (MessageFile for multimodal API)
                if (pendingFiles.isNotEmpty()) {
                        MessageFilePreviewRow(
                                files = pendingFiles,
                                onRemove = onRemovePendingFile,
                                modifier = Modifier.fillMaxWidth()
                        )
                }
                
                // Legacy Attachment Preview Row
                if (attachments.isNotEmpty()) {
                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .horizontalScroll(rememberScrollState())
                                                .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                                attachments.forEach { attachment ->
                                        AttachmentPreview(
                                                attachment = attachment,
                                                onRemove = { onRemoveAttachment(attachment) }
                                        )
                                }
                        }
                }

                // Main Input Container
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(themeColors.surfaceVariant)
                                        .border(1.dp, themeColors.border, RoundedCornerShape(24.dp))
                                        .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.Bottom
                ) {
                        // Left Actions - Single Attachment Button with Menu
                        Box(
                                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                        ) {
                                IconButton(
                                        onClick = { showAttachmentMenu = true },
                                        modifier = Modifier.size(36.dp),
                                        enabled = !isUploadingAttachment
                                ) {
                                        if (isUploadingAttachment) {
                                                CircularProgressIndicator(
                                                        modifier = Modifier.size(18.dp),
                                                        strokeWidth = 2.dp,
                                                        color = themeColors.primary
                                                )
                                        } else {
                                                Icon(
                                                        imageVector = Icons.Default.AttachFile,
                                                        contentDescription = "Attach",
                                                        tint = themeColors.textSecondary,
                                                        modifier = Modifier.size(20.dp)
                                                )
                                        }
                                }

                                // Attachment Options Menu
                                DropdownMenu(
                                        expanded = showAttachmentMenu,
                                        onDismissRequest = { showAttachmentMenu = false }
                                ) {
                                        DropdownMenuItem(
                                                text = { Text("Attach File") },
                                                onClick = {
                                                        showAttachmentMenu = false
                                                        onAttachFile()
                                                },
                                                leadingIcon = {
                                                        Icon(
                                                                imageVector = Icons.Default.Description,
                                                                contentDescription = null
                                                        )
                                                }
                                        )
                                        DropdownMenuItem(
                                                text = { Text("Choose Image") },
                                                onClick = {
                                                        showAttachmentMenu = false
                                                        onAttachImage()
                                                },
                                                leadingIcon = {
                                                        Icon(
                                                                imageVector = Icons.Default.Image,
                                                                contentDescription = null
                                                        )
                                                }
                                        )
                                        DropdownMenuItem(
                                                text = { Text("Take Photo") },
                                                onClick = {
                                                        showAttachmentMenu = false
                                                        onCaptureImage()
                                                },
                                                leadingIcon = {
                                                        Icon(
                                                                imageVector = Icons.Default.CameraAlt,
                                                                contentDescription = null
                                                        )
                                                }
                                        )
                                        DropdownMenuItem(
                                                text = { Text("Add from URL") },
                                                onClick = {
                                                        showAttachmentMenu = false
                                                        showUrlFetchModal = true
                                                },
                                                leadingIcon = {
                                                        Icon(
                                                                imageVector = Icons.Default.Link,
                                                                contentDescription = null
                                                        )
                                                }
                                        )
                                        DropdownMenuItem(
                                                text = { Text("🎨 Generate Image") },
                                                onClick = {
                                                        showAttachmentMenu = false
                                                        onGenerateImage()
                                                },
                                                leadingIcon = {
                                                        Icon(
                                                                imageVector = Icons.Default.Image,
                                                                contentDescription = null
                                                        )
                                                }
                                        )
                                }
                        }

                        // Text Input with History Icon
                        Box(
                                modifier =
                                        Modifier.weight(1f)
                                                .heightIn(min = 44.dp, max = 200.dp)
                                                .padding(vertical = 12.dp),
                                contentAlignment = Alignment.CenterStart
                        ) {
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Box(modifier = Modifier.weight(1f)) {
                                                if (messageText.isEmpty()) {
                                                        Text(
                                                                text = "Ask anything...",
                                                                color = themeColors.textMuted,
                                                                fontSize = 15.sp
                                                        )
                                                }

                                                BasicTextField(
                                                        value = messageText,
                                                        onValueChange = { 
                                                            messageText = it
                                                            PromptPreferences.saveChatDraft(context, it)
                                                        },
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .focusRequester(focusRequester),
                                                        textStyle =
                                                                TextStyle(color = themeColors.textPrimary, fontSize = 15.sp),
                                                        cursorBrush = SolidColor(themeColors.primary),
                                                        keyboardOptions =
                                                                KeyboardOptions(imeAction = ImeAction.Send),
                                                        keyboardActions =
                                                                KeyboardActions(
                                                                        onSend = {
                                                                                if (canSend) {
                                                                                        onSendMessage(messageText)
                                                                                        messageText = ""
                                                                                        PromptPreferences.saveChatDraft(context, "")
                                                                                        keyboardController?.hide()
                                                                                }
                                                                        }
                                                                )
                                                )
                                        }
                                        
                                        // History Icon Button
                                        Box {
                                                IconButton(
                                                        onClick = { showPromptHistory = true },
                                                        modifier = Modifier.size(32.dp)
                                                ) {
                                                        Icon(
                                                                imageVector = Icons.Default.History,
                                                                contentDescription = "Prompt History",
                                                                tint = themeColors.textSecondary,
                                                                modifier = Modifier.size(18.dp)
                                                        )
                                                }
                                                
                                                // Prompt History Dropdown
                                                DropdownMenu(
                                                        expanded = showPromptHistory,
                                                        onDismissRequest = { showPromptHistory = false },
                                                        modifier = Modifier.widthIn(max = 300.dp)
                                                ) {
                                                        val history = remember(showPromptHistory) { 
                                                                if (showPromptHistory) PromptPreferences.getChatHistory(context) else emptyList() 
                                                        }

                                                        if (history.isEmpty()) {
                                                                DropdownMenuItem(
                                                                        text = { Text("No history available") },
                                                                        onClick = { showPromptHistory = false }
                                                                )
                                                        } else {
                                                                history.forEach { historyPrompt ->
                                                                        DropdownMenuItem(
                                                                                text = { 
                                                                                        Text(
                                                                                                text = historyPrompt,
                                                                                                maxLines = 2,
                                                                                                overflow = TextOverflow.Ellipsis
                                                                                        ) 
                                                                                },
                                                                                onClick = {
                                                                                        messageText = historyPrompt
                                                                                        PromptPreferences.saveChatDraft(context, historyPrompt)
                                                                                        showPromptHistory = false
                                                                                }
                                                                        )
                                                                }
                                                        }
                                                }
                                        }
                                }
                        }

                        // Right Actions
                        Row(
                                modifier = Modifier.padding(end = 4.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                // Microphone Button (when no text) - long press for advanced recorder
                                AnimatedVisibility(
                                        visible = messageText.isEmpty() && !isLoading && !showVoiceRecorder,
                                        enter = fadeIn() + scaleIn(),
                                        exit = fadeOut() + scaleOut()
                                ) {
                                        IconButton(
                                                onClick = { 
                                                        // Short press: use legacy speech-to-text
                                                        if (onVoiceRecordingSend != null) {
                                                                showVoiceRecorder = true
                                                        } else {
                                                                onVoiceInput()
                                                        }
                                                },
                                                modifier = Modifier.size(36.dp)
                                        ) {
                                                Icon(
                                                        imageVector = Icons.Default.Mic,
                                                        contentDescription = "Voice input",
                                                        tint = Color.Gray,
                                                        modifier = Modifier.size(20.dp)
                                                )
                                        }
                                }

                                // Send/Stop Button
                                AnimatedVisibility(
                                        visible = messageText.isNotEmpty() || isLoading,
                                        enter = fadeIn() + scaleIn(),
                                        exit = fadeOut() + scaleOut()
                                ) {
                                        Box(
                                                modifier =
                                                        Modifier.size(36.dp)
                                                                .clip(CircleShape)
                                                                .background(
                                                                        if (canSend || isLoading)
                                                                                themeColors.primary
                                                                        else themeColors.border
                                                                )
                                                                .clickable(
                                                                        enabled =
                                                                                canSend || isLoading
                                                                ) {
                                                                        if (isLoading) {
                                                                                onStopGeneration()
                                                                        } else if (canSend) {
                                                                                onSendMessage(
                                                                                        messageText
                                                                                )
                                                                                messageText = ""
                                                                                PromptPreferences.saveChatDraft(context, "")
                                                                                keyboardController
                                                                                        ?.hide()
                                                                        }
                                                                },
                                                contentAlignment = Alignment.Center
                                        ) {
                                                Icon(
                                                        imageVector =
                                                                if (isLoading) Icons.Default.Stop
                                                                else Icons.Default.ArrowUpward,
                                                        contentDescription =
                                                                if (isLoading) "Stop" else "Send",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(18.dp)
                                                )
                                        }
                                }
                        }
                }
                
                // Voice Recorder Overlay - shows when recording
                VoiceRecorderOverlay(
                        isVisible = showVoiceRecorder,
                        isTranscribing = isTranscribing,
                        onCancel = { showVoiceRecorder = false },
                        onSend = { file ->
                                showVoiceRecorder = false
                                onVoiceRecordingSend?.invoke(file)
                        },
                        onError = { _ ->
                                showVoiceRecorder = false
                        },
                        modifier = Modifier.fillMaxWidth()
                )
                
                // URL Fetch Modal
                UrlFetchModal(
                        isOpen = showUrlFetchModal,
                        onClose = { showUrlFetchModal = false },
                        onFileAdded = { file ->
                                onUrlFileAdded?.invoke(file)
                        },
                        onError = { _ -> }
                )
        }
}

/** Preview for attached files/images */
@Composable
private fun AttachmentPreview(attachment: Attachment, onRemove: () -> Unit) {
        val themeColors: ThemeColors =
                ThemeManager.getThemeColors(
                        ThemeManager.currentPreference,
                        isSystemInDarkTheme()
                )
        Box(
                modifier =
                        Modifier.size(72.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(themeColors.surfaceVariant)
                                .border(1.dp, themeColors.border, RoundedCornerShape(12.dp))
        ) {
                when (attachment.type) {
                        AttachmentType.IMAGE -> {
                                AsyncImage(
                                        model = attachment.url,
                                        contentDescription = attachment.name,
                                        modifier =
                                                Modifier.size(72.dp)
                                                        .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                )
                        }
                        else -> {
                                Column(
                                        modifier = Modifier.size(72.dp).padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.Description,
                                                contentDescription = null,
                                                tint = TextSecondary,
                                                modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                                text = attachment.name,
                                                color = TextMuted,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                        )
                                }
                        }
                }

                // Remove button
                Box(
                        modifier =
                                Modifier.align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(AccentRed.copy(alpha = 0.9f))
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
