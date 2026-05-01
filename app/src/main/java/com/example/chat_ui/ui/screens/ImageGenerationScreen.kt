package com.example.chat_ui.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.chat_ui.config.ConfigManager
import com.example.chat_ui.data.ApiProvider
import com.example.chat_ui.utils.FileAttachmentManager
import com.example.chat_ui.utils.ImageProcessor
import com.example.chat_ui.utils.PromptPreferences
import com.example.chat_ui.utils.CloudinaryUrlUtils
import com.example.chat_ui.viewmodel.ImageGenerationViewModel
import kotlinx.coroutines.launch

private const val THUMBNAIL_MAX_DIMENSION = 512
private const val FULLSCREEN_MAX_DIMENSION = 1600

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageGenerationScreen(
    initialModel: String? = null,
    onNavigateBack: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: ImageGenerationViewModel = viewModel()
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = androidx.compose.ui.platform.LocalContext.current
    // settings navigation moved to top-bar settings button via onSettingsClick
    var selectedImageIndex by remember { mutableStateOf<Int?>(null) }
    var saveToGallery by remember { mutableStateOf(true) }
    
    // Set initial model if provided
    LaunchedEffect(initialModel) {
        if (initialModel != null) {
            viewModel.updateSelectedModel(initialModel)
        }
    }
    
    // Restore saved draft prompt
    LaunchedEffect(Unit) {
        val savedDraft = PromptPreferences.getImageDraft(context)
        if (savedDraft.isNotBlank() && viewModel.prompt.isBlank()) {
            viewModel.updatePrompt(savedDraft)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Image Generation") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
                // Model Selection Dropdown
                ModelSelectionSection(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxWidth()
                )

             HorizontalDivider()

                // Prompt Input Area
                PromptInputSection(
                    viewModel = viewModel,
                    context = context,
                    saveToGallery = saveToGallery,
                    onSaveToGalleryChange = { saveToGallery = it },
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()

                // Generated Images Area
                GeneratedImagesSection(
                    viewModel = viewModel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    onImageClick = { index -> selectedImageIndex = index }
                )
            }

            // Settings are handled on a dedicated screen `ApiSettingsScreenV3`.

            // Error Snackbar
            viewModel.errorMessage?.let { error ->
                LaunchedEffect(error) {
                    kotlinx.coroutines.delay(4000)
                    viewModel.clearError()
                }

                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .navigationBarsPadding(),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(error)
                }
            }

            // Full Screen Image Viewer
            selectedImageIndex?.let { index ->
                if (index < viewModel.generatedImages.size) {
                    FullScreenImageViewer(
                        image = viewModel.generatedImages[index],
                        onDismiss = { selectedImageIndex = null }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelectionSection(
    viewModel: ImageGenerationViewModel,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    val googleModels = listOf(
        "google/gemini-2.5-flash-image" to "Gemini 2.5 Flash Image",
        "google/gemini-3-pro-image-preview" to "Gemini 3 Pro Image (4K)",
        "google/imagen-4.0-generate-001" to "Imagen 4.0",
        "google/imagen-4.0-ultra-generate-001" to "Imagen 4.0 Ultra",
        "google/imagen-4.0-fast-generate-001" to "Imagen 4.0 Fast"
    )

    // Use the canonical Hugging Face model IDs (no local 'hf/' prefix).
    // If a model is not available on the Router it will return 404; prefer public models for testing.
    val huggingfaceModels = listOf(
        "black-forest-labs/FLUX.1-schnell" to "FLUX.1 Schnell",
        "stabilityai/stable-diffusion-xl-base-1.0" to "Stable Diffusion XL",
        "ByteDance/SDXL-Lightning" to "SDXL Lightning",
        "playgroundai/playground-v2.5-1024px-aesthetic" to "Playground v2.5"
    )

    val provider = ConfigManager.getProviderConfig().provider

    // UX: show only models that match the currently selected provider.
    // Also hide Imagen models for Google AI Studio since they're not supported by our current endpoint.
    val allModels = when (provider) {
        ApiProvider.HUGGINGFACE -> huggingfaceModels
        ApiProvider.GOOGLE_AI_STUDIO -> googleModels
    }

    // If the user has a model selected that doesn't match the current provider,
    // auto-fallback to the first valid model so Generate doesn't silently fail.
    LaunchedEffect(provider) {
        val validIds = allModels.map { it.first }.toSet()
        val fallback = allModels.firstOrNull()?.first
        if (fallback != null && viewModel.selectedModel !in validIds) {
            viewModel.updateSelectedModel(fallback)
        }
    }

    val selectedModelDisplay = allModels.find { it.first == viewModel.selectedModel }?.second
        ?: viewModel.selectedModel
    
    Column(
        modifier = modifier.padding(16.dp)
    ) {
        Text(
            "Selected Model",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = selectedModelDisplay,
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
            )
            
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                val sectionTitle = when (provider) {
                    ApiProvider.HUGGINGFACE -> "HuggingFace Models"
                    ApiProvider.GOOGLE_AI_STUDIO -> "Google AI Studio Models"
                }

                Text(
                    sectionTitle,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                allModels.forEach { (id, name) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            viewModel.updateSelectedModel(id)
                            expanded = false
                        },
                        leadingIcon = {
                            val icon = if (provider == ApiProvider.HUGGINGFACE) Icons.Default.Extension else Icons.Default.Image
                            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    )
                }

                if (provider == ApiProvider.GOOGLE_AI_STUDIO) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        "Imagen models are hidden because they're not supported yet in AI Studio in this app.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PromptInputSection(
    viewModel: ImageGenerationViewModel,
    context: Context,
    saveToGallery: Boolean,
    onSaveToGalleryChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val info = FileAttachmentManager.getFileInfo(context, uri)
            if (info != null) {
                scope.launch {
                    val processed = ImageProcessor.processImageFromUri(
                        context = context,
                        uri = uri,
                        fileName = info.name,
                        mimeType = info.mimeType
                    )
                    if (processed != null) {
                        viewModel.updateReferenceImage(processed.value, processed.mime)
                    }
                }
            }
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Prompt History State
        var showHistory by remember { mutableStateOf(false) }

        // Prompt Input
        Box {
            OutlinedTextField(
                value = viewModel.prompt,
                onValueChange = { 
                    viewModel.updatePrompt(it)
                    PromptPreferences.saveImageDraft(context, it)
                },
                label = { Text("Prompt") },
                placeholder = { Text("Describe the image you want to generate...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                maxLines = 5,
                trailingIcon = {
                    IconButton(onClick = { showHistory = true }) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Prompt History"
                        )
                    }
                }
            )

            DropdownMenu(
                expanded = showHistory,
                onDismissRequest = { showHistory = false },
                modifier = Modifier.width(300.dp) // Limit width
            ) {
                // Load history using LaunchedEffect or just side-effect since it's prefs
                val history = remember(showHistory) { 
                    if (showHistory) PromptPreferences.getImageHistory(context) else emptyList() 
                }

                if (history.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No history available") },
                        onClick = { showHistory = false }
                    )
                } else {
                    history.forEach { historyPrompt ->
                        DropdownMenuItem(
                            text = { 
                                Text(
                                    text = historyPrompt,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                ) 
                            },
                            onClick = {
                                viewModel.updatePrompt(historyPrompt)
                                showHistory = false
                            }
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Negative Prompt (for Imagen)
        if (viewModel.selectedModel.contains("imagen")) {
            OutlinedTextField(
                value = viewModel.negativePrompt,
                onValueChange = { viewModel.updateNegativePrompt(it) },
                label = { Text("Negative Prompt (Optional)") },
                placeholder = { Text("Things to avoid in the image...") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
            
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        // Save to Gallery Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = saveToGallery,
                onCheckedChange = onSaveToGalleryChange
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Save to Gallery",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        // Reference Image (Optional for Gemini Image models)
        val isGeminiImageModel = viewModel.selectedModel.contains("gemini") && viewModel.selectedModel.contains("image")
        if (isGeminiImageModel) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Reference Image (Optional)",
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier
                        .height(48.dp)
                        .weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = colorScheme.onSurface,
                        disabledContentColor = colorScheme.onSurface.copy(alpha = 0.38f)
                    ),
                    border = BorderStroke(1.dp, colorScheme.outline),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = if (viewModel.referenceImageBase64 == null) {
                            colorScheme.onSurface
                        } else {
                            colorScheme.onSurface
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Select Reference",
                        color = colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                if (viewModel.referenceImageBase64 != null) {
                    OutlinedButton(
                        onClick = { viewModel.clearReferenceImage() },
                        modifier = Modifier
                            .height(48.dp)
                            .weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colorScheme.error,
                            disabledContentColor = colorScheme.error.copy(alpha = 0.38f)
                        ),
                        border = BorderStroke(1.dp, colorScheme.error.copy(alpha = 0.6f)),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Remove",
                            color = colorScheme.error,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
            if (viewModel.referenceImageBase64 != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val refBitmap = remember(viewModel.referenceImageBase64) {
                    try {
                        val bytes = Base64.decode(viewModel.referenceImageBase64, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } catch (e: Exception) {
                        null
                    }
                }
                if (refBitmap != null) {
                    Image(
                        bitmap = refBitmap.asImageBitmap(),
                        contentDescription = "Reference Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
        
        // Generate Button
        Button(
            onClick = { viewModel.generateImage(context, saveToGallery) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colorScheme.primary,
                contentColor = colorScheme.onPrimary,
                disabledContainerColor = colorScheme.surfaceVariant,
                disabledContentColor = colorScheme.onSurfaceVariant
            ),
            shape = RoundedCornerShape(12.dp),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 2.dp,
                pressedElevation = 4.dp
            ),
            enabled = !viewModel.isGenerating && viewModel.prompt.isNotBlank()
        ) {
            if (viewModel.isGenerating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generating...")
            } else {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate Image")
            }
        }
    }
}

@Composable
private fun GeneratedImagesSection(
    viewModel: ImageGenerationViewModel,
    modifier: Modifier = Modifier,
    onImageClick: (Int) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    
    Box(modifier = modifier.fillMaxWidth()) {
        if (viewModel.generatedImages.isEmpty() && !viewModel.isGenerating) {
            // Empty State
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = colorScheme.outline
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No images generated yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Enter a prompt and click Generate",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.outline
                )
            }
        } else if (viewModel.generatedImages.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                viewModel.generatedImages.forEachIndexed { index, imageData ->
                    GeneratedImageCard(
                        imageData = imageData,
                        onClick = { onImageClick(index) }
                    )
                }
            }
        }
        
        // Loading Overlay
        if (viewModel.isGenerating) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorScheme.surface.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Text(
                        text = "Generating image...",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "This may take up to 30 seconds",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
private fun GeneratedImageCard(
    imageData: ImageGenerationViewModel.GeneratedImageData,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = androidx.compose.ui.platform.LocalContext.current
    val cloudUrl = imageData.cloudinaryUrl

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colorScheme.surfaceVariant)
            .clickable { onClick() }
    ) {
        if (!cloudUrl.isNullOrBlank()) {
            val thumbUrl = remember(cloudUrl) { CloudinaryUrlUtils.galleryThumbnailUrl(cloudUrl) }
            val request = remember(thumbUrl) {
                ImageRequest.Builder(context)
                    .data(thumbUrl)
                    .size(512, 512)
                    .precision(Precision.INEXACT)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .allowHardware(true)
                    .allowRgb565(true)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = imageData.prompt,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentScale = ContentScale.Crop
            )
        } else {
            val bitmap = remember(imageData.base64Data.hashCode()) {
                decodeBase64BitmapSampled(
                    base64Data = imageData.base64Data,
                    maxDimension = THUMBNAIL_MAX_DIMENSION
                )
            }

            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = imageData.prompt,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(colorScheme.errorContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Preview is too large to load safely",
                        color = colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = imageData.prompt.take(60) + if (imageData.prompt.length > 60) "..." else "",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = imageData.model.removePrefix("google/"),
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.outline
            )
        }
    }
}

@Composable
private fun SettingsPanel(
    viewModel: ImageGenerationViewModel,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    
    Column(
        modifier = modifier
            .background(colorScheme.surface)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Generation Settings",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Aspect Ratio
        Text(
            text = "Aspect Ratio",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        val aspectRatios = listOf("1:1", "16:9", "9:16", "4:3", "3:4", "21:9", "2:3", "3:2", "4:5", "5:4")
        LazyColumn(
            modifier = Modifier.height(200.dp)
        ) {
            items(aspectRatios) { ratio ->
                FilterChip(
                    selected = viewModel.aspectRatio == ratio,
                    onClick = { viewModel.updateAspectRatio(ratio) },
                    label = { Text(ratio) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Image Size (Gemini 3 Pro only)
        if (viewModel.selectedModel.contains("gemini-3-pro-image")) {
            Text(
                text = "Image Size",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("1K", "2K", "4K").forEach { size ->
                    FilterChip(
                        selected = viewModel.imageSize == size,
                        onClick = { viewModel.updateImageSize(size) },
                        label = { Text(size) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Number of Images (Imagen only)
        if (viewModel.selectedModel.contains("imagen")) {
            Text(
                text = "Number of Images",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (1..4).forEach { count ->
                    FilterChip(
                        selected = viewModel.numberOfImages == count,
                        onClick = { viewModel.updateNumberOfImages(count) },
                        label = { Text(count.toString()) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Guidance Scale (HuggingFace)
        val currentProvider = com.example.chat_ui.config.ConfigManager.getProviderConfig().provider
        if (currentProvider == ApiProvider.HUGGINGFACE) {
            Text(
                text = "Guidance Scale: ${String.format("%.1f", viewModel.guidanceScale)}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Slider(
                value = viewModel.guidanceScale,
                onValueChange = { viewModel.updateGuidanceScale(it) },
                valueRange = 1f..20f,
                steps = 18
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        
        // Actions
        Button(
            onClick = { viewModel.clearImages() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors()
        ) {
            Icon(Icons.Default.Delete, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Clear Images")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullScreenImageViewer(
    image: ImageGenerationViewModel.GeneratedImageData,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = androidx.compose.ui.platform.LocalContext.current
    val cloudUrl = image.cloudinaryUrl

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val bitmap = remember(image.base64Data.hashCode(), cloudUrl) {
            if (cloudUrl.isNullOrBlank()) {
                decodeBase64BitmapSampled(
                    base64Data = image.base64Data,
                    maxDimension = FULLSCREEN_MAX_DIMENSION
                )
            } else {
                null
            }
        }
        Card(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f)),
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                TopAppBar(
                    title = { Text("Image Viewer") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.surface)
                )

                if (!cloudUrl.isNullOrBlank()) {
                    val previewUrl = remember(cloudUrl) { CloudinaryUrlUtils.previewUrl(cloudUrl) }
                    val request = remember(previewUrl) {
                        ImageRequest.Builder(context)
                            .data(previewUrl)
                            .size(1600, 1600)
                            .precision(Precision.INEXACT)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .networkCachePolicy(CachePolicy.ENABLED)
                            .allowHardware(true)
                            .allowRgb565(true)
                            .crossfade(false)
                            .build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = image.prompt,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentScale = ContentScale.Fit
                    )
                    ImageInfoPanel(
                        image = image,
                        sizeText = "Size: ${image.width ?: "?"} × ${image.height ?: "?"}",
                        savedToCloudinary = true
                    )
                } else if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = image.prompt,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentScale = ContentScale.Fit
                    )
                    ImageInfoPanel(
                        image = image,
                        sizeText = "Size: ${bitmap.width} × ${bitmap.height}",
                        savedToCloudinary = false
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .padding(16.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(colorScheme.errorContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Image is too large to decode safely on this device.",
                            color = colorScheme.onErrorContainer,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageInfoPanel(
    image: ImageGenerationViewModel.GeneratedImageData,
    sizeText: String,
    savedToCloudinary: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = image.prompt,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Model: ${image.model.removePrefix("google/")}",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
            Text(
                text = sizeText,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
            if (savedToCloudinary) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Cloudinary: saved",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.primary
                )
            }
        }
    }
}
private fun decodeBase64BitmapSampled(base64Data: String, maxDimension: Int): Bitmap? {
    if (base64Data.isBlank()) return null
    if (base64Data.length > 28_000_000) return null
    return try {
        val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, boundsOptions)

        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
            return null
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(boundsOptions, maxDimension)
            // RGB_565 halves memory use versus ARGB_8888, enough for generated-image previews.
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, decodeOptions)
    } catch (_: OutOfMemoryError) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun calculateInSampleSize(options: BitmapFactory.Options, maxDimension: Int): Int {
    var inSampleSize = 1
    val width = options.outWidth
    val height = options.outHeight

    while ((width / inSampleSize) > maxDimension || (height / inSampleSize) > maxDimension) {
        inSampleSize *= 2
    }

    return inSampleSize.coerceAtLeast(1)
}
