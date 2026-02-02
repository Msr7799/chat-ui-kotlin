package com.example.chat_ui.ui.screens

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.chat_ui.R
import com.example.chat_ui.data.models.GeneratedImage
import com.example.chat_ui.ui.theme.ThemeManager
import com.example.chat_ui.viewmodel.ImageGalleryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

/**
 * Image Gallery Screen - Display all generated images
 * 
 * Features:
 * - Grid layout with 2 columns
 * - Full-screen image preview
 * - Delete with confirmation
 * - Share functionality
 * - Filter by model
 * - Pull-to-refresh
 * - Empty state
 * - Loading state
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageGalleryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToImageGeneration: ((String) -> Unit)? = null
) {
    val viewModel: ImageGalleryViewModel = viewModel()
    val images by viewModel.images.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    
    val context = LocalContext.current
    val themeColors = ThemeManager.getThemeColors(
        ThemeManager.currentPreference,
        androidx.compose.foundation.isSystemInDarkTheme()
    )
    
    var selectedImage by remember { mutableStateOf<GeneratedImage?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var imageToDelete by remember { mutableStateOf<GeneratedImage?>(null) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var selectedModelFilter by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // Available models for filtering
    val availableModels = remember(images) {
        images.map { it.modelUsed }.distinct()
    }
    
    // Show error snackbar
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            // Error will be shown in UI
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Image Gallery",
                            color = themeColors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        if (selectedModelFilter != null) {
                            Text(
                                text = "Filter: $selectedModelFilter",
                                fontSize = 12.sp,
                                color = themeColors.textSecondary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = themeColors.textPrimary
                        )
                    }
                },
                actions = {
                    // Filter button
                    IconButton(onClick = { showFilterMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter",
                            tint = themeColors.textPrimary
                        )
                    }
                    
                    // Refresh button
                    IconButton(onClick = { viewModel.refreshImages() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = themeColors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = themeColors.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // Navigate to Image Generation Screen with default model
                    onNavigateToImageGeneration?.invoke("google/gemini-2.5-flash-image")
                },
                containerColor = themeColors.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.generate_image),
                    tint = Color.White
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(themeColors.background)
                .padding(padding)
        ) {
            when {
                isLoading && images.isEmpty() -> {
                    // Initial loading
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(color = themeColors.primary)
                            Text(
                                text = "Loading images...",
                                color = themeColors.textSecondary
                            )
                        }
                    }
                }
                
                images.isEmpty() -> {
                    // Empty state
                    EmptyStateView(themeColors)
                }
                
                else -> {
                    // Image grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(images, key = { it.id }) { image ->
                            ImageGridItem(
                                image = image,
                                onImageClick = { selectedImage = it },
                                onDeleteClick = {
                                    imageToDelete = it
                                    showDeleteConfirmation = true
                                }
                            )
                        }
                    }
                }
            }
            
            // Error message
            if (errorMessage != null) {
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .navigationBarsPadding(),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("OK")
                        }
                    }
                ) {
                    Text(errorMessage ?: "")
                }
            }
        }
    }
    
    // Full-screen image dialog
    if (selectedImage != null) {
        FullScreenImageDialog(
            image = selectedImage!!,
            onDismiss = { selectedImage = null },
            onCopyPrompt = {
                scope.launch {
                    snackbarHostState.showSnackbar("Prompt copied")
                }
            },
            onSaved = {
                scope.launch {
                    snackbarHostState.showSnackbar("Saved to gallery")
                }
            },
            onShare = { image ->
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, image.cloudinaryUrl)
                    putExtra(Intent.EXTRA_SUBJECT, "Generated Image: ${image.prompt}")
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Image"))
            },
            onDelete = {
                imageToDelete = it
                showDeleteConfirmation = true
                selectedImage = null
            }
        )
    }
    
    // Delete confirmation dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Image?") },
            text = { Text("This action cannot be undone. The image will be permanently deleted.") },
            confirmButton = {
                Button(
                    onClick = {
                        imageToDelete?.let { viewModel.deleteImage(it) }
                        showDeleteConfirmation = false
                        imageToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Filter menu
    if (showFilterMenu) {
        DropdownMenu(
            expanded = showFilterMenu,
            onDismissRequest = { showFilterMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("All Models") },
                onClick = {
                    selectedModelFilter = null
                    viewModel.setModelFilter(null)
                    showFilterMenu = false
                },
                leadingIcon = {
                    if (selectedModelFilter == null) {
                        Icon(Icons.Default.Check, contentDescription = null)
                    }
                }
            )
            
            availableModels.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model) },
                    onClick = {
                        selectedModelFilter = model
                        viewModel.setModelFilter(model)
                        showFilterMenu = false
                    },
                    leadingIcon = {
                        if (selectedModelFilter == model) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageGridItem(
    image: GeneratedImage,
    onImageClick: (GeneratedImage) -> Unit,
    onDeleteClick: (GeneratedImage) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .combinedClickable(
                onClick = { onImageClick(image) },
                onLongClick = { onDeleteClick(image) }
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Image
            AsyncImage(
                model = image.cloudinaryUrl,
                contentDescription = image.prompt,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // Gradient overlay for text
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
            )
            
            // Prompt text
            Text(
                text = image.prompt,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .fillMaxWidth(),
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            // Delete button
            IconButton(
                onClick = { onDeleteClick(image) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp)
                    .background(
                        Color.Black.copy(alpha = 0.5f),
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun FullScreenImageDialog(
    image: GeneratedImage,
    onDismiss: () -> Unit,
    onCopyPrompt: () -> Unit,
    onSaved: () -> Unit,
    onShare: (GeneratedImage) -> Unit,
    onDelete: (GeneratedImage) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
        ) {
            // Top bar (close + actions)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(image.prompt))
                            onCopyPrompt()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy prompt",
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = {
                            scope.launch {
                                val result = saveRemoteImageToGallery(
                                    context = context,
                                    imageUrl = image.cloudinaryUrl,
                                    displayName = "generated_${image.id}.jpg"
                                )
                                result.fold(
                                    onSuccess = {
                                        onSaved()
                                    },
                                    onFailure = {
                                        Toast.makeText(
                                            context,
                                            it.message ?: "Failed to save",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.DownloadForOffline,
                            contentDescription = "Download",
                            tint = Color.White
                        )
                    }
                }
            }

            // Image
            AsyncImage(
                model = image.cloudinaryUrl,
                contentDescription = image.prompt,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(horizontal = 12.dp)
                    .heightIn(max = 520.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Fit
            )

            // Bottom sheet info + actions
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = image.prompt,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Model: ${image.modelUsed}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Size: ${image.width} × ${image.height}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                    Text(
                        text = "Created: ${dateFormat.format(Date(image.createdAt))}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onShare(image) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share")
                        }

                        Button(
                            onClick = { onDelete(image) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
}

private suspend fun saveRemoteImageToGallery(
    context: android.content.Context,
    imageUrl: String,
    displayName: String
): Result<Uri> = withContext(Dispatchers.IO) {
    try {
        val url = URL(imageUrl)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
        }

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            return@withContext Result.failure(IOException("HTTP $responseCode"))
        }

        val bitmap: Bitmap = connection.inputStream.use { input ->
            BitmapFactory.decodeStream(input)
                ?: return@withContext Result.failure(IOException("Failed to decode image"))
        }

        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ChatUI")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return@withContext Result.failure(FileNotFoundException("Failed to create MediaStore record"))

        try {
            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) {
                    return@withContext Result.failure(IOException("Failed to write image"))
                }
            } ?: return@withContext Result.failure(FileNotFoundException("Failed to open output stream"))

            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)

            Result.success(uri)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            Result.failure(e)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

@Composable
private fun EmptyStateView(themeColors: com.example.chat_ui.ui.theme.ThemeColors) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = themeColors.textMuted.copy(alpha = 0.5f)
            )
            Text(
                text = "No Images Yet",
                style = MaterialTheme.typography.titleLarge,
                color = themeColors.textPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Generate your first AI image to see it here!",
                style = MaterialTheme.typography.bodyMedium,
                color = themeColors.textSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
