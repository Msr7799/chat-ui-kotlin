package com.example.chat_ui.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.chat_ui.R
import com.example.chat_ui.ui.components.DotsLoader
import com.example.chat_ui.api.ImageGenerationClient
import com.example.chat_ui.data.firebase.FirebaseManager
import com.example.chat_ui.data.firebase.FirestoreManager
import com.example.chat_ui.data.models.GeneratedImage
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Gallery Screen - Displays generated images Similar to src/routes/gallery/+page.svelte */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
        onNavigateBack: () -> Unit,
        @Suppress("UNUSED_PARAMETER") onGenerateNew: () -> Unit,
        onNavigateToImageGen: ((String) -> Unit)? = null
) {
    @Suppress("UNUSED_VARIABLE") val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var images by remember { mutableStateOf<List<GeneratedImage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedImage by remember { mutableStateOf<GeneratedImage?>(null) }
    var showGenerateDialog by remember { mutableStateOf(false) }
    var deletingId by remember { mutableStateOf<String?>(null) }

    // Load images from Firebase
    LaunchedEffect(Unit) {
        if (!FirebaseManager.isInitialized()) {
            error = "Firebase not initialized"
            isLoading = false
            return@LaunchedEffect
        }
        try {
            FirestoreManager.getGeneratedImagesFlow().collect { imageList ->
                images = imageList
                isLoading = false
            }
        } catch (e: Exception) {
            error = e.message ?: "Failed to load images"
            isLoading = false
        }
    }

    Scaffold(
            topBar = {
                TopAppBar(
                        title = {
                            Column {
                                Text(stringResource(R.string.image_gallery))
                                Text(
                                        stringResource(R.string.your_ai_images),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                )
                            }
                        },
                        actions = {
                            Button(
                                    onClick = { 
                                        // Navigate to ImageGenerationScreen
                                        onNavigateToImageGen?.invoke("google/gemini-2.5-flash-image")
                                    },
                                    colors =
                                            ButtonDefaults.buttonColors(
                                                    containerColor =
                                                            MaterialTheme.colorScheme.primary
                                            )
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.generate))
                            }
                        }
                )
            }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> {
                    DotsLoader(
                        modifier = Modifier.align(Alignment.Center),
                        dotColor = MaterialTheme.colorScheme.primary
                    )
                }
                error != null -> {
                    Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                                Icons.Default.Warning,
                                null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Error: $error", color = MaterialTheme.colorScheme.error)
                    }
                }
                images.isEmpty() -> {
                    // Empty state
                    Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                                Icons.Default.Image,
                                null,
                                tint =
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = 0.5f
                                        ),
                                modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                                stringResource(R.string.no_images_yet),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                                stringResource(R.string.click_generate_new),
                                style = MaterialTheme.typography.bodyMedium,
                                color =
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = 0.7f
                                        )
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { showGenerateDialog = true }) {
                            Icon(Icons.Default.AutoAwesome, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.generate_new_image))
                        }
                    }
                }
                else -> {
                    // Image grid
                    LazyVerticalGrid(
                            columns = GridCells.Adaptive(150.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(images, key = { it.id }) { image ->
                            ImageCard(
                                    image = image,
                                    onClick = { selectedImage = image },
                                    onDelete = {
                                        scope.launch {
                                            deletingId = image.id
                                            ImageGenerationClient.deleteImage(image.id)
                                            deletingId = null
                                        }
                                    },
                                    isDeleting = deletingId == image.id
                            )
                        }
                    }
                }
            }
        }
    }

    // Fullscreen Image Dialog
    selectedImage?.let { image ->
        ImageFullscreenDialog(image = image, onDismiss = { selectedImage = null })
    }

    // Generate Dialog
    if (showGenerateDialog) {
        ImageGenerationDialog(
                onDismiss = { showGenerateDialog = false },
                onGenerated = { showGenerateDialog = false }
        )
    }
}

@Composable
private fun ImageCard(
        image: GeneratedImage,
        onClick: () -> Unit,
        onDelete: () -> Unit,
        isDeleting: Boolean
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }

    Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            // Image
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                AsyncImage(
                        model =
                                ImageRequest.Builder(LocalContext.current)
                                        .data(image.cloudinaryUrl)
                                        .crossfade(true)
                                        .build(),
                        contentDescription = image.prompt,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                )

                // Delete button overlay
                IconButton(
                        onClick = onDelete,
                        enabled = !isDeleting,
                        modifier =
                                Modifier.align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .background(
                                                Color.Black.copy(alpha = 0.5f),
                                                RoundedCornerShape(8.dp)
                                        )
                                        .size(32.dp)
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                        )
                    } else {
                        Icon(
                                Icons.Default.Delete,
                                "Delete",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Model badge
                Surface(
                        modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                            text = image.modelUsed.split("/").lastOrNull() ?: "FLUX",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Info
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                        text = image.prompt,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                        text = dateFormat.format(Date(image.createdAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ImageFullscreenDialog(image: GeneratedImage, onDismiss: () -> Unit) {
    Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
                modifier =
                        Modifier.fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.9f))
                                .clickable(onClick = onDismiss)
        ) {
            // Close button
            IconButton(
                    onClick = onDismiss,
                    modifier =
                            Modifier.align(Alignment.TopEnd)
                                    .padding(16.dp)
                                    .background(Color.White, RoundedCornerShape(8.dp))
            ) { Icon(Icons.Default.Close, "Close", tint = Color.Black) }

            Column(
                    modifier = Modifier.fillMaxWidth().align(Alignment.Center).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Image
                AsyncImage(
                        model = image.cloudinaryUrl,
                        contentDescription = image.prompt,
                        contentScale = ContentScale.Fit,
                        modifier =
                                Modifier.fillMaxWidth()
                                        .weight(1f, fill = false)
                                        .clip(RoundedCornerShape(12.dp))
                )

                Spacer(Modifier.height(16.dp))

                // Info card
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = image.prompt, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                    text = image.modelUsed.split("/").lastOrNull() ?: "FLUX",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                    text = "${image.width ?: 0} × ${image.height ?: 0}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageGenerationDialog(onDismiss: () -> Unit, onGenerated: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var prompt by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf(ImageGenerationClient.ImageModel.DEFAULT) }
    var isGenerating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var generatedUrl by remember { mutableStateOf<String?>(null) }
    var showSuccess by remember { mutableStateOf(false) }

    val models = remember { ImageGenerationClient.ImageModel.all() }

    Dialog(
            onDismissRequest = { if (!isGenerating && !showSuccess) onDismiss() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Card(
                    modifier = Modifier.fillMaxWidth(0.95f).padding(16.dp),
                    shape = RoundedCornerShape(16.dp)
            ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                                Icons.Default.AutoAwesome,
                                null,
                                tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Generate Image", style = MaterialTheme.typography.titleLarge)
                    }
                    IconButton(onClick = { if (!isGenerating) onDismiss() }) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Model selector
                Text("Select Model", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))

                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                            value = selectedModel.displayName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .menuAnchor(
                                                    type = MenuAnchorType.PrimaryNotEditable,
                                                    enabled = !isGenerating
                                            ),
                            enabled = !isGenerating
                    )
                    ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                    ) {
                        models.forEach { model ->
                            DropdownMenuItem(
                                    text = { Text(model.displayName) },
                                    onClick = {
                                        selectedModel = model
                                        expanded = false
                                    }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Prompt input
                Text("Image Description", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it.take(500) },
                        placeholder = { Text("A beautiful sunset over mountains...") },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        enabled = !isGenerating,
                        maxLines = 4
                )
                Text(
                        "${prompt.length}/500 characters",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                // Generate button
                Button(
                        onClick = {
                            scope.launch {
                                isGenerating = true
                                error = null

                                // Timeout after 90 seconds (some HF models are slow)
                                val generateResult = withTimeoutOrNull(90_000L) {
                                    val result =
                                            ImageGenerationClient.generateImage(
                                                    context = context,
                                                    prompt = prompt,
                                                    model = selectedModel
                                            )

                                    result.fold(
                                            onSuccess = { generatedImage ->
                                                generatedUrl = generatedImage.url
                                                true
                                            },
                                            onFailure = { e -> 
                                                error = e.message
                                                false
                                            }
                                    )
                                }
                                
                                isGenerating = false
                                
                                // Handle result properly: only TRUE is success
                                when (generateResult) {
                                    true -> {
                                        // Real success
                                        showSuccess = true
                                        kotlinx.coroutines.delay(800)
                                        onGenerated()
                                    }
                                    null -> {
                                        // Timeout - show clear error
                                        error = "Timed out. The model may be busy—try again or switch model."
                                    }
                                    false -> {
                                        // Error already set in fold
                                    }
                                }
                            }
                        },
                        enabled = prompt.isNotBlank() && !isGenerating && !showSuccess,
                        modifier = Modifier.fillMaxWidth()
                ) {
                    if (isGenerating) {
                        DotsLoader(
                                dotRadius = 5.dp,
                                dotColor = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.generating))
                    } else {
                        Icon(Icons.Default.AutoAwesome, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.generate_image))
                    }
                }

                // Error
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Card(
                            colors =
                                    CardDefaults.cardColors(
                                            containerColor =
                                                    MaterialTheme.colorScheme.errorContainer
                                    )
                    ) {
                        Text(
                                text = it,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                        )
                    }
                }

            }
        }
            
            // Success overlay with green checkmark
            if (showSuccess) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .padding(16.dp)
                        .background(
                            Color.Black.copy(alpha = 0.7f),
                            RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier
                            .size(80.dp)
                            .padding(40.dp)
                    )
                }
            }
        }
    }
}
