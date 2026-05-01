package com.example.chat_ui.ui.screens

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.example.chat_ui.R
import com.example.chat_ui.data.models.GeneratedImage
import com.example.chat_ui.ui.theme.ThemeManager
import com.example.chat_ui.utils.CloudinaryUrlUtils
import com.example.chat_ui.viewmodel.ImageGalleryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    val availableModels = remember(images) {
        images.map { it.modelUsed }.filter { it.isNotBlank() }.distinct()
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { snackbarHostState.showSnackbar(it) }
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
                        selectedModelFilter?.let {
                            Text(
                                text = "Filter: $it",
                                fontSize = 12.sp,
                                color = themeColors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
                    IconButton(onClick = { showFilterMenu = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter", tint = themeColors.textPrimary)
                    }
                    IconButton(onClick = { viewModel.refreshImages() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = themeColors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = themeColors.surface)
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToImageGeneration?.invoke("google/gemini-2.5-flash-image") },
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
                isLoading && images.isEmpty() -> GalleryLoading(themeColors)
                images.isEmpty() -> EmptyStateView(themeColors)
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 156.dp),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = images,
                            key = { it.id.ifBlank { it.cloudinaryUrl } }
                        ) { image ->
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

            if (errorMessage != null) {
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .navigationBarsPadding(),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) { Text("OK") }
                    }
                ) { Text(errorMessage.orEmpty()) }
            }
        }
    }

    selectedImage?.let { image ->
        FullScreenImageDialog(
            image = image,
            onDismiss = { selectedImage = null },
            onCopyPrompt = {
                scope.launch { snackbarHostState.showSnackbar("Prompt copied") }
            },
            onSaved = {
                scope.launch { snackbarHostState.showSnackbar("Saved to gallery") }
            },
            onShare = {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, it.cloudinaryUrl)
                    putExtra(Intent.EXTRA_SUBJECT, "Generated Image: ${it.prompt}")
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

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Image?") },
            text = { Text("This will delete the image metadata from Firebase. Cloudinary deletion requires a backend-signed call in production.") },
            confirmButton = {
                Button(
                    onClick = {
                        imageToDelete?.let { viewModel.deleteImage(it) }
                        showDeleteConfirmation = false
                        imageToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            }
        )
    }

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
                leadingIcon = { if (selectedModelFilter == null) Icon(Icons.Default.Check, contentDescription = null) }
            )
            availableModels.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        selectedModelFilter = model
                        viewModel.setModelFilter(model)
                        showFilterMenu = false
                    },
                    leadingIcon = { if (selectedModelFilter == model) Icon(Icons.Default.Check, contentDescription = null) }
                )
            }
        }
    }
}

@Composable
private fun GalleryLoading(themeColors: com.example.chat_ui.ui.theme.ThemeColors) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(color = themeColors.primary)
            Text(text = "Loading images...", color = themeColors.textSecondary)
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
    val context = LocalContext.current
    val thumbnailUrl = remember(image.cloudinaryUrl) { CloudinaryUrlUtils.galleryThumbnailUrl(image.cloudinaryUrl) }
    val request = remember(thumbnailUrl) {
        ImageRequest.Builder(context)
            .data(thumbnailUrl)
            .size(360, 360)
            .precision(Precision.INEXACT)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .allowHardware(true)
            .allowRgb565(true)
            .crossfade(false)
            .build()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .combinedClickable(
                onClick = { onImageClick(image) },
                onLongClick = { onDeleteClick(image) }
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = request,
                contentDescription = image.prompt,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(74.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                        )
                    )
            )

            Text(
                text = image.prompt.ifBlank { "Generated image" },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .fillMaxWidth(),
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            IconButton(
                onClick = { onDeleteClick(image) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(18.dp))
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
    val previewUrl = remember(image.cloudinaryUrl) { CloudinaryUrlUtils.previewUrl(image.cloudinaryUrl) }
    val request = remember(previewUrl) {
        ImageRequest.Builder(context)
            .data(previewUrl)
            .size(1200, 1200)
            .precision(Precision.INEXACT)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .allowHardware(true)
            .allowRgb565(true)
            .crossfade(false)
            .build()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(image.prompt))
                            onCopyPrompt()
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy prompt", tint = Color.White)
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                val result = copyRemoteImageToGallery(
                                    context = context,
                                    imageUrl = image.cloudinaryUrl,
                                    displayName = "generated_${image.id}.png"
                                )
                                result.fold(
                                    onSuccess = { onSaved() },
                                    onFailure = {
                                        Toast.makeText(context, it.message ?: "Failed to save", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    ) {
                        Icon(Icons.Default.DownloadForOffline, contentDescription = "Download", tint = Color.White)
                    }
                }
            }

            AsyncImage(
                model = request,
                contentDescription = image.prompt,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(horizontal = 12.dp)
                    .heightIn(max = 540.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Fit
            )

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
                        text = image.prompt.ifBlank { "Generated image" },
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Model: ${image.modelUsed.ifBlank { "Unknown" }}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Size: ${image.width ?: "?"} × ${image.height ?: "?"}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                    if (image.createdAt > 0L) {
                        Text(
                            text = "Created: ${dateFormat.format(Date(image.createdAt))}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { onShare(image) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share")
                        }
                        Button(
                            onClick = { onDelete(image) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
}

private suspend fun copyRemoteImageToGallery(
    context: android.content.Context,
    imageUrl: String,
    displayName: String
): Result<Uri> = withContext(Dispatchers.IO) {
    try {
        val connection = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            return@withContext Result.failure(IOException("HTTP $responseCode"))
        }

        val mimeType = connection.contentType?.substringBefore(';')?.trim().takeUnless { it.isNullOrBlank() }
            ?: "image/png"

        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ChatUI")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return@withContext Result.failure(FileNotFoundException("Failed to create MediaStore record"))

        try {
            resolver.openOutputStream(uri)?.use { output ->
                connection.inputStream.use { input -> input.copyTo(output, bufferSize = 16 * 1024) }
            } ?: return@withContext Result.failure(FileNotFoundException("Failed to open output stream"))

            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
            Result.success(uri)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            Result.failure(e)
        } finally {
            connection.disconnect()
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

@Composable
private fun EmptyStateView(themeColors: com.example.chat_ui.ui.theme.ThemeColors) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                textAlign = TextAlign.Center
            )
        }
    }
}
