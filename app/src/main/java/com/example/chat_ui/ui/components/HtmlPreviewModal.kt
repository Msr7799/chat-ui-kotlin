package com.example.chat_ui.ui.components

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.chat_ui.ui.theme.ThemeManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * HtmlPreviewModal - Modal for previewing HTML content
 * 
 * Similar to: HtmlPreviewModal.svelte in chat-ui
 * 
 * Features:
 * - WebView for rendering HTML
 * - Copy HTML source button
 * - Open in browser button
 * - Fullscreen toggle
 */

@Composable
fun HtmlPreviewModal(
    isOpen: Boolean,
    htmlContent: String,
    title: String = "HTML Preview",
    onClose: () -> Unit
) {
    if (!isOpen) return
    
    val context = LocalContext.current
    val themeColors = ThemeManager.getThemeColors(
        ThemeManager.currentPreference,
        isSystemInDarkTheme()
    )
    
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(16.dp))
                .background(themeColors.surface)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(themeColors.surfaceVariant)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = themeColors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Copy HTML button
                    IconButton(
                        onClick = {
                            copyToClipboard(context, htmlContent)
                            Toast.makeText(context, "HTML copied!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy HTML",
                            tint = themeColors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    // Open in browser button
                    IconButton(
                        onClick = {
                            openHtmlInBrowser(context, htmlContent)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = "Open in browser",
                            tint = themeColors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    // Close button
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = themeColors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            
            // WebView for HTML rendering
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewClient = WebViewClient()
                            settings.apply {
                                javaScriptEnabled = true
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                builtInZoomControls = true
                                displayZoomControls = false
                            }
                        }
                    },
                    update = { webView ->
                        // Wrap content in basic HTML structure if needed
                        val fullHtml = if (htmlContent.trim().startsWith("<!DOCTYPE") || 
                                          htmlContent.trim().startsWith("<html")) {
                            htmlContent
                        } else {
                            """
                            <!DOCTYPE html>
                            <html>
                            <head>
                                <meta charset="UTF-8">
                                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                <style>
                                    body { 
                                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                                        padding: 16px;
                                        line-height: 1.6;
                                    }
                                    pre { 
                                        background: #f4f4f4; 
                                        padding: 12px; 
                                        border-radius: 8px;
                                        overflow-x: auto;
                                    }
                                    code { 
                                        background: #f4f4f4; 
                                        padding: 2px 6px; 
                                        border-radius: 4px;
                                    }
                                    table {
                                        border-collapse: collapse;
                                        width: 100%;
                                    }
                                    th, td {
                                        border: 1px solid #ddd;
                                        padding: 8px;
                                        text-align: left;
                                    }
                                    th { background: #f4f4f4; }
                                    img { max-width: 100%; height: auto; }
                                </style>
                            </head>
                            <body>
                                $htmlContent
                            </body>
                            </html>
                            """.trimIndent()
                        }
                        webView.loadDataWithBaseURL(null, fullHtml, "text/html", "UTF-8", null)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("HTML", text)
    clipboard.setPrimaryClip(clip)
}

private fun openHtmlInBrowser(context: Context, htmlContent: String) {
    try {
        // Create a data URI for the HTML content
        val dataUri = "data:text/html;charset=utf-8," + Uri.encode(htmlContent)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(dataUri))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot open in browser", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Detect if content contains HTML that should be previewed
 */
fun containsPreviewableHtml(content: String): Boolean {
    val htmlPatterns = listOf(
        "<table", "<div", "<form", "<iframe", 
        "<canvas", "<svg", "<style", "<!DOCTYPE"
    )
    return htmlPatterns.any { content.contains(it, ignoreCase = true) }
}

/**
 * Extract HTML blocks from markdown content
 */
fun extractHtmlBlocks(content: String): List<String> {
    val blocks = mutableListOf<String>()
    
    // Pattern for ```html code blocks
    val htmlCodeBlockPattern = Regex("```html\\s*\\n([\\s\\S]*?)\\n```", RegexOption.IGNORE_CASE)
    htmlCodeBlockPattern.findAll(content).forEach { match ->
        blocks.add(match.groupValues[1].trim())
    }
    
    // Pattern for inline HTML (tables, divs, etc.)
    val inlineHtmlPattern = Regex("<(table|div|form)[^>]*>[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE)
    inlineHtmlPattern.findAll(content).forEach { match ->
        blocks.add(match.value)
    }
    
    return blocks.distinct()
}
