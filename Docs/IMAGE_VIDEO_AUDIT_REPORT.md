# 📊 تقرير فحص شامل: Image & Video Generation + Galleries

**التاريخ:** 21 ديسمبر 2025  
**النطاق:** Image Generation, Image Gallery, Video Generation, Video Gallery, Cloudinary, Firebase Integration, UX

---

## 🎯 الملخص التنفيذي

### ✅ **النقاط الإيجابية:**
1. **بنية معمارية جيدة:** فصل واضح بين API Clients, ViewModels, UI
2. **تكامل متعدد:** دعم Google AI Studio, Vertex AI, HuggingFace
3. **تخزين مزدوج:** Cloudinary للصور + Firebase Storage للفيديوهات
4. **معالجة async صحيحة:** استخدام coroutines و Flow

### ❌ **المشاكل الحرجة:**
1. **🔴 CRITICAL:** Video API لا يتوافق مع Google Veo الرسمي
2. **🔴 CRITICAL:** عدم وجود Image Gallery UI (فقط VideoGallery)
3. **🟡 IMPORTANT:** منطق حفظ/جلب الفيديوهات غير متسق
4. **🟡 IMPORTANT:** UX ضعيف (لا loading states, لا error recovery)
5. **🟢 REGULAR:** عدم استخدام API settings المحفوظة

---

## 📸 **القسم 1: Image Generation**

### 1.1 **فحص `ImageGenerationApiClient.kt`**

#### ✅ **ما هو صحيح:**

```kotlin
// ✅ دعم متعدد للـ Providers
when (providerConfig.provider) {
    ApiProvider.GOOGLE_AI_STUDIO -> generateWithGoogleAIStudio(model, request)
    ApiProvider.GOOGLE_VERTEX_AI -> generateWithVertexAI(model, request)
    ApiProvider.HUGGINGFACE -> generateWithHuggingFace(model, request)
}

// ✅ حفظ صحيح إلى Cloudinary + Firestore
val cloudinaryResult = CloudinaryManager.uploadImage(...)
FirestoreManager.saveGeneratedImage(generatedImage)
```

#### ❌ **المشاكل:**

##### **1. Google AI Studio API - نقص في المعاملات**

```kotlin
// ❌ المشكلة الحالية:
val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$apiKey"

// ✅ الحل الصحيح (حسب Google Docs):
// يجب إضافة دعم لـ:
// - imageSize: "1K", "2K", "4K" (لـ Gemini 3 Pro)
// - guidanceScale: Float
// - seed: Long
```

**الإصلاح المطلوب:**
```kotlin
// في generateWithGoogleAIStudio()
val requestBody = buildString {
    append("{")
    append("\"contents\":[{\"parts\":[{\"text\":\"${request.prompt.escapeJson()}\"}]}],")
    append("\"generationConfig\":{")
    
    if (isGeminiImage) {
        append("\"responseModalities\":[\"IMAGE\",\"TEXT\"]")
        // إضافة imageSize للـ Gemini 3 Pro
        if (modelId.contains("gemini-3") && request.imageSize != null) {
            append(",\"imageSize\":\"${request.imageSize}\"")
        }
    } else {
        // Imagen models
        append("\"imageConfig\":{")
        append("\"aspectRatio\":\"${request.aspectRatio}\"")
        
        if (request.negativePrompt != null) {
            append(",\"negativePrompt\":\"${request.negativePrompt.escapeJson()}\"")
        }
        
        // إضافة guidanceScale
        if (request.guidanceScale != null) {
            append(",\"guidanceScale\":${request.guidanceScale}")
        }
        
        // إضافة seed
        if (request.seed != null) {
            append(",\"seed\":${request.seed}")
        }
        
        append(",\"sampleCount\":${request.numberOfImages.coerceIn(1, 4)}")
        append("}")
    }
    
    append("}")
    append("}")
}
```

##### **2. Vertex AI - غير مُطبّق**

```kotlin
// ❌ المشكلة:
private suspend fun generateWithVertexAI(...): ImageGenResult {
    return ImageGenResult.Error("Vertex AI image generation not yet implemented")
}
```

**الحل:** يجب تطبيق Vertex AI Image Generation باستخدام:
- `imagen-4.0-generate-001`
- `gemini-2.5-flash-image-preview`

##### **3. HuggingFace - استخدام API Key خاطئ**

```kotlin
// ❌ المشكلة:
val apiKey = ConfigManager.openAiApiKey  // خطأ! هذا لـ OpenAI

// ✅ الحل:
val apiKey = ConfigManager.getProviderConfig().apiKey
```

##### **4. عدم استخدام Base URL من ConfigManager**

```kotlin
// ❌ المشكلة الحالية:
val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$apiKey"

// ✅ الحل:
val providerConfig = ConfigManager.getProviderConfig()
val baseUrl = providerConfig.baseUrl.trimEnd('/')
val url = "$baseUrl/models/$modelId:generateContent?key=$apiKey"
```

---

### 1.2 **فحص `ImageGenerationViewModel.kt`**

#### ✅ **ما هو صحيح:**
- State management جيد باستخدام Compose State
- معالجة async صحيحة

#### ❌ **المشاكل:**

##### **1. عدم وجود Error Recovery**

```kotlin
// ❌ المشكلة:
isGenerating = false
errorMessage = result.message
// لا يوجد retry mechanism!

// ✅ الحل المقترح:
var retryCount by mutableStateOf(0)
val maxRetries = 3

fun retryGeneration() {
    if (retryCount < maxRetries) {
        retryCount++
        generateImage(lastPrompt, lastModel, context, saveToFirestore)
    }
}
```

##### **2. عدم وجود Progress Tracking**

```kotlin
// ✅ إضافة مطلوبة:
var generationProgress by mutableStateOf(0f)

// في generateImage():
generationProgress = 0.3f // Starting request
// ... API call ...
generationProgress = 0.7f // Processing response
// ... save to Cloudinary ...
generationProgress = 1.0f // Complete
```

---

## 🖼️ **القسم 2: Image Gallery**

### 2.1 **المشكلة الحرجة: لا يوجد Image Gallery UI!**

#### ❌ **الوضع الحالي:**
- يوجد `FirestoreManager.getGeneratedImagesFlow()` ✅
- يوجد `GeneratedImage` data class ✅
- **لكن لا يوجد:**
  - ❌ `ImageGalleryScreen.kt`
  - ❌ `ImageGalleryViewModel.kt`
  - ❌ `ImageGalleryActivity.kt`

#### ✅ **الحل المطلوب:**

**إنشاء `ImageGalleryScreen.kt`:**

```kotlin
@Composable
fun ImageGalleryScreen(onNavigateBack: () -> Unit) {
    val viewModel: ImageGalleryViewModel = viewModel()
    val images by viewModel.images.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Image Gallery") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.padding(padding)
            ) {
                items(images) { image ->
                    ImageGalleryItem(
                        image = image,
                        onImageClick = { /* Open full screen */ },
                        onDeleteClick = { viewModel.deleteImage(image) }
                    )
                }
            }
        }
    }
}
```

**إنشاء `ImageGalleryViewModel.kt`:**

```kotlin
class ImageGalleryViewModel : ViewModel() {
    private val _images = MutableStateFlow<List<GeneratedImage>>(emptyList())
    val images: StateFlow<List<GeneratedImage>> = _images.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    init {
        loadImages()
    }
    
    private fun loadImages() {
        viewModelScope.launch {
            _isLoading.value = true
            FirestoreManager.getGeneratedImagesFlow().collect { imageList ->
                _images.value = imageList
                _isLoading.value = false
            }
        }
    }
    
    fun deleteImage(image: GeneratedImage) {
        viewModelScope.launch {
            try {
                // Delete from Firestore
                FirestoreManager.deleteGeneratedImage(image.id)
                
                // Delete from Cloudinary
                CloudinaryManager.deleteImage(image.cloudinaryPublicId)
                
                // Update UI
                _images.value = _images.value.filter { it.id != image.id }
            } catch (e: Exception) {
                Log.e("ImageGalleryVM", "Failed to delete image", e)
            }
        }
    }
}
```

---

### 2.2 **فحص `FirestoreManager.getGeneratedImagesFlow()`**

#### ✅ **ما هو صحيح:**

```kotlin
fun getGeneratedImagesFlow(): Flow<List<GeneratedImage>> = callbackFlow {
    val userId = FirebaseManager.getCurrentUserId()
    if (userId == null) {
        trySend(emptyList())
        close()
        return@callbackFlow
    }
    
    val listener = firestore
        .collection("generatedImages")
        .whereEqualTo("userId", userId)
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .addSnapshotListener { snapshot, error ->
            // ... parsing ...
        }
    
    awaitClose { listener.remove() }
}
```

#### ⚠️ **تحسينات مقترحة:**

```kotlin
// إضافة Pagination:
fun getGeneratedImagesFlow(
    limit: Int = 20,
    lastDocument: DocumentSnapshot? = null
): Flow<PaginatedResult<GeneratedImage>> = callbackFlow {
    // ... implementation with pagination ...
}

// إضافة Filtering:
fun getGeneratedImagesFlow(
    modelFilter: String? = null,
    dateRange: Pair<Long, Long>? = null
): Flow<List<GeneratedImage>> = callbackFlow {
    var query = firestore.collection("generatedImages")
        .whereEqualTo("userId", userId)
    
    if (modelFilter != null) {
        query = query.whereEqualTo("modelUsed", modelFilter)
    }
    
    if (dateRange != null) {
        query = query
            .whereGreaterThanOrEqualTo("createdAt", dateRange.first)
            .whereLessThanOrEqualTo("createdAt", dateRange.second)
    }
    
    // ... rest of implementation ...
}
```

---

## 🎬 **القسم 3: Video Generation**

### 3.1 **المشكلة الحرجة: API لا يتوافق مع Google Veo الرسمي**

#### ❌ **الوضع الحالي في `VeoVideoClient.kt`:**

```kotlin
// ❌ المشكلة: استخدام Backend مخصص بدلاً من Google Veo API الرسمي
val backendUrl = ConfigManager.get(ConfigManager.Keys.VEO_BACKEND_BASE_URL, "")
val url = "$backendUrl/v1/video/text"

// Request format مخصص:
JSONObject().apply {
    put("prompt", params.prompt)
    put("durationSeconds", sanitizeDurationSeconds(params.durationSeconds))
    put("aspectRatio", params.aspectRatio)
    put("quality", toBackendQuality(params.quality))
    // ... custom parameters ...
}
```

#### ✅ **Google Veo API الرسمي (حسب Documentation):**

```kotlin
// الـ Endpoint الصحيح:
val url = "https://us-central1-aiplatform.googleapis.com/v1/projects/$PROJECT_ID/locations/us-central1/publishers/google/models/veo-2.0-generate-preview:predictLongRunning"

// Request format الصحيح:
{
  "instances": [
    {
      "prompt": "TEXT_PROMPT"
    }
  ],
  "parameters": {
    "storageUri": "gs://YOUR_BUCKET/videos/",
    "sampleCount": 1,
    "aspectRatio": "16:9",  // أو "9:16"
    "negativePrompt": "disturbing images",
    "personGeneration": "allow_adult",  // أو "disallow"
    "resolution": "720p",  // أو "1080p" (Veo 3 only)
    "seed": 12345
  }
}
```

#### 🔧 **الإصلاح المطلوب:**

**إنشاء `VeoOfficialClient.kt` جديد:**

```kotlin
object VeoOfficialClient {
    private const val TAG = "VeoOfficialClient"
    
    suspend fun generateVideo(
        context: Context,
        request: VideoGenerationRequest
    ): VeoApiResult<VideoGenerationResult> = withContext(Dispatchers.IO) {
        try {
            // 1. Get Firebase Auth token
            val token = FirebaseAuthHelper.getFirebaseIdToken(forceRefresh = false)
                ?: return@withContext VeoApiResult.Error("Authentication required")
            
            // 2. Get project ID from ConfigManager
            val projectId = ConfigManager.get("GOOGLE_PROJECT_ID", "")
            if (projectId.isBlank()) {
                return@withContext VeoApiResult.Error("Google Project ID not configured")
            }
            
            // 3. Build request
            val url = "https://us-central1-aiplatform.googleapis.com/v1/projects/$projectId/locations/us-central1/publishers/google/models/veo-2.0-generate-preview:predictLongRunning"
            
            val requestBody = JSONObject().apply {
                put("instances", JSONArray().apply {
                    put(JSONObject().apply {
                        put("prompt", request.prompt)
                    })
                })
                put("parameters", JSONObject().apply {
                    put("storageUri", "gs://${projectId}-videos/")
                    put("sampleCount", 1)
                    put("aspectRatio", request.aspectRatio)
                    
                    if (request.negativePrompt != null) {
                        put("negativePrompt", request.negativePrompt)
                    }
                    
                    if (request.seed != null) {
                        put("seed", request.seed)
                    }
                    
                    // Veo 3 only
                    if (request.resolution != null) {
                        put("resolution", request.resolution)
                    }
                })
            }
            
            // 4. Send request
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            
            OutputStreamWriter(connection.outputStream).use {
                it.write(requestBody.toString())
            }
            
            // 5. Parse response
            val responseCode = connection.responseCode
            if (responseCode != 200) {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: ""
                return@withContext VeoApiResult.Error("HTTP $responseCode: $errorBody")
            }
            
            val responseBody = connection.inputStream.bufferedReader().readText()
            val responseJson = JSONObject(responseBody)
            val operationName = responseJson.getString("name")
            
            // 6. Poll operation status
            val result = pollOperationStatus(operationName, token)
            
            VeoApiResult.Success(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Video generation failed", e)
            VeoApiResult.Error(e.message ?: "Unknown error")
        }
    }
    
    private suspend fun pollOperationStatus(
        operationName: String,
        token: String
    ): VideoGenerationResult {
        // Poll every 5 seconds until done
        while (true) {
            delay(5000)
            
            val url = "https://us-central1-aiplatform.googleapis.com/v1/$operationName"
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.setRequestProperty("Authorization", "Bearer $token")
            
            val responseBody = connection.inputStream.bufferedReader().readText()
            val responseJson = JSONObject(responseBody)
            
            if (responseJson.has("done") && responseJson.getBoolean("done")) {
                // Extract video URL from response
                val response = responseJson.getJSONObject("response")
                val predictions = response.getJSONArray("predictions")
                val videoGcsUri = predictions.getJSONObject(0).getString("gcsUri")
                
                // Download and upload to Firebase Storage
                val videoUrl = downloadAndUploadVideo(videoGcsUri)
                
                return VideoGenerationResult(
                    id = UUID.randomUUID().toString(),
                    url = videoUrl,
                    prompt = "", // Store from original request
                    visibility = VideoVisibility.PRIVATE,
                    duration = 8,
                    aspectRatio = "16:9",
                    createdAt = System.currentTimeMillis(),
                    jobId = operationName
                )
            }
            
            if (responseJson.has("error")) {
                throw Exception("Operation failed: ${responseJson.getJSONObject("error").getString("message")}")
            }
        }
    }
}
```

---

### 3.2 **فحص `VideoScreens.kt`**

#### ✅ **ما هو صحيح:**
- UI جيد مع Tabs (Basic/Advanced)
- Feature flags للتحكم في الميزات

#### ❌ **المشاكل:**

##### **1. عدم استخدام ConfigManager API Settings**

```kotlin
// ❌ المشكلة الحالية:
// VideoScreens.kt لا يستخدم API settings المحفوظة في ConfigManager

// ✅ الحل:
LaunchedEffect(Unit) {
    val providerConfig = ConfigManager.getProviderConfig()
    
    // Check if Vertex AI is configured
    if (providerConfig.provider != ApiProvider.GOOGLE_VERTEX_AI) {
        showError = true
        errorMessage = "Please configure Google Vertex AI in API Settings first"
    }
    
    // Check if project ID is set
    val projectId = ConfigManager.get("GOOGLE_PROJECT_ID", "")
    if (projectId.isBlank()) {
        showError = true
        errorMessage = "Please set Google Project ID in API Settings"
    }
}
```

##### **2. عدم وجود Validation للـ Input**

```kotlin
// ✅ إضافة Validation:
val isPromptValid = prompt.isNotBlank() && prompt.length >= 10
val isDurationValid = duration in 4..8
val isAspectRatioValid = aspectRatio in listOf("16:9", "9:16", "1:1")

Button(
    onClick = { /* Generate */ },
    enabled = isPromptValid && isDurationValid && isAspectRatioValid && !isGenerating
) {
    Text("Generate Video")
}

// عرض رسائل خطأ:
if (!isPromptValid && prompt.isNotBlank()) {
    Text(
        text = "Prompt must be at least 10 characters",
        color = MaterialTheme.colorScheme.error,
        fontSize = 12.sp
    )
}
```

---

## 📹 **القسم 4: Video Gallery**

### 4.1 **فحص `VideoGalleryViewModel.kt`**

#### ✅ **ما هو صحيح:**
- استخدام LiveData صحيح
- معالجة async جيدة
- دعم Filtering (ALL/PUBLIC/PRIVATE)

#### ❌ **المشاكل:**

##### **1. منطق الحذف غير متسق**

```kotlin
// ❌ المشكلة الحالية:
if (videoResult.visibility == VeoVideoClient.VideoVisibility.PRIVATE) {
    try {
        val storageRef = FirebaseStorage.getInstance().getReferenceFromUrl(videoResult.url)
        storageRef.delete().await()
    } catch (e: Exception) {
        Log.w("VideoGalleryVM", "Failed to delete storage object: ${e.message}")
    }
}

// ✅ المشكلة:
// 1. لا يحذف من YouTube للفيديوهات العامة
// 2. يتجاهل الأخطاء بصمت (Log.w فقط)

// ✅ الحل:
suspend fun deleteVideo(videoResult: VeoVideoClient.VideoGenerationResult) {
    try {
        _isLoading.value = true
        
        // 1. Delete from Firestore first
        FirebaseManager.firestore
            .collection("generated_videos")
            .document(videoResult.id)
            .delete()
            .await()
        
        // 2. Delete from storage based on visibility
        when (videoResult.visibility) {
            VeoVideoClient.VideoVisibility.PRIVATE -> {
                try {
                    val storageRef = FirebaseStorage.getInstance()
                        .getReferenceFromUrl(videoResult.url)
                    storageRef.delete().await()
                } catch (e: Exception) {
                    // Log but don't fail - file might already be deleted
                    Log.w(TAG, "Storage delete failed: ${e.message}")
                }
            }
            VeoVideoClient.VideoVisibility.PUBLIC -> {
                // TODO: Implement YouTube video deletion
                // Requires YouTube Data API v3 integration
                Log.w(TAG, "YouTube video deletion not implemented yet")
            }
        }
        
        // 3. Update local list
        allVideos = allVideos.filter { it.id != videoResult.id }
        applyFilter(_currentFilter.value ?: VideoFilter.ALL)
        
    } catch (e: Exception) {
        Log.e(TAG, "Failed to delete video", e)
        _errorMessage.value = "Failed to delete video: ${e.message}"
        throw e  // Re-throw to let UI handle it
    } finally {
        _isLoading.value = false
    }
}
```

##### **2. عدم وجود Pagination**

```kotlin
// ❌ المشكلة: يحمل كل الفيديوهات مرة واحدة
val snapshot = FirebaseManager.firestore
    .collection("generated_videos")
    .whereEqualTo("userId", userId)
    .orderBy("createdAt", Query.Direction.DESCENDING)
    .get()
    .await()

// ✅ الحل: إضافة Pagination
private var lastDocument: DocumentSnapshot? = null
private val PAGE_SIZE = 20

fun loadMoreVideos() {
    viewModelScope.launch {
        try {
            val query = FirebaseManager.firestore
                .collection("generated_videos")
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(PAGE_SIZE.toLong())
            
            val snapshot = if (lastDocument != null) {
                query.startAfter(lastDocument!!).get().await()
            } else {
                query.get().await()
            }
            
            if (snapshot.documents.isNotEmpty()) {
                lastDocument = snapshot.documents.last()
                val newVideos = snapshot.documents.mapNotNull { /* parse */ }
                allVideos = allVideos + newVideos
                applyFilter(_currentFilter.value ?: VideoFilter.ALL)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load more videos", e)
        }
    }
}
```

---

## ☁️ **القسم 5: Cloudinary & Firebase Integration**

### 5.1 **فحص `CloudinaryManager.kt`**

#### ✅ **ما هو صحيح:**
```kotlin
suspend fun uploadImage(
    context: Context,
    imageUri: Uri,
    folder: String = "chat-ui/generated-images",
    tags: List<String> = emptyList()
): UploadResult = withContext(Dispatchers.IO) {
    // Implementation is correct
}
```

#### ⚠️ **تحسينات مقترحة:**

##### **1. إضافة Progress Callback**

```kotlin
suspend fun uploadImage(
    context: Context,
    imageUri: Uri,
    folder: String = "chat-ui/generated-images",
    tags: List<String> = emptyList(),
    onProgress: ((Float) -> Unit)? = null  // NEW
): UploadResult = withContext(Dispatchers.IO) {
    // ... existing code ...
    
    // في upload callback:
    .callback(object : UploadCallback {
        override fun onStart(requestId: String) {
            onProgress?.invoke(0f)
        }
        
        override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {
            val progress = bytes.toFloat() / totalBytes.toFloat()
            onProgress?.invoke(progress)
        }
        
        override fun onSuccess(requestId: String, resultData: Map<*, *>) {
            onProgress?.invoke(1f)
            // ... existing code ...
        }
        
        override fun onError(requestId: String, error: ErrorInfo) {
            // ... existing code ...
        }
    })
}
```

##### **2. إضافة Retry Logic**

```kotlin
suspend fun uploadImageWithRetry(
    context: Context,
    imageUri: Uri,
    folder: String = "chat-ui/generated-images",
    tags: List<String> = emptyList(),
    maxRetries: Int = 3
): UploadResult {
    var lastException: Exception? = null
    
    repeat(maxRetries) { attempt ->
        try {
            return uploadImage(context, imageUri, folder, tags)
        } catch (e: Exception) {
            lastException = e
            if (attempt < maxRetries - 1) {
                delay(1000L * (attempt + 1))  // Exponential backoff
                Log.w(TAG, "Upload attempt ${attempt + 1} failed, retrying...")
            }
        }
    }
    
    throw lastException ?: Exception("Upload failed after $maxRetries attempts")
}
```

---

### 5.2 **فحص `FirestoreManager` - Generated Videos**

#### ❌ **المشكلة: لا توجد دالة `saveVideoMetadata()` في FirestoreManager!**

```kotlin
// ❌ في VeoVideoClient.kt:
FirestoreManager.firestore
    .collection("generated_videos")
    .document(result.id)
    .set(videoData)
    .await()

// ✅ يجب إضافة في FirestoreManager.kt:
suspend fun saveGeneratedVideo(video: GeneratedVideo): Boolean {
    return try {
        val userId = FirebaseManager.getCurrentUserId() ?: return false
        
        val data = mapOf(
            "id" to video.id,
            "userId" to userId,
            "prompt" to video.prompt,
            "url" to video.url,
            "visibility" to video.visibility.name,
            "duration" to video.duration,
            "aspectRatio" to video.aspectRatio,
            "createdAt" to video.createdAt,
            "jobId" to video.jobId
        )
        
        firestore
            .collection("generated_videos")
            .document(video.id)
            .set(data)
            .await()
        
        Log.i(TAG, "Video saved: ${video.id}")
        true
    } catch (e: Exception) {
        Log.e(TAG, "Failed to save video: ${e.message}", e)
        false
    }
}

fun getGeneratedVideosFlow(): Flow<List<GeneratedVideo>> = callbackFlow {
    val userId = FirebaseManager.getCurrentUserId()
    if (userId == null) {
        trySend(emptyList())
        close()
        return@callbackFlow
    }
    
    val listener = firestore
        .collection("generated_videos")
        .whereEqualTo("userId", userId)
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error listening to videos", error)
                trySend(emptyList())
                return@addSnapshotListener
            }
            
            val videos = snapshot?.documents?.mapNotNull { doc ->
                try {
                    val data = doc.data ?: return@mapNotNull null
                    GeneratedVideo(
                        id = data["id"] as? String ?: doc.id,
                        prompt = data["prompt"] as? String ?: "",
                        url = data["url"] as? String ?: "",
                        visibility = when (data["visibility"] as? String) {
                            "PUBLIC" -> VideoVisibility.PUBLIC
                            else -> VideoVisibility.PRIVATE
                        },
                        duration = (data["duration"] as? Number)?.toInt() ?: 0,
                        aspectRatio = data["aspectRatio"] as? String ?: "16:9",
                        createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
                        jobId = data["jobId"] as? String ?: ""
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse video doc ${doc.id}", e)
                    null
                }
            } ?: emptyList()
            
            trySend(videos)
        }
    
    awaitClose { listener.remove() }
}
```

---

## 🎨 **القسم 6: تجربة المستخدم (UX)**

### 6.1 **المشاكل الحرجة في UX**

#### ❌ **1. عدم وجود Loading States واضحة**

```kotlin
// ❌ المشكلة الحالية في ImageGenerationViewModel:
var isGenerating by mutableStateOf(false)

// في UI:
if (isGenerating) {
    CircularProgressIndicator()
}

// ✅ الحل: إضافة Loading States تفصيلية
enum class GenerationState {
    IDLE,
    PREPARING_REQUEST,
    CALLING_API,
    PROCESSING_RESPONSE,
    UPLOADING_TO_CLOUDINARY,
    SAVING_TO_FIRESTORE,
    COMPLETE,
    ERROR
}

var generationState by mutableStateOf(GenerationState.IDLE)
var generationProgress by mutableStateOf(0f)

// في UI:
when (generationState) {
    GenerationState.PREPARING_REQUEST -> {
        CircularProgressIndicator()
        Text("Preparing your request...")
    }
    GenerationState.CALLING_API -> {
        CircularProgressIndicator()
        Text("Generating image with AI...")
    }
    GenerationState.UPLOADING_TO_CLOUDINARY -> {
        LinearProgressIndicator(progress = generationProgress)
        Text("Uploading image... ${(generationProgress * 100).toInt()}%")
    }
    // ... etc
}
```

#### ❌ **2. رسائل الخطأ غير واضحة**

```kotlin
// ❌ المشكلة الحالية:
errorMessage = "HTTP 400: Bad Request"

// ✅ الحل: رسائل خطأ مفهومة للمستخدم
fun parseUserFriendlyError(error: String): String {
    return when {
        error.contains("400") -> "الطلب غير صحيح. تأكد من كتابة وصف واضح للصورة"
        error.contains("401") || error.contains("403") -> "خطأ في المصادقة. تحقق من إعدادات API"
        error.contains("429") -> "تم تجاوز الحد المسموح. حاول مرة أخرى بعد قليل"
        error.contains("500") -> "خطأ في الخادم. حاول مرة أخرى لاحقاً"
        error.contains("timeout") -> "انتهت مهلة الاتصال. تحقق من اتصال الإنترنت"
        else -> "حدث خطأ غير متوقع: ${error.take(100)}"
    }
}
```

#### ❌ **3. عدم حفظ حالة Input عند تدوير الشاشة**

```kotlin
// ❌ المشكلة الحالية:
var prompt by remember { mutableStateOf("") }

// ✅ الحل:
var prompt by rememberSaveable { mutableStateOf("") }
var selectedModel by rememberSaveable { mutableStateOf("google/imagen-4.0-generate-001") }
var numberOfImages by rememberSaveable { mutableStateOf(1) }
```

#### ❌ **4. عدم وجود Confirmation Dialogs**

```kotlin
// ✅ إضافة مطلوبة:
var showDeleteConfirmation by remember { mutableStateOf(false) }
var itemToDelete by remember { mutableStateOf<GeneratedImage?>(null) }

if (showDeleteConfirmation) {
    AlertDialog(
        onDismissRequest = { showDeleteConfirmation = false },
        title = { Text("تأكيد الحذف") },
        text = { Text("هل أنت متأكد من حذف هذه الصورة؟ لا يمكن التراجع عن هذا الإجراء.") },
        confirmButton = {
            Button(
                onClick = {
                    itemToDelete?.let { viewModel.deleteImage(it) }
                    showDeleteConfirmation = false
                }
            ) {
                Text("حذف")
            }
        },
        dismissButton = {
            TextButton(onClick = { showDeleteConfirmation = false }) {
                Text("إلغاء")
            }
        }
    )
}
```

#### ❌ **5. عدم وجود Empty States**

```kotlin
// ✅ إضافة مطلوبة:
if (!isLoading && images.isEmpty()) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "لا توجد صور بعد",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "ابدأ بإنشاء صورتك الأولى!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = { /* Navigate to generation */ }) {
                Text("إنشاء صورة")
            }
        }
    }
}
```

---

## 📋 **ملخص المشاكل والحلول**

### 🔴 **CRITICAL (يجب إصلاحها فوراً):**

| # | المشكلة | الملف | الحل |
|---|---------|-------|------|
| 1 | Video API لا يتوافق مع Google Veo الرسمي | `VeoVideoClient.kt` | إنشاء `VeoOfficialClient.kt` جديد |
| 2 | لا يوجد Image Gallery UI | - | إنشاء `ImageGalleryScreen.kt` + `ImageGalleryViewModel.kt` |
| 3 | استخدام API Key خاطئ في HuggingFace | `ImageGenerationApiClient.kt` | استخدام `getProviderConfig().apiKey` |
| 4 | عدم استخدام Base URL من ConfigManager | `ImageGenerationApiClient.kt` | استخدام `providerConfig.baseUrl` |

### 🟡 **IMPORTANT (يجب إصلاحها قريباً):**

| # | المشكلة | الملف | الحل |
|---|---------|-------|------|
| 5 | Vertex AI Image Generation غير مُطبّق | `ImageGenerationApiClient.kt` | تطبيق `generateWithVertexAI()` |
| 6 | منطق حذف الفيديوهات غير متسق | `VideoGalleryViewModel.kt` | إضافة معالجة YouTube deletion |
| 7 | عدم وجود Pagination للفيديوهات | `VideoGalleryViewModel.kt` | إضافة `loadMoreVideos()` |
| 8 | عدم وجود `saveGeneratedVideo()` في FirestoreManager | `FirestoreManager.kt` | إضافة الدالة |
| 9 | عدم وجود Error Recovery | `ImageGenerationViewModel.kt` | إضافة Retry mechanism |
| 10 | عدم وجود Progress Tracking | `ImageGenerationViewModel.kt` | إضافة `generationProgress` |

### 🟢 **REGULAR (تحسينات):**

| # | المشكلة | الملف | الحل |
|---|---------|-------|------|
| 11 | عدم وجود Loading States واضحة | جميع ViewModels | إضافة `GenerationState` enum |
| 12 | رسائل خطأ غير واضحة | جميع ViewModels | إضافة `parseUserFriendlyError()` |
| 13 | عدم حفظ Input عند تدوير الشاشة | جميع Screens | استخدام `rememberSaveable` |
| 14 | عدم وجود Confirmation Dialogs | Gallery Screens | إضافة `AlertDialog` |
| 15 | عدم وجود Empty States | Gallery Screens | إضافة Empty State UI |
| 16 | عدم وجود Progress Callback في Cloudinary | `CloudinaryManager.kt` | إضافة `onProgress` parameter |
| 17 | عدم وجود Retry Logic في Cloudinary | `CloudinaryManager.kt` | إضافة `uploadImageWithRetry()` |
| 18 | نقص معاملات Google AI Studio | `ImageGenerationApiClient.kt` | إضافة `imageSize`, `guidanceScale`, `seed` |

---

## 🎯 **خطة العمل الموصى بها**

### **المرحلة 1: الإصلاحات الحرجة (أولوية قصوى)**
1. ✅ إنشاء `VeoOfficialClient.kt` للتوافق مع Google Veo API
2. ✅ إنشاء Image Gallery UI كاملة
3. ✅ إصلاح استخدام API Keys في جميع الـ Clients
4. ✅ إصلاح استخدام Base URLs من ConfigManager

### **المرحلة 2: الإصلاحات المهمة**
5. ✅ تطبيق Vertex AI Image Generation
6. ✅ إصلاح منطق حذف الفيديوهات
7. ✅ إضافة Pagination للفيديوهات
8. ✅ إضافة دوال Firebase Manager الناقصة

### **المرحلة 3: تحسينات UX**
9. ✅ إضافة Loading States تفصيلية
10. ✅ تحسين رسائل الخطأ
11. ✅ إضافة Confirmation Dialogs
12. ✅ إضافة Empty States
13. ✅ إضافة Error Recovery

### **المرحلة 4: تحسينات الأداء**
14. ✅ إضافة Progress Callbacks
15. ✅ إضافة Retry Logic
16. ✅ تحسين Cloudinary Integration

---

## ✅ **الخلاصة**

### **النقاط الإيجابية:**
- ✅ البنية المعمارية جيدة بشكل عام
- ✅ استخدام Coroutines و Flow صحيح
- ✅ تكامل Cloudinary يعمل بشكل جيد
- ✅ Firebase Integration أساسي موجود

### **النقاط التي تحتاج تحسين:**
- ❌ Video API يحتاج إعادة كتابة كاملة
- ❌ Image Gallery غير موجود
- ❌ UX ضعيف (لا loading states, لا error recovery)
- ❌ عدم استخدام ConfigManager بشكل متسق

### **التقييم العام:**
**6/10** - يعمل لكن يحتاج تحسينات كبيرة في:
1. توافق APIs مع Google Standards
2. اكتمال الـ Features (Image Gallery)
3. تجربة المستخدم (UX)
4. معالجة الأخطاء (Error Handling)

---

**تم إعداد التقرير بواسطة:** AI Code Auditor  
**التاريخ:** 21 ديسمبر 2025

