package com.example.chat_ui.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.chat_ui.ui.theme.*

/**
 * File attachment data class
 */
data class AttachedFile(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val size: Long = 0
)

/**
 * File attachment picker and preview
 * Similar to UploadedFile.svelte in the Svelte app
 */
@Composable
fun FileAttachmentPicker(
    attachedFiles: List<AttachedFile>,
    onFilesSelected: (List<AttachedFile>) -> Unit,
    onFileRemove: (AttachedFile) -> Unit,
    isMultimodal: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    
    // Image picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        val files = uris.mapNotNull { uri ->
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                cursor.moveToFirst()
                AttachedFile(
                    uri = uri,
                    name = cursor.getString(nameIndex),
                    mimeType = context.contentResolver.getType(uri) ?: "image/*",
                    size = cursor.getLong(sizeIndex)
                )
            }
        }
        onFilesSelected(attachedFiles + files)
    }
    
    // Document picker
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        val files = uris.mapNotNull { uri ->
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                cursor.moveToFirst()
                AttachedFile(
                    uri = uri,
                    name = cursor.getString(nameIndex),
                    mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream",
                    size = cursor.getLong(sizeIndex)
                )
            }
        }
        onFilesSelected(attachedFiles + files)
    }
    
    Column(modifier = modifier) {
        // Attached files preview
        AnimatedVisibility(
            visible = attachedFiles.isNotEmpty(),
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(attachedFiles) { file ->
                    AttachedFilePreview(
                        file = file,
                        onRemove = { onFileRemove(file) }
                    )
                }
            }
        }
        
        // Attachment button with menu
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceVariant)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add attachment",
                    tint = TextSecondary
                )
            }
            
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(DarkSurface)
            ) {
                if (isMultimodal) {
                    DropdownMenuItem(
                        text = { Text("Image", color = TextPrimary) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = null,
                                tint = PrimaryBlue
                            )
                        },
                        onClick = {
                            showMenu = false
                            imagePickerLauncher.launch("image/*")
                        }
                    )
                }
                
                DropdownMenuItem(
                    text = { Text("Document", color = TextPrimary) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = AccentOrange
                        )
                    },
                    onClick = {
                        showMenu = false
                        documentPickerLauncher.launch(arrayOf("*/*"))
                    }
                )
                
                DropdownMenuItem(
                    text = { Text("Link", color = TextPrimary) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Link,
                            contentDescription = null,
                            tint = AccentGreen
                        )
                    },
                    onClick = {
                        showMenu = false
                        // TODO: URL input dialog
                    }
                )
            }
        }
    }
}

@Composable
private fun AttachedFilePreview(
    file: AttachedFile,
    onRemove: () -> Unit
) {
    val isImage = file.mimeType.startsWith("image/")
    
    Box(
        modifier = Modifier
            .size(if (isImage) 80.dp else 120.dp, 80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceVariant)
    ) {
        if (isImage) {
            // Image preview
            AsyncImage(
                model = file.uri,
                contentDescription = file.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Document preview
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when {
                        file.mimeType.contains("pdf") -> Icons.Default.PictureAsPdf
                        file.mimeType.contains("text") -> Icons.Default.Description
                        else -> Icons.Default.Attachment
                    },
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatFileSize(file.size),
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }
        
        // Remove button
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f))
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

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}
