package com.example.chat_ui.ui.components

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.chat_ui.data.MessageFile
import com.example.chat_ui.ui.theme.ThemeColors
import com.example.chat_ui.ui.theme.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import android.util.Base64

/**
 * UrlFetchModal - Modal for adding files from URL
 * 
 * Similar to: UrlFetchModal.svelte in chat-ui
 * 
 * Features:
 * - HTTPS URL validation
 * - File download with size limit (10MB)
 * - MIME type detection
 * - Progress indicator
 */

private const val TAG = "UrlFetchModal"
private const val MAX_FILE_SIZE = 10 * 1024 * 1024 // 10MB

@Composable
fun UrlFetchModal(
    isOpen: Boolean,
    onClose: () -> Unit,
    onFileAdded: (MessageFile) -> Unit,
    onError: (String) -> Unit
) {
    if (!isOpen) return
    
    val themeColors = ThemeManager.getThemeColors(
        ThemeManager.currentPreference,
        isSystemInDarkTheme()
    )
    
    var urlValue by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    
    // Reset state when opened
    LaunchedEffect(isOpen) {
        if (isOpen) {
            urlValue = ""
            errorMsg = null
        }
    }
    
    fun isValidHttpsUrl(url: String): Boolean {
        return try {
            val u = URL(url.trim())
            u.protocol == "https"
        } catch (e: Exception) {
            false
        }
    }
    
    fun handleSubmit() {
        errorMsg = null
        val trimmed = urlValue.trim()
        
        if (!isValidHttpsUrl(trimmed)) {
            errorMsg = "Enter a valid HTTPS URL."
            return
        }
        
        isLoading = true
        scope.launch {
            try {
                val result = fetchFileFromUrl(trimmed)
                onFileAdded(result)
                onClose()
            } catch (e: Exception) {
                Log.e(TAG, "Fetch error: ${e.message}", e)
                errorMsg = e.message ?: "Failed to fetch URL"
                onError(errorMsg!!)
            } finally {
                isLoading = false
            }
        }
    }
    
    Dialog(
        onDismissRequest = { if (!isLoading) onClose() },
        properties = DialogProperties(
            dismissOnBackPress = !isLoading,
            dismissOnClickOutside = !isLoading
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(themeColors.surface)
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Add from URL",
                    color = themeColors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(
                    onClick = onClose,
                    enabled = !isLoading,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = themeColors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // URL Input
            OutlinedTextField(
                value = urlValue,
                onValueChange = { urlValue = it },
                label = { Text("Enter URL") },
                placeholder = { Text("https://example.com/file.txt") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        tint = themeColors.textMuted
                    )
                },
                enabled = !isLoading,
                isError = errorMsg != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(
                    onGo = {
                        focusManager.clearFocus()
                        handleSubmit()
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = themeColors.primary,
                    unfocusedBorderColor = themeColors.border,
                    focusedLabelColor = themeColors.primary,
                    unfocusedLabelColor = themeColors.textMuted,
                    cursorColor = themeColors.primary
                ),
                modifier = Modifier.fillMaxWidth()
            )
            
            // Error message
            AnimatedVisibility(visible = errorMsg != null) {
                Text(
                    text = errorMsg ?: "",
                    color = Color(0xFFEF4444),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            // Help text
            Text(
                text = "Only HTTPS. Max 10MB.",
                color = themeColors.textMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cancel button
                TextButton(
                    onClick = onClose,
                    enabled = !isLoading
                ) {
                    Text(
                        text = "Cancel",
                        color = themeColors.textSecondary
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Add button
                Button(
                    onClick = { handleSubmit() },
                    enabled = !isLoading && urlValue.trim().isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = themeColors.primary
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Fetching...")
                    } else {
                        Text("Add")
                    }
                }
            }
        }
    }
}

/**
 * Fetch file from URL and convert to MessageFile
 */
private suspend fun fetchFileFromUrl(urlString: String): MessageFile = withContext(Dispatchers.IO) {
    val url = URL(urlString)
    val connection = url.openConnection() as HttpURLConnection
    
    try {
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty("User-Agent", "ChatUI-Android/1.0")
        
        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw Exception("Failed to fetch (HTTP $responseCode)")
        }
        
        // Check content length
        val contentLength = connection.contentLength
        if (contentLength > MAX_FILE_SIZE) {
            throw Exception("File too large (max 10MB)")
        }
        
        // Get content type
        val contentType = connection.contentType?.split(";")?.firstOrNull()?.trim() 
            ?: "application/octet-stream"
        
        // Get filename from URL or Content-Disposition
        val filename = extractFilename(connection, urlString)
        
        // Read content
        val inputStream = connection.inputStream
        val bytes = inputStream.readBytes()
        
        if (bytes.size > MAX_FILE_SIZE) {
            throw Exception("File too large (max 10MB)")
        }
        
        // Convert to base64
        val base64Content = Base64.encodeToString(bytes, Base64.NO_WRAP)
        
        MessageFile(
            type = MessageFile.FileDataType.BASE64,
            name = filename,
            mime = contentType,
            value = base64Content
        )
    } finally {
        connection.disconnect()
    }
}

/**
 * Extract filename from Content-Disposition header or URL
 */
private fun extractFilename(connection: HttpURLConnection, urlString: String): String {
    // Try Content-Disposition header
    val disposition = connection.getHeaderField("Content-Disposition")
    if (disposition != null) {
        // Try filename*= (RFC 5987)
        val filenameStar = Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE)
            .find(disposition)?.groupValues?.getOrNull(1)
        if (filenameStar != null) {
            return try {
                java.net.URLDecoder.decode(filenameStar.trim().replace("['\"']".toRegex(), ""), "UTF-8")
            } catch (e: Exception) {
                filenameStar
            }
        }
        
        // Try filename=
        val filenameMatch = Regex("filename=\"?([^\";]+)\"?", RegexOption.IGNORE_CASE)
            .find(disposition)?.groupValues?.getOrNull(1)
        if (filenameMatch != null) {
            return filenameMatch.trim()
        }
    }
    
    // Fall back to URL path
    return try {
        val url = URL(urlString)
        val pathParts = url.path.split("/")
        val last = pathParts.lastOrNull { it.isNotBlank() } ?: "attachment"
        java.net.URLDecoder.decode(last, "UTF-8")
    } catch (e: Exception) {
        "attachment"
    }
}
