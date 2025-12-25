package com.example.chat_ui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.chat_ui.ui.theme.ThemeColors
import com.example.chat_ui.ui.theme.ThemeManager
import androidx.compose.foundation.isSystemInDarkTheme

/**
 * Dialog for generating images in chat
 */
@Composable
fun ImageGenerationDialog(
    onDismiss: () -> Unit,
    onGenerate: (String) -> Unit,
    isGenerating: Boolean = false
) {
    var prompt by remember { mutableStateOf("") }
    val themeColors: ThemeColors = ThemeManager.getThemeColors(
        ThemeManager.currentPreference,
        isSystemInDarkTheme()
    )
    
    Dialog(
        onDismissRequest = { if (!isGenerating) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = themeColors.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = themeColors.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Generate Image",
                            style = MaterialTheme.typography.titleLarge,
                            color = themeColors.textPrimary
                        )
                    }
                    
                    if (!isGenerating) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = themeColors.textMuted
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Prompt Input
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    label = { Text("Image description") },
                    placeholder = { Text("e.g., A beautiful sunset over mountains...") },
                    enabled = !isGenerating,
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = themeColors.primary,
                        unfocusedBorderColor = themeColors.textMuted,
                        focusedLabelColor = themeColors.primary,
                        unfocusedLabelColor = themeColors.textMuted,
                        focusedTextColor = themeColors.textPrimary,
                        unfocusedTextColor = themeColors.textPrimary
                    )
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isGenerating) {
                        TextButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = themeColors.textMuted
                            )
                        ) {
                            Text("Cancel")
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = {
                            if (prompt.isNotBlank()) {
                                onGenerate(prompt)
                            }
                        },
                        enabled = prompt.isNotBlank() && !isGenerating,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = themeColors.primary,
                            disabledContainerColor = themeColors.surfaceVariant
                        )
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = themeColors.textPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generating...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate")
                        }
                    }
                }
            }
        }
    }
}
