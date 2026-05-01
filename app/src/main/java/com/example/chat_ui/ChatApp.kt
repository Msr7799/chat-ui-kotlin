package com.example.chat_ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.chat_ui.data.Message
import com.example.chat_ui.data.firebase.FirebaseManager
import com.example.chat_ui.data.firebase.FirestoreManager
import com.example.chat_ui.data.models.User
import com.example.chat_ui.navigation.NavRoutes
import com.example.chat_ui.ui.components.ChatScreen
import com.example.chat_ui.ui.components.ImageGenerationDialog
import com.example.chat_ui.ui.components.NavigationDrawerContent
import com.example.chat_ui.ui.screens.ApiSettingsScreenV3
import com.example.chat_ui.ui.screens.GalleryScreen
import com.example.chat_ui.ui.screens.GoogleGeminiSettingsScreen
import com.example.chat_ui.ui.screens.MCPSettingsScreen
import com.example.chat_ui.ui.screens.ImageGenerationScreen
import com.example.chat_ui.ui.screens.ImageGalleryScreen
import com.example.chat_ui.ui.screens.VideoGenerationScreen
import com.example.chat_ui.ui.screens.VideoGalleryScreen
import com.example.chat_ui.ui.screens.ModelsScreen
import com.example.chat_ui.ui.screens.ProfileScreen
import com.example.chat_ui.ui.screens.SettingsScreen
import com.example.chat_ui.ui.video.GenerateVideoActivity
import com.example.chat_ui.ui.video.VideoGalleryActivity
import com.example.chat_ui.debug.DebugScreen
import com.example.chat_ui.viewmodel.ChatViewModel
import java.io.File
import kotlinx.coroutines.launch

private val DebugAdminEmails = setOf(
        "bibf101academic@gmail.com",
        "mmalromaihi99@gmail.com",
        "alromaihi2224@gmail.com"
)

private fun isDebugAdmin(email: String?): Boolean {
    // حماية مهمة: لا نفتح أدوات الاختبار إلا لحسابات الأدمن المحددة.
    return email?.trim()?.lowercase() in DebugAdminEmails
}

private fun buildConversationShareText(
        title: String?,
        model: String?,
        messages: List<Message>
): String {
    return buildString {
        appendLine(title?.takeIf { it.isNotBlank() } ?: "ChatUI Conversation")
        model?.takeIf { it.isNotBlank() }?.let { appendLine("Model: $it") }
        appendLine()
        messages.forEach { msg ->
            val sender = if (msg.isUser) "You" else "AI"
            appendLine("$sender:")
            appendLine(msg.getDisplayContent())
            appendLine()
        }
    }.trim()
}

@Composable
fun ChatApp(startRoute: String? = null, viewModel: ChatViewModel = viewModel()) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Initialize configuration manager early
    LaunchedEffect(Unit) {
        try {
            com.example.chat_ui.config.ConfigManager.init(context)
        } catch (e: Exception) {
            android.util.Log.w("ChatApp", "Config init failed: ${e.message}", e)
        }
    }

    // Track current user globally
    var currentUser by remember { mutableStateOf<User?>(null) }
    
    // Image generation dialog state
    var showImageGenDialog by remember { mutableStateOf(false) }
    var imageGenPrompt by remember { mutableStateOf("") }
    val debugEmail = currentUser?.email ?: FirebaseManager.getCurrentUserEmail()
    val isDebugAdmin = isDebugAdmin(debugEmail)

    val initialRoute = remember(startRoute) {
        when (startRoute) {
            NavRoutes.ImageGallery.route,
            NavRoutes.Gallery.route,
            NavRoutes.Settings.route,
            NavRoutes.Models.route,
            NavRoutes.Profile.route,
            NavRoutes.MCPSettings.route,
            NavRoutes.Debug.route -> startRoute
            else -> NavRoutes.Chat.route
        }
    }

    // Load current user from Firebase
    LaunchedEffect(Unit) {
        try {
            val userId = FirebaseManager.getCurrentUserId()
            if (userId != null) {
                currentUser = FirestoreManager.getUser(userId)
            }
        } catch (e: Exception) {
            // Firebase not initialized yet - this is expected on first launch
            android.util.Log.d("ChatApp", "Firebase not ready yet: ${e.message}")
        }
    }

    // File picker launchers - using new MessageFile system for multimodal API
    val imagePickerLauncher =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
                uri?.let { selectedUri ->
                    // Get file info and add as MessageFile for multimodal
                    val cursor = context.contentResolver.query(selectedUri, null, null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                            val fileName = if (nameIndex >= 0) it.getString(nameIndex) else "image.jpg"
                            val fileSize = if (sizeIndex >= 0) it.getLong(sizeIndex) else -1L
                            val mimeType = context.contentResolver.getType(selectedUri) ?: "image/jpeg"
                            android.util.Log.i("ChatApp", "Picked chat image: name=$fileName, mime=$mimeType, sizeBytes=$fileSize, uri=$selectedUri")
                            viewModel.addImageFromUri(context, selectedUri, fileName, mimeType)
                        }
                    }
                }
            }

    val filePickerLauncher =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri ->
                uri?.let { selectedUri ->
                    // Get file info and add as MessageFile
                    val cursor = context.contentResolver.query(selectedUri, null, null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                            val fileName = if (nameIndex >= 0) it.getString(nameIndex) else "file"
                            val fileSize = if (sizeIndex >= 0) it.getLong(sizeIndex) else -1L
                            val mimeType = context.contentResolver.getType(selectedUri) ?: "application/octet-stream"
                            android.util.Log.i("ChatApp", "Picked chat file: name=$fileName, mime=$mimeType, sizeBytes=$fileSize, uri=$selectedUri")
                            
                            // Use appropriate method based on file type
                            if (mimeType.startsWith("image/")) {
                                viewModel.addImageFromUri(context, selectedUri, fileName, mimeType)
                            } else {
                                viewModel.addTextFileFromUri(context, selectedUri, fileName, mimeType)
                            }
                        }
                    }
                }
            }

    // Camera launcher (capture new image)
    val cameraImageUri = remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.TakePicture()) { success ->
                if (success) {
                    cameraImageUri.value?.let { uri ->
                        viewModel.addImageFromUri(context, uri, "camera_${System.currentTimeMillis()}.jpg", "image/jpeg")
                    }
                }
            }

    // Voice input state - holds text to be inserted into input field
    var voiceInputText by remember { mutableStateOf("") }
    var voiceInputCounter by remember { mutableStateOf(0) } // To trigger recomposition

    // Voice input launcher (speech-to-text) - now inserts into input field instead of sending
    val voiceInputLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val data = result.data
                    val matches = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    val text = matches?.firstOrNull()
                    if (!text.isNullOrBlank()) {
                        // Insert text into input field instead of sending directly
                        voiceInputText = text
                        voiceInputCounter++ // Force recomposition
                    }
                }
            }

    // Get state from ViewModel
    val conversations = viewModel.conversations
    val currentConversation = viewModel.currentConversation
    val messages = viewModel.messages
    val selectedModelId = viewModel.selectedModelId
    val isLoading = viewModel.isLoading
    val attachments = viewModel.attachments
    val isUploadingAttachment = viewModel.isUploadingAttachment

    NavHost(
            navController = navController,
            startDestination = initialRoute,
            enterTransition = {
                slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            },
            popEnterTransition = {
                slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            }
    ) {
        // Chat Screen with Drawer
        composable(NavRoutes.Chat.route) {
            ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        NavigationDrawerContent(
                                conversations = conversations,
                                currentConversation = currentConversation,
                                currentUser = currentUser,
                                onConversationClick = { conversation ->
                                    viewModel.selectConversation(conversation)
                                    scope.launch { drawerState.close() }
                                },
                                onNewChat = {
                                    viewModel.newChat()
                                    scope.launch { drawerState.close() }
                                },
                                onDeleteConversation = { conversation ->
                                    viewModel.deleteConversation(conversation)
                                },
                                onSettingsClick = {
                                    scope.launch { drawerState.close() }
                                    navController.navigate(NavRoutes.Settings.route)
                                },
                                onModelsClick = {
                                    scope.launch { drawerState.close() }
                                    navController.navigate(NavRoutes.Models.route)
                                },
                                onGalleryClick = {
                                    scope.launch { drawerState.close() }
                                    navController.navigate(NavRoutes.Gallery.route)
                                },
                                onImageGalleryClick = {
                                    scope.launch { drawerState.close() }
                                    navController.navigate(NavRoutes.ImageGallery.route)
                                },
                                onVideoGenerationClick = {
                                    scope.launch { drawerState.close() }
                                    val intent = Intent(context, GenerateVideoActivity::class.java)
                                    if (context !is Activity) {
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                },
                                onVideoGalleryClick = {
                                    scope.launch { drawerState.close() }
                                    val intent = Intent(context, VideoGalleryActivity::class.java)
                                    if (context !is Activity) {
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                },
                                onProfileClick = {
                                    scope.launch { drawerState.close() }
                                    navController.navigate(NavRoutes.Profile.route)
                                },
                                onMCPClick = {
                                    scope.launch { drawerState.close() }
                                    navController.navigate(NavRoutes.MCPSettings.route)
                                }
                        )
                    },
                    gesturesEnabled = true
            ) {
                Box(
                        modifier =
                                Modifier.fillMaxSize()
                                        .background(MaterialTheme.colorScheme.background)
                ) {
                    ChatScreen(
                            messages = messages,
                            currentConversation = currentConversation,
                            isLoading = isLoading,
                            attachments = attachments,
                            isUploadingAttachment = isUploadingAttachment,
                            onMenuClick = {
                                    viewModel.startRealtimeSync()
                                    scope.launch { drawerState.open() }
                                },
                            onSendMessage = { messageText -> 
                                // Auto-detect image generation requests
                                val imageGenerationVerbs = listOf(
                                    "انشئ", "أنشئ", "أنشأ", "انشأ", "انشا", "أنشا",
                                    "ارسم", "إرسم",
                                    "صمم", 
                                    "اعمل", "أعمل", "اعملي",
                                    "generate", "create", "draw", "make", "design",
                                    "توليد", "ولد"
                                )
                                val analysisVerbs = listOf("حلل", "تحلل", "تحليل", "اشرح", "صف", "اقرأ", "read", "analyze", "describe", "explain")
                                
                                val normalizedText = messageText.replace("ة", "ه").replace("أ", "ا").replace("إ", "ا").replace("ئ", "ا")
                                val hasGenerationVerb = imageGenerationVerbs.any { keyword ->
                                    normalizedText.contains(keyword.replace("ة", "ه").replace("أ", "ا").replace("إ", "ا").replace("ئ", "ا"), ignoreCase = true)
                                }
                                val hasAnalysisVerb = analysisVerbs.any { keyword ->
                                    normalizedText.contains(keyword.replace("ة", "ه").replace("أ", "ا").replace("إ", "ا").replace("ئ", "ا"), ignoreCase = true)
                                }
                                
                                val hasImageWord = normalizedText.contains("صور", ignoreCase = true) || 
                                                   normalizedText.contains("صوره", ignoreCase = true) ||
                                                   normalizedText.contains("image", ignoreCase = true)
                                val hasPendingFiles = viewModel.pendingFiles.isNotEmpty() || attachments.isNotEmpty()
                                
                                if (!hasPendingFiles && hasGenerationVerb && hasImageWord && !hasAnalysisVerb) {
                                    android.util.Log.d("ChatApp", "Image generation detected for: $messageText")
                                    viewModel.generateImageInChat(
                                        prompt = messageText,
                                        context = context,
                                        saveToGallery = true
                                    )
                                } else {
                                    if (hasPendingFiles) {
                                        android.util.Log.i("ChatApp", "Sending chat with attachments for analysis, not image generation. pendingFiles=${viewModel.pendingFiles.size}, attachments=${attachments.size}")
                                    }
                                    viewModel.sendMessage(messageText, context)
                                }
                            },
                            onAttachImage = { imagePickerLauncher.launch("image/*") },
                            onAttachFile = { filePickerLauncher.launch(arrayOf("*/*")) },
                            onRemoveAttachment = { attachment ->
                                viewModel.removeAttachment(attachment)
                            },
                            onCaptureImage = {
                                try {
                                    val imageFile =
                                            File(
                                                    context.cacheDir,
                                                    "camera_${System.currentTimeMillis()}.jpg"
                                            )
                                    val uri =
                                            FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.fileprovider",
                                                    imageFile
                                            )
                                    cameraImageUri.value = uri
                                    cameraLauncher.launch(uri)
                                } catch (e: Exception) {
                                    android.util.Log.e("ChatApp", "Failed to launch camera: ${e.message}", e)
                                    android.widget.Toast.makeText(
                                        context,
                                        "فشل فتح الكاميرا: ${e.message}",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            onVoiceInput = {
                                val intent =
                                        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(
                                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                            )
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
                                            putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث الآن...")
                                        }
                                voiceInputLauncher.launch(intent)
                            },
                            voiceInputText = voiceInputText,
                            voiceInputKey = voiceInputCounter,
                            currentModelId = selectedModelId,
                            onCurrentModelClick = {
                                // Navigate directly to Models screen when tapping current model
                                // chip
                                navController.navigate(NavRoutes.Models.route)
                            },
                            onMCPSettingsClick = {
                                navController.navigate(NavRoutes.MCPSettings.route)
                            },
                            pendingFiles = viewModel.pendingFiles,
                            onRemovePendingFile = { file ->
                                viewModel.removePendingFile(file)
                            },
                            onVoiceRecordingSend = { _audioFile ->
                                // TODO: Send audio to Whisper API for transcription
                            },
                            onUrlFileAdded = { file ->
                                // Add file from URL to pending files
                                viewModel.addPendingFile(file)
                            },
                            onRegenerate = { updatedPrompt ->
                                viewModel.regenerateLastMessage(updatedPrompt)
                            },
                            onStopGeneration = {
                                viewModel.stopGeneration()
                            },
                            onGenerateImage = {
                                showImageGenDialog = true
                            },
                            mcpToolsEnabled = viewModel.mcpToolsEnabled,
                            onToggleMCPTools = {
                                viewModel.toggleMCPTools()
                            },
                            onShareClick = {
                                if (messages.isNotEmpty()) {
                                    val shareText =
                                            buildConversationShareText(
                                                    title = currentConversation?.title,
                                                    model = currentConversation?.model ?: selectedModelId,
                                                    messages = messages
                                            )
                                    val sendIntent =
                                            Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, "ChatUI Conversation")
                                                putExtra(Intent.EXTRA_TEXT, shareText)
                                            }
                                    val shareIntent =
                                            Intent.createChooser(sendIntent, "Share conversation")
                                    if (context !is Activity) {
                                        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(shareIntent)
                                } else {
                                    android.widget.Toast.makeText(
                                        context,
                                        context.getString(R.string.no_conversation_to_share),
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                    )
                }
            }
        }

        // Settings Screen
        composable(NavRoutes.Settings.route) {
            SettingsScreen(
                    onBackClick = { navController.popBackStack() },
                    onProfileClick = { navController.navigate(NavRoutes.Profile.route) },
                    onApiSettingsClick = { navController.navigate(NavRoutes.ApiSettings.route) },
                    onMCPSettingsClick = { navController.navigate(NavRoutes.MCPSettings.route) },
                    showDebugConsole = isDebugAdmin,
                    onDebugClick = {
                        if (isDebugAdmin) {
                            navController.navigate(NavRoutes.Debug.route)
                        } else {
                            // حماية إضافية في الواجهة: لا نكشف الديباق لغير الأدمن.
                            android.widget.Toast.makeText(
                                    context,
                                    "Debug Console متاح للأدمن فقط",
                                    android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onGoogleGeminiSettingsClick = { navController.navigate(NavRoutes.GoogleGeminiSettings.route) }
            )
        }

        // Google Gemini Settings Screen
        composable(NavRoutes.GoogleGeminiSettings.route) {
            GoogleGeminiSettingsScreen(onBackClick = { navController.popBackStack() })
        }
        
        // MCP Settings Screen
        composable(NavRoutes.MCPSettings.route) {
            MCPSettingsScreen(onBackClick = { navController.popBackStack() })
        }

        // API Settings Screen
        composable(NavRoutes.ApiSettings.route) {
            ApiSettingsScreenV3(onBackClick = { navController.popBackStack() })
        }

        // Models Screen
        composable(NavRoutes.Models.route) {
            LaunchedEffect(Unit) { viewModel.fetchModels() }
            ModelsScreen(
                    onBackClick = { navController.popBackStack() },
                    onModelSelect = { model ->
                        viewModel.selectModel(model.id)
                        // Small delay to ensure state is updated before navigation
                        scope.launch {
                            kotlinx.coroutines.delay(100)
                            navController.popBackStack()
                        }
                    },
                    selectedModelId = selectedModelId,
                    onNavigateToImageGen = { modelId ->
                        navController.navigate(NavRoutes.ImageGeneration.createRoute(modelId))
                    }
            )
        }

        // Gallery Screen
        composable(NavRoutes.Gallery.route) {
            GalleryScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onGenerateNew = { /* Dialog opens inside GalleryScreen */},
                    onNavigateToImageGen = { modelId ->
                        navController.navigate(NavRoutes.ImageGeneration.createRoute(modelId))
                    }
            )
        }
        
        // Image Generation Screen
        composable(
            route = NavRoutes.ImageGeneration.route,
            arguments = listOf(
                androidx.navigation.navArgument("model") {
                    type = androidx.navigation.NavType.StringType
                    defaultValue = "google/gemini-2.5-flash-image"
                }
            )
        ) { backStackEntry ->
            val encodedModel = backStackEntry.arguments?.getString("model") ?: "google/gemini-2.5-flash-image"
            val modelId = java.net.URLDecoder.decode(encodedModel, "UTF-8")
            ImageGenerationScreen(
                initialModel = modelId,
           onSettingsClick = { navController.navigate(NavRoutes.Settings.route) },
    onNavigateBack = { navController.popBackStack() }
)
        }
        
        // Image Gallery Screen
        composable(NavRoutes.ImageGallery.route) {
            ImageGalleryScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToImageGeneration = { modelId ->
                    navController.navigate(NavRoutes.ImageGeneration.createRoute(modelId))
                }
            )
        }
        
        // Video Generation Screen
        composable(NavRoutes.VideoGeneration.route) {
            VideoGenerationScreen(
                    onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Video Gallery Screen  
        composable(NavRoutes.VideoGallery.route) {
            VideoGalleryScreen(
                    onNavigateBack = { navController.popBackStack() }
            )
        }

        // Profile Screen (Google OAuth)
        composable(NavRoutes.Profile.route) {
            ProfileScreen(
                    onBackClick = { navController.popBackStack() },
                    onSignOut = {
                        // Clear ViewModel state before logout
                        scope.launch {
                            // This will trigger onCleared() which cancels Firebase listeners
                            currentUser = null
                            kotlinx.coroutines.delay(150) // Give time for cleanup
                            navController.popBackStack()
                        }
                    },
                    onUserChanged = { user: User? -> currentUser = user }
            )
        }
        
        // Debug Screen
        composable(NavRoutes.Debug.route) {
            if (isDebugAdmin) {
                DebugScreen(
                    onBack = { navController.popBackStack() }
                )
            } else {
                LaunchedEffect(Unit) {
                    // حماية الراوت نفسه حتى لو تم فتحه مباشرة.
                    android.widget.Toast.makeText(
                            context,
                            "Debug Console متاح للأدمن فقط",
                            android.widget.Toast.LENGTH_SHORT
                    ).show()
                    navController.navigate(NavRoutes.Chat.route) {
                        popUpTo(NavRoutes.Debug.route) { inclusive = true }
                    }
                }
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            }
        }
    }
    
    // Image Generation Dialog
    if (showImageGenDialog) {
        ImageGenerationDialog(
            onDismiss = {
                showImageGenDialog = false
                imageGenPrompt = ""
            },
            onGenerate = { prompt ->
                viewModel.generateImageInChat(
                    prompt = prompt,
                    context = context,
                    saveToGallery = true
                )
                showImageGenDialog = false
                imageGenPrompt = ""
            },
            isGenerating = viewModel.isGeneratingImage
        )
    }
}
