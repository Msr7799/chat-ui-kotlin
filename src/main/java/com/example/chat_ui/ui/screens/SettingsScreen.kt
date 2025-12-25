package com.example.chat_ui.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat_ui.R
import com.example.chat_ui.ui.theme.LanguageManager
import com.example.chat_ui.ui.theme.ThemeManager
import com.example.chat_ui.ui.theme.ThemePreference
import com.example.chat_ui.ui.theme.selectedColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
        onBackClick: () -> Unit,
        onProfileClick: () -> Unit = {},
        onAboutClick: () -> Unit = {},
        onApiSettingsClick: () -> Unit = {},
        onMCPSettingsClick: () -> Unit = {},
        onDebugClick: () -> Unit = {}
) {
        // Theme is controlled by ThemeManager, not this local state
        var notificationsEnabled by remember { mutableStateOf(true) }
        var showClearDialog by remember { mutableStateOf(false) }
        var showAboutDialog by remember { mutableStateOf(false) }
        val colorScheme = MaterialTheme.colorScheme
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        // Clear Conversations Confirmation Dialog
        if (showClearDialog) {
                AlertDialog(
                        onDismissRequest = { showClearDialog = false },
                        title = {
                                Text(
                                        text = stringResource(R.string.clear_conversations),
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSurface
                                )
                        },
                        text = {
                                Text(
                                        text = stringResource(R.string.clear_conversations_confirm),
                                        color = colorScheme.onSurfaceVariant
                                )
                        },
                        confirmButton = {
                                Button(
                                        onClick = {
                                                scope.launch {
                                                        val count =
                                                                com.example.chat_ui.data.firebase
                                                                        .FirestoreManager
                                                                        .clearAllConversations()
                                                        showClearDialog = false
                                                        Toast.makeText(
                                                                        context,
                                                                        "Cleared $count conversations",
                                                                        Toast.LENGTH_SHORT
                                                                )
                                                                .show()
                                                }
                                        },
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor = colorScheme.error
                                                )
                                ) { Text(stringResource(R.string.delete_all)) }
                        },
                        dismissButton = {
                                OutlinedButton(onClick = { showClearDialog = false }) {
                                        Text(stringResource(R.string.cancel))
                                }
                        },
                        containerColor = colorScheme.surface
                )
        }

        // About Dialog
        if (showAboutDialog) {
                val isDark = ThemeManager.isDarkMode
                AlertDialog(
                        onDismissRequest = { showAboutDialog = false },
                        title = {
                                Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                        // Logo - White for dark theme, Black for light theme
                                        Image(
                                                painter = painterResource(
                                                        id = if (isDark) R.drawable.fulltext_logo_white_small
                                                             else R.drawable.fulltext_logo_black_small
                                                ),
                                                contentDescription = "App Logo",
                                                modifier = Modifier
                                                        .height(48.dp)
                                                        .padding(bottom = 12.dp)
                                        )
                                        Text(
                                                text = stringResource(R.string.about_chat_ui),
                                                fontWeight = FontWeight.Bold,
                                                color = colorScheme.onSurface
                                        )
                                }
                        },
                        text = {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        // Version
                                        AboutInfoRow(
                                                icon = Icons.Default.Info,
                                                label = stringResource(R.string.version),
                                                value = "1.0.0"
                                        )

                                        // Developer
                                        AboutInfoRow(
                                                icon = Icons.Default.Person,
                                                label = stringResource(R.string.developer),
                                                value = stringResource(R.string.developer_name)
                                        )

                                        // Report Issues
                                        AboutInfoRow(
                                                icon = Icons.Default.Email,
                                                label = stringResource(R.string.report_issues),
                                                value = stringResource(R.string.developer_email),
                                                isClickable = true,
                                                onClick = {
                                                        val intent =
                                                                Intent(Intent.ACTION_SENDTO).apply {
                                                                        data =
                                                                                Uri.parse(
                                                                                        "mailto:alromaihi2224@gmail.com"
                                                                                )
                                                                        putExtra(
                                                                                Intent.EXTRA_SUBJECT,
                                                                                "Chat UI - Bug Report"
                                                                        )
                                                                }
                                                        context.startActivity(intent)
                                                }
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Features
                                        Text(
                                                text = stringResource(R.string.features),
                                                fontWeight = FontWeight.SemiBold,
                                                color = colorScheme.primary,
                                                fontSize = 14.sp
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                FeatureItem(stringResource(R.string.feature_ai))
                                                FeatureItem(stringResource(R.string.feature_image))
                                                FeatureItem(stringResource(R.string.feature_themes))
                                                FeatureItem(
                                                        stringResource(R.string.feature_languages)
                                                )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Copyright
                                        Text(
                                                text = stringResource(R.string.copyright),
                                                color = colorScheme.onSurfaceVariant,
                                                fontSize = 12.sp,
                                                modifier = Modifier.fillMaxWidth(),
                                                textAlign =
                                                        androidx.compose.ui.text.style.TextAlign
                                                                .Center
                                        )
                                }
                        },
                        confirmButton = {
                                TextButton(onClick = { showAboutDialog = false }) {
                                        Text(stringResource(R.string.close))
                                }
                        },
                        containerColor = colorScheme.surface
                )
        }

        Column(modifier = Modifier.fillMaxSize().background(colorScheme.background)) {
                // Top Bar
                TopAppBar(
                        title = {
                                Text(
                                        text = stringResource(R.string.settings),
                                        color = colorScheme.onBackground,
                                        fontWeight = FontWeight.SemiBold
                                )
                        },
                        navigationIcon = {
                                IconButton(onClick = onBackClick) {
                                        Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Back",
                                                tint = colorScheme.onBackground
                                        )
                                }
                        },
                        colors =
                                TopAppBarDefaults.topAppBarColors(
                                        containerColor = colorScheme.background
                                )
                )

                Column(
                        modifier =
                                Modifier.fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                        // Profile Section
                        SettingsSection(title = stringResource(R.string.account)) {
                                SettingsItem(
                                        icon = Icons.Default.Person,
                                        title = stringResource(R.string.profile),
                                        subtitle = stringResource(R.string.manage_account),
                                        onClick = onProfileClick
                                )
                        }

                        // API Section
                        SettingsSection(title = stringResource(R.string.api_configuration)) {
                                SettingsItem(
                                        icon = Icons.Default.Settings,
                                        title = stringResource(R.string.api_settings),
                                        subtitle = stringResource(R.string.configure_api),
                                        onClick = onApiSettingsClick
                                )
                                SettingsItem(
                                        icon = Icons.Default.Extension,
                                        title = "MCP Servers",
                                        subtitle = "إدارة خوادم Model Context Protocol",
                                        onClick = onMCPSettingsClick
                                )
                                SettingsItem(
                                        icon = Icons.Default.BugReport,
                                        title = "Debug Console",
                                        subtitle = "اختبار النماذج والـ Backend",
                                        onClick = onDebugClick
                                )
                        }

                        // Appearance Section
                        SettingsSection(title = stringResource(R.string.appearance)) {
                                // Theme Selector
                                ThemeSelectorItem()

                                // Language Selector
                                LanguageSelectorItem()
                        }

                        // Notifications Section
                        SettingsSection(title = stringResource(R.string.notifications)) {
                                SettingsToggleItem(
                                        icon = Icons.Default.Notifications,
                                        title = stringResource(R.string.push_notifications),
                                        subtitle = stringResource(R.string.receive_notifications),
                                        isChecked = notificationsEnabled,
                                        onCheckedChange = { notificationsEnabled = it }
                                )
                        }

                        // Data & Storage Section
                        SettingsSection(title = stringResource(R.string.data_storage)) {
                                SettingsItem(
                                        icon = Icons.Default.Delete,
                                        title = stringResource(R.string.clear_conversations),
                                        subtitle = stringResource(R.string.delete_all_chat_history),
                                        onClick = { showClearDialog = true },
                                        isDestructive = true
                                )
                        }

                        // Privacy & Security Section
                        SettingsSection(title = stringResource(R.string.privacy_security)) {
                                SettingsItem(
                                        icon = Icons.Default.Security,
                                        title = stringResource(R.string.privacy_policy),
                                        onClick = {
                                                // Open privacy policy URL
                                                val intent =
                                                        Intent(
                                                                Intent.ACTION_VIEW,
                                                                Uri.parse("https://github.com")
                                                        )
                                                context.startActivity(intent)
                                        }
                                )
                        }

                        // About Section
                        SettingsSection(title = stringResource(R.string.about)) {
                                SettingsItem(
                                        icon = Icons.Default.Info,
                                        title = stringResource(R.string.about_chat_ui),
                                        subtitle = stringResource(R.string.version) + " 1.0.0",
                                        onClick = {
                                                onAboutClick()
                                                showAboutDialog = true
                                        }
                                )
                        }

                        // Logout Button
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(colorScheme.surfaceVariant)
                                                .clickable {}
                                                .padding(16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Logout,
                                        contentDescription = "Logout",
                                        tint = colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                        text = stringResource(R.string.sign_out),
                                        color = colorScheme.error,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 15.sp
                                )
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                }
        }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
        val colorScheme = MaterialTheme.colorScheme
        Column {
                Text(
                        text = title,
                        color = if (ThemeManager.isDarkMode) colorScheme.onSurfaceVariant else colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Column(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colorScheme.surfaceVariant)
                ) { content() }
        }
}

@Composable
private fun SettingsItem(
        icon: ImageVector,
        title: String,
        subtitle: String? = null,
        isDestructive: Boolean = false,
        onClick: () -> Unit
) {
        val colorScheme = MaterialTheme.colorScheme
        Row(
                modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
                Box(
                        modifier =
                                Modifier.size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                                if (isDestructive)
                                                        colorScheme.error.copy(alpha = 0.1f)
                                                else colorScheme.outline.copy(alpha = 0.3f)
                                        ),
                        contentAlignment = Alignment.Center
                ) {
                        Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint =
                                        if (isDestructive) colorScheme.error
                                        else colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                        )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = title,
                                color =
                                        if (isDestructive) colorScheme.error
                                        else colorScheme.onSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                        )
                        if (subtitle != null) {
                                Text(text = subtitle, color = colorScheme.outline, fontSize = 13.sp)
                        }
                }

                Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                )
        }
}

@Composable
private fun SettingsToggleItem(
        icon: ImageVector,
        title: String,
        subtitle: String? = null,
        isChecked: Boolean,
        onCheckedChange: (Boolean) -> Unit
) {
        val colorScheme = MaterialTheme.colorScheme
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .clickable { onCheckedChange(!isChecked) }
                                .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
                Box(
                        modifier =
                                Modifier.size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colorScheme.outline.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                ) {
                        Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                        )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = title,
                                color = colorScheme.onSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                        )
                        if (subtitle != null) {
                                Text(text = subtitle, color = colorScheme.outline, fontSize = 13.sp)
                        }
                }

                Switch(
                        checked = isChecked,
                        onCheckedChange = onCheckedChange,
                        colors =
                                SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = colorScheme.primary,
                                        uncheckedThumbColor = colorScheme.outline,
                                        uncheckedTrackColor = colorScheme.outline.copy(alpha = 0.3f)
                                )
                )
        }
}

/** Theme selector item with theme color circles */
@Composable
private fun ThemeSelectorItem() {
        val currentTheme = ThemeManager.currentPreference
        val colorScheme = MaterialTheme.colorScheme
        val selectedColor = selectedColor()

        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Box(
                                modifier =
                                        Modifier.size(36.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(colorScheme.outline.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                        ) {
                                Icon(
                                        imageVector = Icons.Default.DarkMode,
                                        contentDescription = null,
                                        tint = colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = "Theme",
                                        color = colorScheme.onSurface,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                )
                                Text(
                                        text = ThemeManager.getThemeName(currentTheme),
                                        color = selectedColor,
                                        fontSize = 13.sp
                                )
                        }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Theme Options Row - Light and Dark only
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                        ThemeOption(
                                color = Color(0xFFF9FAFB),
                                label = "Light",
                                isSelected = currentTheme == ThemePreference.LIGHT,
                                onClick = { ThemeManager.setTheme(ThemePreference.LIGHT) },
                                modifier = Modifier.weight(1f)
                        )
                        ThemeOption(
                                color = Color(0xFF0D0F12),
                                label = "Dark",
                                isSelected = currentTheme == ThemePreference.DARK,
                                onClick = { ThemeManager.setTheme(ThemePreference.DARK) },
                                modifier = Modifier.weight(1f)
                        )
                }
        }
}

@Composable
private fun ThemeOption(
        color: Color, 
        label: String, 
        isSelected: Boolean, 
        onClick: () -> Unit,
        modifier: Modifier = Modifier
) {
        val colorScheme = MaterialTheme.colorScheme
        val selectedColor = selectedColor()
        Box(
                modifier = modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                                if (isSelected) selectedColor.copy(alpha = 0.15f)
                                else colorScheme.surfaceVariant
                        )
                        .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) selectedColor
                                        else colorScheme.outline.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onClick() }
                        .padding(vertical = 16.dp, horizontal = 12.dp),
                contentAlignment = Alignment.Center
        ) {
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                ) {
                        Box(
                                modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(1.dp, colorScheme.outline.copy(alpha = 0.5f), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                                text = label,
                                color = if (isSelected) selectedColor else colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                }
        }
}

/** About Info Row for the About dialog */
@Composable
private fun AboutInfoRow(
        icon: ImageVector,
        label: String,
        value: String,
        isClickable: Boolean = false,
        onClick: () -> Unit = {}
) {
        val colorScheme = MaterialTheme.colorScheme
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .then(
                                        if (isClickable) Modifier.clickable { onClick() }
                                        else Modifier
                                ),
                verticalAlignment = Alignment.CenterVertically
        ) {
                Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                        Text(text = label, color = colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        Text(
                                text = value,
                                color =
                                        if (isClickable) colorScheme.primary
                                        else colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                        )
                }
        }
}

/** Feature Item for the About dialog */
@Composable
private fun FeatureItem(text: String) {
        val colorScheme = MaterialTheme.colorScheme
        Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                        modifier =
                                Modifier.size(6.dp)
                                        .clip(CircleShape)
                                        .background(colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = text, color = colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
}

/** Language selector item with language options */
@Composable
private fun LanguageSelectorItem() {
        val currentLanguage = LanguageManager.currentLanguage
        val colorScheme = MaterialTheme.colorScheme
        val context = LocalContext.current

        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Box(
                                modifier =
                                        Modifier.size(36.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(colorScheme.outline.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                        ) {
                                Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = null,
                                        tint = colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = "Language",
                                        color = colorScheme.onSurface,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                )
                                Text(
                                        text = LanguageManager.getCurrentLanguageDisplayName(),
                                        color = colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp
                                )
                        }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Language Options Row
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                        LanguageManager.Language.entries.forEach { language ->
                                LanguageOption(
                                        language = language,
                                        isSelected = currentLanguage == language,
                                        onClick = {
                                                LanguageManager.setLanguage(context, language)
                                        },
                                        modifier = Modifier.weight(1f)
                                )
                        }
                }
        }
}

@Composable
private fun LanguageOption(
        language: LanguageManager.Language,
        isSelected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
) {
        val colorScheme = MaterialTheme.colorScheme
        val selectedColor = selectedColor()
        Box(
                modifier =
                        modifier.clip(RoundedCornerShape(12.dp))
                                .background(
                                        if (isSelected) colorScheme.primary.copy(alpha = 0.15f)
                                        else colorScheme.surfaceVariant
                                )
                                .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color =
                                                if (isSelected) selectedColor
                                                else colorScheme.outline.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onClick() }
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
        ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                                text = language.nativeName,
                                color =
                                        if (isSelected) selectedColor
                                        else colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight =
                                        if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Text(
                                text = language.displayName,
                                color = colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                        )
                }
        }
}
