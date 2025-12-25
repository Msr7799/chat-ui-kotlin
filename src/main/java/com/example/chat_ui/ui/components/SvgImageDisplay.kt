package com.example.chat_ui.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.caverock.androidsvg.SVG

/**
 * SVG Image Display Component
 * Renders SVG code as an image with a "Copy Code" button
 */
@Composable
fun SvgImageDisplay(
    svgCode: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E1E2E))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // SVG Image rendering
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 150.dp, max = 400.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            SvgImage(
                svgContent = svgCode,
                onError = { errorMessage = it }
            )
        }
        
        // Error message if any
        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = Color(0xFFEF4444),
                fontSize = 11.sp
            )
        }
        
        // Copy Code Button
        Button(
            onClick = {
                copyToClipboard(context, svgCode)
                copied = true
                Toast.makeText(context, "تم نسخ الكود", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (copied) Color(0xFF10B981) else Color(0xFF3B82F6)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = "Copy",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (copied) "تم النسخ ✓" else "انسخ الكود",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
        
        // Reset copied state after 2 seconds
        LaunchedEffect(copied) {
            if (copied) {
                kotlinx.coroutines.delay(2000)
                copied = false
            }
        }
    }
}

/**
 * Renders SVG content using AndroidSVG library
 */
@Composable
private fun SvgImage(
    svgContent: String,
    onError: (String) -> Unit
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    LaunchedEffect(svgContent) {
        try {
            val svg = SVG.getFromString(svgContent)
            
            // Get SVG dimensions or use defaults
            val width = if (svg.documentWidth > 0) svg.documentWidth.toInt() else 800
            val height = if (svg.documentHeight > 0) svg.documentHeight.toInt() else 600
            
            // Create bitmap
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            
            // Draw SVG on canvas
            svg.renderToCanvas(canvas)
            
            bitmap = bmp
        } catch (e: Exception) {
            onError("فشل تحليل SVG: ${e.message}")
        }
    }
    
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = "SVG Image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("SVG Code", text)
    clipboard.setPrimaryClip(clip)
}
