package com.example.chat_ui.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import com.example.chat_ui.R
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.chat_ui.api.ModelsApiClient
import com.example.chat_ui.data.CatalogModel
import com.example.chat_ui.data.ModelsCatalogLoader
import com.example.chat_ui.ui.components.DotsLoader
import com.example.chat_ui.ui.theme.ThemeManager
import com.example.chat_ui.ui.theme.ThemeColors
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Visibility

// Legacy data class for compatibility
data class AIModel(
    val id: String,
    val name: String,
    val provider: String,
    val description: String,
    val color: Color,
    val isFree: Boolean = true,
    val isFast: Boolean = false,
    val isPopular: Boolean = false
)

// Helper to generate color from model id
private fun generateModelColor(modelId: String): Color {
    val colors = listOf(
        Color(0xFF3B82F6), Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFF10B981), Color(0xFFF59E0B),
        Color(0xFFFF6B6B), Color(0xFF4ECDC4), Color(0xFF45B7D1), Color(0xFFA55EEA),
        Color(0xFFFF9F43), Color(0xFF2ED573), Color(0xFF1E90FF), Color(0xFFFF6B81)
    )
    return colors[kotlin.math.abs(modelId.hashCode()) % colors.size]
}

// Helper to extract provider from model id
private fun extractProvider(modelId: String): String {
    return if (modelId.contains("/")) {
        modelId.split("/")[0]
    } else {
        modelId
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    onBackClick: () -> Unit,
    onModelSelect: (AIModel) -> Unit,
    selectedModelId: String,
    onNavigateToImageGen: ((String) -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf(selectedModelId) }
    var fetchedModels by remember { mutableStateOf<List<ModelsApiClient.FetchedModel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // Load catalog data
    val catalogModels = remember {
        try {
            ModelsCatalogLoader.getAllModels(context).associateBy { it.id }
        } catch (e: Exception) {
            emptyMap<String, CatalogModel>()
        }
    }
    
    // Fetch models from API on first load
    LaunchedEffect(Unit) {
        scope.launch {
            isLoading = true
            error = null
            try {
                fetchedModels = ModelsApiClient.getAllModels()
            } catch (e: Exception) {
                error = e.message
            } finally {
                isLoading = false
            }
        }
    }
    
    // Filter models based on search query
    val filteredModels = fetchedModels.filter {
        it.id.contains(searchQuery, ignoreCase = true) ||
        it.displayName.contains(searchQuery, ignoreCase = true) ||
        extractProvider(it.id).contains(searchQuery, ignoreCase = true)
    }
    
    val colorScheme = MaterialTheme.colorScheme
    val themeColors = ThemeManager.getThemeColors(ThemeManager.currentPreference, isSystemInDarkTheme())
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        // Top Bar
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Models",
                        color = colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (fetchedModels.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "(${fetchedModels.size})",
                            color = colorScheme.outline,
                            fontSize = 14.sp
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colorScheme.onBackground
                    )
                }
            },
            actions = {
                // Refresh button
                IconButton(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            error = null
                            try {
                                fetchedModels = ModelsApiClient.getAllModels()
                            } catch (e: Exception) {
                                error = e.message
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = colorScheme.onBackground
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = colorScheme.background
            )
        )
        
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = {
                Text(
                    text = "Search by name...",
                    color = if (!themeColors.isDark) themeColors.providerTextLight else colorScheme.outline
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = if (!themeColors.isDark) themeColors.providerTextLight else colorScheme.outline
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colorScheme.onSurface,
                unfocusedTextColor = colorScheme.onSurface,
                focusedBorderColor = colorScheme.primary,
                unfocusedBorderColor = colorScheme.outline,
                focusedContainerColor = colorScheme.surfaceVariant,
                unfocusedContainerColor = colorScheme.surfaceVariant,
                cursorColor = colorScheme.primary
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Loading State
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    DotsLoader(dotColor = colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading models ...",
                        color = colorScheme.outline
                    )
                }
            }
        }
        // Error State
        else if (error != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "⚠️ Failed to load models",
                        color = colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error ?: "Unknown error",
                        color = colorScheme.outline,
                        fontSize = 12.sp
                    )
                }
            }
        }
        // Models List
        else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Group models by type
                val languageModels = filteredModels.filter { it.modelType == ModelsApiClient.ModelType.LANGUAGE }
                val imagenModels = filteredModels.filter { it.modelType == ModelsApiClient.ModelType.IMAGE_GENERATION }
                val veoModels = filteredModels.filter { it.modelType == ModelsApiClient.ModelType.VIDEO_GENERATION }
                
                // Language Models Section
                if (languageModels.isNotEmpty()) {
                    item {
                        SectionHeader("Language Models")
                    }
                    items(languageModels) { model ->
                        val catalogInfo = catalogModels[model.id]
                        FetchedModelCard(
                            model = model,
                            catalogInfo = catalogInfo,
                            isSelected = selectedModel == model.id,
                            themeColors = themeColors,
                            onClick = {
                                selectedModel = model.id
                                onModelSelect(AIModel(
                                    id = model.id,
                                    name = model.displayName,
                                    provider = extractProvider(model.id),
                                    description = model.description ?: "",
                                    color = generateModelColor(model.id),
                                    isFree = true
                                ))
                            }
                        )
                    }
                }
                
                // Imagen Section
                if (imagenModels.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        SectionHeader("Image Generation")
                    }
                    items(imagenModels) { model ->
                        val catalogInfo = catalogModels[model.id]
                        FetchedModelCard(
                            model = model,
                            catalogInfo = catalogInfo,
                            isSelected = selectedModel == model.id,
                            themeColors = themeColors,
                            onClick = {
                                selectedModel = model.id
                                onNavigateToImageGen?.invoke(model.id)
                            }
                        )
                    }
                }
                
                // Veo Section
                if (veoModels.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        SectionHeader("Video Generation")
                    }
                    items(veoModels) { model ->
                        val catalogInfo = catalogModels[model.id]
                        FetchedModelCard(
                            model = model,
                            catalogInfo = catalogInfo,
                            isSelected = selectedModel == model.id,
                            themeColors = themeColors,
                            onClick = {
                                // TODO: Navigate to Video Generation screen
                                selectedModel = model.id
                            }
                        )
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    val colorScheme = MaterialTheme.colorScheme
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onBackground
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(colorScheme.outlineVariant)
        )
    }
}

@Composable
private fun FetchedModelCard(
    model: ModelsApiClient.FetchedModel,
    catalogInfo: CatalogModel?,
    isSelected: Boolean,
    themeColors: ThemeColors,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isOmni = model.id == "omni" || model.displayName.contains("Omni", ignoreCase = true)
    val provider = catalogInfo?.company ?: extractProvider(model.id)
    val omniColor = Color(0xFF8B5CF6) // Purple for Omni
    val color = if (isOmni) omniColor else generateModelColor(model.id)
    
    // Colors for badges
    val visionColor = Color(0xFFF59E0B) // Yellow/Orange
    val thinkingColor = Color(0xFF3B82F6) // Blue
    val toolsColor = Color(0xFF10B981) // Green
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isOmni) omniColor.copy(alpha = 0.1f) else colorScheme.surfaceVariant)
            .border(
                width = if (isSelected) 2.dp else if (isOmni) 1.dp else 0.dp,
                color = if (isSelected) colorScheme.primary else if (isOmni) omniColor.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Model Icon / Logo
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isOmni -> {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_omni),
                            contentDescription = "Omni",
                            tint = omniColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    provider.contains("google", ignoreCase = true) -> {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_google),
                            contentDescription = "Google",
                            modifier = Modifier.size(32.dp),
                            tint = Color.Unspecified
                        )
                    }
                    model.logoUrl != null -> {
                        AsyncImage(
                            model = model.logoUrl,
                            contentDescription = model.displayName,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                    else -> {
                        Text(
                            text = model.displayName.take(2).uppercase(),
                            color = color,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.displayName,
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1
                )
                
                Text(
                    text = provider,
                    color = if (!themeColors.isDark) themeColors.providerTextLight else colorScheme.outline,
                    fontSize = 12.sp
                )
            }
            
            // Selection Indicator
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        
        // Capability Badges Row
        Spacer(modifier = Modifier.height(10.dp))
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isOmni) {
                ModelBadge(text = "🔀 Router", color = omniColor)
            }
            
            // Vision badge
            if (catalogInfo?.vision == true || model.multimodal) {
                ModelBadge(text = "👁 Vision", color = visionColor)
            }
            
            // Thinking badge
            if (catalogInfo?.thinking == true) {
                ModelBadge(text = "🧠 Thinking", color = thinkingColor)
            }
            
            // Tools badge
            if (catalogInfo?.tools == true || model.supportsTools) {
                ModelBadge(text = "🔧 Tools", color = toolsColor)
            }
        }
        
        // Features / Description
        val description = catalogInfo?.features ?: model.description
        if (!description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                color = colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun ModelCard(
    model: AIModel,
    isSelected: Boolean,
    themeColors: ThemeColors,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colorScheme.surfaceVariant)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Model Icon
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(model.color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = model.name.take(2).uppercase(),
                color = model.color,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = model.name,
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Badges
                if (model.isFree) {
                    ModelBadge(text = "Free", color = Color(0xFF10B981)) // Green
                }
                if (model.isFast) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Fast",
                        tint = Color(0xFFF59E0B), // Yellow
                        modifier = Modifier.size(14.dp)
                    )
                }
                if (model.isPopular) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Popular",
                        tint = Color(0xFFF59E0B), // Yellow
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            
            Text(
                text = model.provider,
                color = if (!themeColors.isDark) themeColors.providerTextLight else colorScheme.outline,
                fontSize = 12.sp
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = model.description,
                color = colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
        
        // Selection Indicator
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ModelBadge(
    text: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
