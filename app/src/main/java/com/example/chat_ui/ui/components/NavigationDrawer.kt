package com.example.chat_ui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.chat_ui.R
import com.example.chat_ui.data.Conversation
import com.example.chat_ui.data.models.User
import com.example.chat_ui.ui.theme.ThemeColors
import com.example.chat_ui.ui.theme.ThemeManager
import com.example.chat_ui.ui.theme.ThemePreference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NavigationDrawerContent(
        conversations: List<Conversation>,
        currentConversation: Conversation?,
        currentUser: User? = null,
        onConversationClick: (Conversation) -> Unit,
        onNewChat: () -> Unit,
        onDeleteConversation: (Conversation) -> Unit,
        onSettingsClick: () -> Unit = {},
        onModelsClick: () -> Unit = {},
        onGalleryClick: () -> Unit = {},
        onImageGalleryClick: () -> Unit = {},
        onVideoGenerationClick: () -> Unit = {},
        onVideoGalleryClick: () -> Unit = {},
        onProfileClick: () -> Unit = {},
        onMCPClick: () -> Unit = {}
) {
        val themeColors: ThemeColors =
                ThemeManager.getThemeColors(ThemeManager.currentPreference, isSystemInDarkTheme())

        ModalDrawerSheet(
                modifier = Modifier.width(290.dp),
                drawerContainerColor = themeColors.surface
        ) {
                Column(modifier = Modifier.fillMaxHeight().padding(vertical = 16.dp)) {
                        // Header with Avatar - Clickable to open Profile/Sign-In
                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .padding(horizontal = 16.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable { onProfileClick() }
                                                .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                // Avatar
                                if (currentUser?.avatarUrl != null) {
                                        AsyncImage(
                                                model = currentUser.avatarUrl,
                                                contentDescription = "User Avatar",
                                                modifier =
                                                        Modifier.size(40.dp)
                                                                .clip(CircleShape)
                                                                .background(themeColors.primary)
                                        )
                                } else {
                                        Box(
                                                modifier =
                                                        Modifier.size(40.dp)
                                                                .clip(CircleShape)
                                                                .background(themeColors.primary),
                                                contentAlignment = Alignment.Center
                                        ) {
                                                Text(
                                                        text =
                                                                currentUser
                                                                        ?.name
                                                                        ?.firstOrNull()
                                                                        ?.uppercase()
                                                                        ?: "U",
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 16.sp
                                                )
                                        }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                                text = currentUser?.name
                                                                ?: stringResource(
                                                                        R.string.sign_in_google
                                                                ),
                                                color = themeColors.textPrimary,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                                text = currentUser?.email
                                                                ?: stringResource(
                                                                        R.string.sign_in_tap
                                                                ),
                                                color = themeColors.textSecondary,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                        )
                                }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Quick Action Icons Row
                        Row(
                                modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                // New Chat Icon
                                IconButton(
                                        onClick = onNewChat,
                                        modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(themeColors.surfaceVariant)
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = stringResource(R.string.new_chat),
                                                tint = themeColors.textPrimary,
                                                modifier = Modifier.size(24.dp)
                                        )
                                }
                                
                                // Models Icon
                                IconButton(
                                        onClick = onModelsClick,
                                        modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(themeColors.surfaceVariant)
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.ViewModule,
                                                contentDescription = stringResource(R.string.models),
                                                tint = themeColors.textPrimary,
                                                modifier = Modifier.size(24.dp)
                                        )
                                }
                                
                                // Gallery Icon (Legacy - redirects to Image Gallery)
                                IconButton(
                                        onClick = onImageGalleryClick,
                                        modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(themeColors.surfaceVariant)
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.Image,
                                                contentDescription = "Image Gallery",
                                                tint = themeColors.textPrimary,
                                                modifier = Modifier.size(24.dp)
                                        )
                                }
                                
                                // Video Gallery Icon
                                IconButton(
                                        onClick = onVideoGalleryClick,
                                        modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(themeColors.surfaceVariant)
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.Videocam,
                                                contentDescription = stringResource(R.string.video_gallery_nav),
                                                tint = themeColors.textPrimary,
                                                modifier = Modifier.size(24.dp)
                                        )
                                }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Conversations List Header
                        Text(
                                text = stringResource(R.string.recent_chats),
                                color = themeColors.textMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Conversations List
                        LazyColumn(
                                modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(themeColors.surfaceVariant)
                        ) {
                                items(conversations) { conversation ->
                                        ConversationItem(
                                                conversation = conversation,
                                                isSelected =
                                                        currentConversation?.id == conversation.id,
                                                onClick = { onConversationClick(conversation) },
                                                onDelete = { onDeleteConversation(conversation) }
                                        )
                                }
                        }

                        // Border separator with shadow before bottom actions
                        Box(
                                modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                        .shadow(
                                                elevation = 2.dp,
                                                shape = RoundedCornerShape(1.dp)
                                        )
                                        .height(1.dp)
                                        .background(themeColors.textMuted.copy(alpha = 0.2f))
                        )

                        // Bottom Actions
                        Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                                // Theme Toggle - Light/Dark only
                                val currentTheme = ThemeManager.currentPreference
                                val isDark = ThemeManager.isDarkMode

                                Row(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable { ThemeManager.switchTheme() }
                                                        .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Icon(
                                                imageVector =
                                                        if (isDark) Icons.Default.DarkMode
                                                        else Icons.Default.LightMode,
                                                contentDescription = "Theme",
                                                tint = themeColors.textSecondary,
                                                modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                                text = if (isDark) "Dark Mode" else "Light Mode",
                                                color = themeColors.textSecondary,
                                                fontSize = 14.sp
                                        )
                                }

                                // MCP Servers
                                Row(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable { onMCPClick() }
                                                        .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.DeviceHub,
                                                contentDescription = "MCP Servers",
                                                tint = themeColors.textSecondary,
                                                modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                                text = "MCP Servers",
                                                color = themeColors.textSecondary,
                                                fontSize = 14.sp
                                        )
                                }

                                // Settings
                                Row(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable { onSettingsClick() }
                                                        .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.Settings,
                                                contentDescription =
                                                        stringResource(R.string.settings),
                                                tint = themeColors.textSecondary,
                                                modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                                text = stringResource(R.string.settings),
                                                color = themeColors.textSecondary,
                                                fontSize = 14.sp
                                        )
                                }
                        }
                }
        }
}

@Composable
private fun QuickActionButton(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        label: String,
        modifier: Modifier = Modifier,
        onClick: () -> Unit = {}
) {
        val themeColors: ThemeColors =
                ThemeManager.getThemeColors(ThemeManager.currentPreference, isSystemInDarkTheme())
        Row(
                modifier =
                        modifier.clip(RoundedCornerShape(8.dp))
                                .background(themeColors.surfaceVariant)
                                .clickable { onClick() }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
        ) {
                Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = themeColors.textSecondary,
                        modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = label, color = themeColors.textSecondary, fontSize = 12.sp)
        }
}

@Composable
private fun ConversationItem(
        conversation: Conversation,
        isSelected: Boolean,
        onClick: () -> Unit,
        onDelete: () -> Unit
) {
        var showMenu by remember { mutableStateOf(false) }
        val themeColors: ThemeColors =
                ThemeManager.getThemeColors(ThemeManager.currentPreference, isSystemInDarkTheme())

        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                        if (isSelected) themeColors.surfaceVariant
                                        else Color.Transparent
                                )
                                .clickable { onClick() }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
                Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = null,
                        tint =
                                if (isSelected) themeColors.textPrimary
                                else themeColors.textSecondary,
                        modifier = Modifier.size(18.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = conversation.title,
                                color =
                                        if (isSelected) themeColors.textPrimary
                                        else themeColors.textSecondary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                        )
                        Text(
                                text = formatTimestamp(conversation.timestamp),
                                color = themeColors.textMuted,
                                fontSize = 11.sp
                        )
                }

                Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More",
                                        tint = themeColors.textMuted,
                                        modifier = Modifier.size(16.dp)
                                )
                        }

                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                        text = { Text("Delete") },
                                        onClick = {
                                                showMenu = false
                                                onDelete()
                                        },
                                        leadingIcon = {
                                                Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.error
                                                )
                                        }
                                )
                        }
                }
        }
}

private fun formatTimestamp(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
                diff < 60000 -> "Just now"
                diff < 3600000 -> "${diff / 60000}m ago"
                diff < 86400000 -> "${diff / 3600000}h ago"
                diff < 604800000 -> "${diff / 86400000}d ago"
                else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
        }
}
