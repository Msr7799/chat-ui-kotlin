package com.example.chat_ui.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat_ui.R
import com.example.chat_ui.config.ConfigManager
import com.example.chat_ui.data.GoogleGeminiConfig
import com.example.chat_ui.ui.theme.selectedColor
import kotlinx.coroutines.launch

/**
 * Google Gemini Settings Screen
 * Comprehensive configuration for Google Gemini API
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleGeminiSettingsScreen(onBackClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val selectedColor = selectedColor()

    val savedSnackbarText = stringResource(R.string.google_gemini_saved_snackbar)
    
    var config by remember { mutableStateOf(ConfigManager.getGoogleGeminiConfig()) }
    var showResetDialog by remember { mutableStateOf(false) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Save configuration
    fun saveConfig() {
        ConfigManager.saveGoogleGeminiConfig(config)
        scope.launch {
            snackbarHostState.showSnackbar(
                message = "✓ $savedSnackbarText",
                duration = SnackbarDuration.Short
            )
        }
    }
    
    // Reset Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            icon = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
            title = { Text(stringResource(R.string.google_gemini_reset_title)) },
            text = { Text(stringResource(R.string.google_gemini_reset_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        config = GoogleGeminiConfig.default()
                        ConfigManager.resetGoogleGeminiConfig()
                        showResetDialog = false
                        saveConfig()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error)
                ) {
                    Text(stringResource(R.string.google_gemini_reset_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.google_gemini_settings_screen_title),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = stringResource(R.string.google_gemini_settings_screen_subtitle),
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showResetDialog = true }) {
                        Icon(Icons.Default.RestartAlt, "Reset", tint = colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Chat Generation Settings
            SettingsSection("⚙️ ${stringResource(R.string.google_gemini_section_chat)}") {
                SliderSetting(
                    title = stringResource(R.string.google_gemini_temperature),
                    subtitle = stringResource(R.string.google_gemini_temperature_sub),
                    value = config.temperature,
                    range = 0f..2f,
                    steps = 19,
                    onValueChange = { config = config.copy(temperature = it) },
                    valueDisplay = { "%.2f".format(it) }
                )
                
                SliderSetting(
                    title = stringResource(R.string.google_gemini_max_output_tokens),
                    subtitle = stringResource(R.string.google_gemini_max_output_tokens_sub),
                    value = config.maxOutputTokens.toFloat(),
                    range = 256f..65536f,
                    steps = 0,
                    onValueChange = { config = config.copy(maxOutputTokens = it.toInt()) },
                    valueDisplay = { it.toInt().toString() }
                )
                
                SliderSetting(
                    title = stringResource(R.string.google_gemini_top_p),
                    subtitle = stringResource(R.string.google_gemini_top_p_sub),
                    value = config.topP,
                    range = 0f..1f,
                    steps = 19,
                    onValueChange = { config = config.copy(topP = it) },
                    valueDisplay = { "%.2f".format(it) }
                )
                
                SliderSetting(
                    title = stringResource(R.string.google_gemini_top_k),
                    subtitle = stringResource(R.string.google_gemini_top_k_sub),
                    value = config.topK.toFloat(),
                    range = 1f..100f,
                    steps = 0,
                    onValueChange = { config = config.copy(topK = it.toInt()) },
                    valueDisplay = { it.toInt().toString() }
                )
            }
            
            // Thinking Configuration
            SettingsSection("🧠 ${stringResource(R.string.google_gemini_section_thinking)}") {
                SwitchSetting(
                    title = stringResource(R.string.google_gemini_enable_thinking),
                    subtitle = stringResource(R.string.google_gemini_enable_thinking_sub),
                    checked = config.thinkingEnabled,
                    onCheckedChange = { config = config.copy(thinkingEnabled = it) }
                )
                
                if (config.thinkingEnabled) {
                    DropdownSetting(
                        title = stringResource(R.string.google_gemini_thinking_level),
                        subtitle = stringResource(R.string.google_gemini_thinking_level_sub),
                        options = GoogleGeminiConfig.ThinkingLevel.entries,
                        selected = config.thinkingLevel,
                        onSelect = { config = config.copy(thinkingLevel = it) },
                        displayName = { it.displayName }
                    )
                }
            }
            
            // Media Resolution
            SettingsSection("🖼️ ${stringResource(R.string.google_gemini_section_media)}") {
                DropdownSetting(
                    title = stringResource(R.string.google_gemini_media_resolution),
                    subtitle = stringResource(R.string.google_gemini_media_resolution_sub),
                    options = GoogleGeminiConfig.MediaResolution.entries,
                    selected = config.mediaResolution,
                    onSelect = { config = config.copy(mediaResolution = it) },
                    displayName = { it.displayName }
                )
            }
            
            // Tools
            SettingsSection("🔧 ${stringResource(R.string.google_gemini_section_tools)}") {
                SwitchSetting(
                    title = stringResource(R.string.google_gemini_google_search),
                    subtitle = stringResource(R.string.google_gemini_google_search_sub),
                    checked = config.googleSearchEnabled,
                    onCheckedChange = { config = config.copy(googleSearchEnabled = it) }
                )
                
                SwitchSetting(
                    title = stringResource(R.string.google_gemini_url_context),
                    subtitle = stringResource(R.string.google_gemini_url_context_sub),
                    checked = config.urlContextEnabled,
                    onCheckedChange = { config = config.copy(urlContextEnabled = it) }
                )
            }
            
            // Response Format
            SettingsSection("📄 ${stringResource(R.string.google_gemini_section_response_format)}") {
                DropdownSetting(
                    title = stringResource(R.string.google_gemini_response_type),
                    subtitle = stringResource(R.string.google_gemini_response_type_sub),
                    options = GoogleGeminiConfig.ResponseMimeType.entries,
                    selected = config.responseMimeType,
                    onSelect = { config = config.copy(responseMimeType = it) },
                    displayName = { it.displayName }
                )
            }
            
            // Safety Settings
            SettingsSection("🛡️ ${stringResource(R.string.google_gemini_section_safety)}") {
                DropdownSetting(
                    title = stringResource(R.string.google_gemini_safety_harassment),
                    subtitle = stringResource(R.string.google_gemini_safety_level_sub),
                    options = GoogleGeminiConfig.SafetyThreshold.entries,
                    selected = config.safetyHarassment,
                    onSelect = { config = config.copy(safetyHarassment = it) },
                    displayName = { it.displayName }
                )
                
                DropdownSetting(
                    title = stringResource(R.string.google_gemini_safety_hate_speech),
                    subtitle = stringResource(R.string.google_gemini_safety_level_sub),
                    options = GoogleGeminiConfig.SafetyThreshold.entries,
                    selected = config.safetyHateSpeech,
                    onSelect = { config = config.copy(safetyHateSpeech = it) },
                    displayName = { it.displayName }
                )
                
                DropdownSetting(
                    title = stringResource(R.string.google_gemini_safety_sexual),
                    subtitle = stringResource(R.string.google_gemini_safety_level_sub),
                    options = GoogleGeminiConfig.SafetyThreshold.entries,
                    selected = config.safetySexuallyExplicit,
                    onSelect = { config = config.copy(safetySexuallyExplicit = it) },
                    displayName = { it.displayName }
                )
                
                DropdownSetting(
                    title = stringResource(R.string.google_gemini_safety_dangerous),
                    subtitle = stringResource(R.string.google_gemini_safety_level_sub),
                    options = GoogleGeminiConfig.SafetyThreshold.entries,
                    selected = config.safetyDangerousContent,
                    onSelect = { config = config.copy(safetyDangerousContent = it) },
                    displayName = { it.displayName }
                )
            }
            
            // Image Generation Settings
            SettingsSection("🎨 ${stringResource(R.string.google_gemini_section_image)}") {
                SwitchSetting(
                    title = stringResource(R.string.google_gemini_enable_image_generation),
                    subtitle = stringResource(R.string.google_gemini_enable_image_generation_sub),
                    checked = config.imageResponseModalities,
                    onCheckedChange = { config = config.copy(imageResponseModalities = it) }
                )
                
                if (config.imageResponseModalities) {
                    DropdownSetting(
                        title = stringResource(R.string.google_gemini_image_aspect_ratio),
                        subtitle = stringResource(R.string.google_gemini_image_aspect_ratio_sub),
                        options = GoogleGeminiConfig.ImageAspectRatio.entries,
                        selected = config.imageAspectRatio,
                        onSelect = { config = config.copy(imageAspectRatio = it) },
                        displayName = { it.displayName }
                    )
                    
                    DropdownSetting(
                        title = stringResource(R.string.google_gemini_image_size),
                        subtitle = stringResource(R.string.google_gemini_image_size_sub),
                        options = GoogleGeminiConfig.ImageSize.entries,
                        selected = config.imageSize,
                        onSelect = { config = config.copy(imageSize = it) },
                        displayName = { it.displayName }
                    )
                }
            }
            
            // Save Button
            Button(
                onClick = { saveConfig() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = selectedColor
                )
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.google_gemini_save_button), fontSize = 16.sp)
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = colorScheme.surfaceColorAtElevation(2.dp)
    
    Column {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurface,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(containerColor)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SwitchSetting(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val accentColor = selectedColor()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariant
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colorScheme.surface,
                checkedTrackColor = accentColor,
                uncheckedThumbColor = colorScheme.onSurfaceVariant,
                uncheckedTrackColor = colorScheme.onSurface.copy(alpha = 0.24f),
                uncheckedBorderColor = colorScheme.outline.copy(alpha = 0.65f)
            )
        )
    }
}

@Composable
private fun SliderSetting(
    title: String,
    subtitle: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    valueDisplay: (Float) -> String
) {
    val colorScheme = MaterialTheme.colorScheme
    val accentColor = selectedColor()
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                text = valueDisplay(value),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = accentColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.18f))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = colorScheme.onSurface.copy(alpha = 0.28f),
                activeTickColor = colorScheme.surface,
                inactiveTickColor = colorScheme.onSurface.copy(alpha = 0.18f)
            )
        )
    }
}

@Composable
private fun <T> DropdownSetting(
    title: String,
    subtitle: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    displayName: (T) -> String
) {
    val colorScheme = MaterialTheme.colorScheme
    val accentColor = selectedColor()
    var expanded by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurface
        )
        Text(
            text = subtitle,
            fontSize = 12.sp,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = colorScheme.surface
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = SolidColor(colorScheme.outline.copy(alpha = 0.7f))
                )
            ) {
                Text(
                    text = displayName(selected),
                    modifier = Modifier.weight(1f),
                    fontSize = 14.sp,
                    color = colorScheme.onSurface
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant
                )
            }
            
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = displayName(option),
                                fontSize = 14.sp,
                                fontWeight = if (option == selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (option == selected) accentColor else colorScheme.onSurface
                            )
                        },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
