package com.example.chat_ui.api

import android.util.Log
import com.example.chat_ui.config.ConfigManager
import com.example.chat_ui.data.ApiProvider
import com.example.chat_ui.data.GoogleModels
import com.example.chat_ui.utils.FirebaseAuthHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.HttpURLConnection
import java.net.URL

/**
 * API Client for fetching models from HuggingFace Router
 * Similar to src/lib/server/models.ts in Svelte
 */
object ModelsApiClient {
    private const val TAG = "ModelsApiClient"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Serializable
    data class Provider(
        val supports_tools: Boolean? = null
    )

    @Serializable
    data class Architecture(
        val input_modalities: List<String>? = null
    )

    @Serializable
    data class ModelData(
        val id: String,
        val description: String? = null,
        val providers: List<Provider>? = null,
        val architecture: Architecture? = null
    )

    @Serializable
    data class ModelsListResponse(
        val data: List<ModelData>
    )

    /**
     * Model type for categorization
     */
    enum class ModelType {
        LANGUAGE,
        IMAGE_GENERATION,
        VIDEO_GENERATION,
        EMBEDDING
    }

    /**
     * Fetched model with additional metadata
     */
    data class FetchedModel(
        val id: String,
        val name: String,
        val displayName: String,
        val description: String?,
        val logoUrl: String?,
        val multimodal: Boolean,
        val supportsTools: Boolean,
        val modelType: ModelType = ModelType.LANGUAGE
    )

    private enum class GoogleFamily {
        GEMINI_CHAT,
        GEMINI_IMAGE,
        IMAGEN,
        VEO,
        EMBEDDING,
        OTHER
    }

    private data class SortKey(
        val familyRank: Int,
        val major: Int,
        val minor: Int,
        val patch: Int,
        val stabilityRank: Int, // lower is better: preview/exp before stable; latest last
        val tierRank: Int,      // lower is better: pro before flash before lite
        val variantRank: Int,   // lower is better
        val numericSuffix: Int, // higher is better
        val raw: String
    )

    /**
     * Fetch all models from HuggingFace Router API, Google Vertex AI, or Google AI Studio
     */
    suspend fun fetchModels(): Result<List<FetchedModel>> = withContext(Dispatchers.IO) {
        try {
            val providerConfig = ConfigManager.getProviderConfig()

            if (providerConfig.provider == ApiProvider.GOOGLE_AI_STUDIO) {
                Log.i(TAG, "Fetching Google AI Studio models from API...")
                return@withContext fetchGoogleAIStudioModelsFromAPI()
            }

            // HuggingFace
            val baseUrl = providerConfig.baseUrl.trimEnd('/')
            val isHfRouter = baseUrl == "https://router.huggingface.co/v1"
            val firebaseToken = FirebaseAuthHelper.getFirebaseIdToken(forceRefresh = false)

            Log.i(TAG, "Fetching models from: $baseUrl/models")

            if (firebaseToken == null) {
                return@withContext Result.failure(Exception("Please sign in before fetching models"))
            }

            val url = URL("$baseUrl/models")
            val connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $firebaseToken")
                connectTimeout = 30000
                readTimeout = 30000
            }

            val responseCode = connection.responseCode
            Log.i(TAG, "Response code: $responseCode")

            if (responseCode != HttpURLConnection.HTTP_OK) {
                val error = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                Log.e(TAG, "Error fetching models: $error")
                return@withContext Result.failure(Exception("HTTP $responseCode: $error"))
            }

            val responseBody = connection.inputStream.bufferedReader().readText()
            val parsed = json.decodeFromString<ModelsListResponse>(responseBody)

            Log.i(TAG, "Fetched ${parsed.data.size} models")

            val models = parsed.data.map { model ->
                val inputModalities = model.architecture?.input_modalities?.map { it.lowercase() } ?: emptyList()
                val supportsImage = inputModalities.contains("image") || inputModalities.contains("vision")
                val supportsTools = model.providers?.any { it.supports_tools == true } ?: false

                val logoUrl = if (isHfRouter && model.id.contains("/")) {
                    val org = model.id.split("/")[0]
                    "https://huggingface.co/api/avatars/${java.net.URLEncoder.encode(org, "UTF-8")}"
                } else null

                FetchedModel(
                    id = model.id,
                    name = model.id,
                    displayName = model.id,
                    description = model.description,
                    logoUrl = logoUrl,
                    multimodal = supportsImage,
                    supportsTools = supportsTools
                )
            }

            Result.success(models)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching models", e)
            Result.failure(e)
        }
    }

    /**
     * Create Omni Router virtual model
     */
    fun createOmniRouterModel(): FetchedModel {
        val displayName = ConfigManager.get(ConfigManager.Keys.PUBLIC_LLM_ROUTER_DISPLAY_NAME, "Omni")
        val aliasId = ConfigManager.get(ConfigManager.Keys.PUBLIC_LLM_ROUTER_ALIAS_ID, "omni")
        val isMultimodal = ConfigManager.isMultimodalEnabled

        return FetchedModel(
            id = aliasId,
            name = aliasId,
            displayName = displayName,
            description = "Automatically routes your messages to the best model for your request.",
            logoUrl = null,
            multimodal = isMultimodal,
            supportsTools = true
        )
    }

    /**
     * Vertex: Fetch Google models from Vertex AI API dynamically
     */
    private suspend fun fetchGoogleModelsFromAPI(): Result<List<FetchedModel>> = withContext(Dispatchers.IO) {
        try {
            val providerConfig = ConfigManager.getProviderConfig()
            val projectId = providerConfig.baseUrl.substringAfter("projects/").substringBefore("/")
            val location = providerConfig.baseUrl.substringAfter("locations/").substringBefore("/")

            val token = com.google.firebase.auth.FirebaseAuth.getInstance()
                .currentUser?.getIdToken(false)?.await()?.token

            if (token == null) {
                Log.e(TAG, "No Firebase Auth token available")
                return@withContext Result.failure(Exception("No authentication token"))
            }

            val apiUrl = "https://$location-aiplatform.googleapis.com/v1beta1/publishers/google/models?pageSize=200"
            Log.i(TAG, "Fetching Google models from: $apiUrl")

            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("x-goog-user-project", projectId)
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 30000
                readTimeout = 30000
            }

            val responseCode = connection.responseCode
            Log.i(TAG, "Google API Response code: $responseCode")

            if (responseCode != HttpURLConnection.HTTP_OK) {
                val error = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                Log.e(TAG, "Error fetching Google models: $error")
                return@withContext Result.success(
                    GoogleModels.AVAILABLE_MODELS.map { model ->
                        FetchedModel(
                            id = model.id,
                            name = model.id.substringAfter("/"),
                            displayName = model.displayName,
                            description = model.description,
                            logoUrl = "https://www.gstatic.com/lamda/images/gemini_sparkle_v002_d4735304ff6292a690345.svg",
                            multimodal = model.multimodal,
                            supportsTools = model.supportsTools
                        )
                    }.sortedWith(compareByDescending<FetchedModel> { googleSortKey(it.name, it.displayName, emptySet()).major }
                        .thenByDescending { googleSortKey(it.name, it.displayName, emptySet()).minor }
                        .thenByDescending { googleSortKey(it.name, it.displayName, emptySet()).patch }
                    )
                )
            }

            val responseBody = connection.inputStream.bufferedReader().readText()
            val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
            val publisherModels = jsonResponse["publisherModels"]?.jsonArray ?: emptyList()

            val models = publisherModels.mapNotNull { element ->
                val obj = element.jsonObject
                val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val modelId = name.substringAfter("publishers/google/models/")

                // Keep Gemini + related; do not hard-drop preview/exp here
                val displayName = modelId
                FetchedModel(
                    id = "google/$modelId",
                    name = modelId,
                    displayName = displayName,
                    description = "Google model",
                    logoUrl = "https://www.gstatic.com/lamda/images/gemini_sparkle_v002_d4735304ff6292a690345.svg",
                    multimodal = true,
                    supportsTools = true
                )
            }

            val sorted = models
                .distinctBy { it.id }
                .sortedWith { a, b ->
                    val ka = googleSortKey(a.name, a.displayName, emptySet())
                    val kb = googleSortKey(b.name, b.displayName, emptySet())
                    compareGoogleKeys(ka, kb)
                }

            Log.i(TAG, "Fetched ${sorted.size} models from Vertex API")
            Result.success(sorted)

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Google models from API", e)
            Result.success(
                GoogleModels.AVAILABLE_MODELS.map { model ->
                    FetchedModel(
                        id = model.id,
                        name = model.id.substringAfter("/"),
                        displayName = model.displayName,
                        description = model.description,
                        logoUrl = "https://www.gstatic.com/lamda/images/gemini_sparkle_v002_d4735304ff6292a690345.svg",
                        multimodal = model.multimodal,
                        supportsTools = model.supportsTools
                    )
                }.sortedWith { a, b ->
                    val ka = googleSortKey(a.name, a.displayName, emptySet())
                    val kb = googleSortKey(b.name, b.displayName, emptySet())
                    compareGoogleKeys(ka, kb)
                }
            )
        }
    }

    /**
     * AI Studio: Fetch models via models.list (pagination) and sort logically (newest first)
     */
    private suspend fun fetchGoogleAIStudioModelsFromAPI(): Result<List<FetchedModel>> = withContext(Dispatchers.IO) {
        try {
            val providerConfig = ConfigManager.getProviderConfig()
            val baseUrl = providerConfig.baseUrl.trimEnd('/')
            val apiKey = providerConfig.apiKey
            val usesBackendGoogle = baseUrl.contains("/v1/google")
            val firebaseToken = if (usesBackendGoogle) {
                FirebaseAuthHelper.getFirebaseIdToken(forceRefresh = false)
            } else null

            if (!usesBackendGoogle && apiKey.isBlank()) {
                Log.e(TAG, "Google AI Studio API Key is empty")
                return@withContext Result.failure(Exception("Google AI Studio API Key is empty"))
            }
            if (usesBackendGoogle && firebaseToken == null) {
                return@withContext Result.failure(Exception("Please sign in before fetching Google models"))
            }

            val allModels = mutableListOf<FetchedModel>()
            var pageToken: String? = null

            do {
                val urlStrNoKey = buildString {
                    append("$baseUrl/models")
                    append("?pageSize=200")
                    if (!pageToken.isNullOrBlank()) {
                        append("&pageToken=")
                        append(java.net.URLEncoder.encode(pageToken!!, "UTF-8"))
                    }
                }

                val urlStrWithKey = buildString {
                    append("$baseUrl/models?key=")
                    append(java.net.URLEncoder.encode(apiKey, "UTF-8"))
                    append("&pageSize=200")
                    if (!pageToken.isNullOrBlank()) {
                        append("&pageToken=")
                        append(java.net.URLEncoder.encode(pageToken!!, "UTF-8"))
                    }
                }

                fun open(urlStr: String): HttpURLConnection =
                    (URL(urlStr).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        setRequestProperty("Accept", "application/json")
                        if (usesBackendGoogle) {
                            setRequestProperty("Authorization", "Bearer $firebaseToken")
                        } else {
                            // Prefer header key to avoid leaking the key in logs/URLs
                            setRequestProperty("x-goog-api-key", apiKey)
                        }
                        connectTimeout = 20000
                        readTimeout = 30000
                    }

                var connection = open(urlStrNoKey)
                var responseCode = connection.responseCode
                var body = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()
                    ?.readText()
                    .orEmpty()

                if (!usesBackendGoogle && responseCode !in 200..299) {
                    Log.w(TAG, "Google AI Studio models.list failed without key in URL, retrying with ?key=. HTTP $responseCode - $body")
                    connection.disconnect()

                    connection = open(urlStrWithKey)
                    responseCode = connection.responseCode
                    body = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                        ?.bufferedReader()
                        ?.readText()
                        .orEmpty()
                }

                if (responseCode !in 200..299) {
                    Log.e(TAG, "Google AI Studio models.list failed: HTTP $responseCode - $body")
                    return@withContext Result.failure(Exception("Google AI Studio models.list failed: HTTP $responseCode"))
                }

                val root = json.parseToJsonElement(body).jsonObject
                val models = root["models"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())

                models.forEach { element ->
                    val obj = element.jsonObject
                    val nameRaw = obj["name"]?.jsonPrimitive?.content ?: return@forEach
                    val modelId = nameRaw.removePrefix("models/")
                    val displayName = obj["displayName"]?.jsonPrimitive?.content ?: modelId
                    val description = obj["description"]?.jsonPrimitive?.contentOrNull

                    val methods = obj["supportedGenerationMethods"]?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                        ?.toSet()
                        ?: emptySet()

                    val supportsAnythingUseful = methods.contains("generateContent") ||
                            methods.contains("streamGenerateContent") ||
                            methods.contains("bidiGenerateContent") ||
                            methods.contains("predict") ||
                            methods.contains("predictLongRunning") ||
                            methods.any { it.startsWith("embed") } ||
                            methods.contains("embedContent") ||
                            methods.contains("embedText")

                    if (!supportsAnythingUseful) return@forEach

                    val modelType = when {
                        methods.contains("predictLongRunning") -> ModelType.VIDEO_GENERATION
                        methods.contains("predict") -> ModelType.IMAGE_GENERATION
                        methods.any { it.startsWith("embed") } || methods.contains("embedContent") || methods.contains("embedText") -> ModelType.EMBEDDING
                        else -> ModelType.LANGUAGE
                    }

                    allModels.add(
                        FetchedModel(
                            id = "google/$modelId",
                            name = modelId,
                            displayName = displayName,
                            description = description,
                            logoUrl = "https://www.gstatic.com/lamda/images/gemini_sparkle_v002_d4735304ff6292a690345.svg",
                            multimodal = true,
                            supportsTools = true,
                            modelType = modelType
                        )
                    )
                }

                pageToken = root["nextPageToken"]?.jsonPrimitive?.contentOrNull
            } while (!pageToken.isNullOrBlank())

            val distinct = allModels.distinctBy { it.id }
            val sorted = distinct.sortedWith { a, b ->
                val ka = googleSortKey(a.name, a.displayName, emptySet())
                val kb = googleSortKey(b.name, b.displayName, emptySet())
                compareGoogleKeys(ka, kb)
            }

            Log.i(TAG, "Fetched ${sorted.size} models from Google AI Studio API")
            Result.success(sorted)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Google AI Studio models from API", e)
            Result.failure(e)
        }
    }

    private fun compareGoogleKeys(a: SortKey, b: SortKey): Int {
        // We want newest first, best tier first.
        // familyRank asc (Gemini chat first), version desc, stabilityRank asc, tierRank asc, variantRank asc, numericSuffix desc, raw asc
        if (a.familyRank != b.familyRank) return a.familyRank.compareTo(b.familyRank)

        if (a.major != b.major) return b.major.compareTo(a.major)
        if (a.minor != b.minor) return b.minor.compareTo(a.minor)
        if (a.patch != b.patch) return b.patch.compareTo(a.patch)

        if (a.stabilityRank != b.stabilityRank) return a.stabilityRank.compareTo(b.stabilityRank)
        if (a.tierRank != b.tierRank) return a.tierRank.compareTo(b.tierRank)
        if (a.variantRank != b.variantRank) return a.variantRank.compareTo(b.variantRank)

        if (a.numericSuffix != b.numericSuffix) return b.numericSuffix.compareTo(a.numericSuffix)

        return a.raw.compareTo(b.raw)
    }

    private fun googleSortKey(modelId: String, displayName: String, methods: Set<String>): SortKey {
        val id = modelId.trim()

        // Force your requested first item to the very top.
        if (id == "gemini-3-pro-preview") {
            return SortKey(
                familyRank = 0,
                major = 3, minor = 0, patch = 0,
                stabilityRank = 0,
                tierRank = 0,
                variantRank = 0,
                numericSuffix = Int.MAX_VALUE,
                raw = id
            )
        }

        val (family, famRank) = detectFamily(id, displayName, methods)

        val (major, minor, patch) = parseVersion(id, family)
        val stabilityRank = stabilityRank(id)
        val tierRank = tierRank(id, family)
        val variantRank = variantRank(id, family)
        val numericSuffix = parseTrailingNumber(id)

        return SortKey(
            familyRank = famRank,
            major = major,
            minor = minor,
            patch = patch,
            stabilityRank = stabilityRank,
            tierRank = tierRank,
            variantRank = variantRank,
            numericSuffix = numericSuffix,
            raw = id
        )
    }

    private fun detectFamily(id: String, displayName: String, methods: Set<String>): Pair<GoogleFamily, Int> {
        val lower = id.lowercase()
        val dn = displayName.lowercase()

        fun hasAny(vararg xs: String): Boolean = xs.any { lower.contains(it) || dn.contains(it) }

        val isEmbedding = hasAny("embedding", "text-embedding") || methods.any { it.startsWith("embed") } || methods.contains("embedContent") || methods.contains("embedText")
        if (isEmbedding) return GoogleFamily.EMBEDDING to 4

        val isVeo = lower.startsWith("veo-") || hasAny("veo")
        if (isVeo) return GoogleFamily.VEO to 3

        val isImagen = lower.startsWith("imagen-") || hasAny("imagen")
        if (isImagen) return GoogleFamily.IMAGEN to 2

        val isNanoBanana = hasAny("nano-banana")
        if (isNanoBanana) return GoogleFamily.GEMINI_IMAGE to 1

        val isGemini = lower.startsWith("gemini-") || hasAny("gemini")
        if (isGemini) {
            val isImageGemini = hasAny("image") || lower.contains("-image")
            return if (isImageGemini) GoogleFamily.GEMINI_IMAGE to 1 else GoogleFamily.GEMINI_CHAT to 0
        }

        return GoogleFamily.OTHER to 5
    }

    private fun stabilityRank(id: String): Int {
        val lower = id.lowercase()
        return when {
            lower.contains("preview") -> 0
            lower.contains("-exp") || lower.contains("experimental") -> 1
            lower.contains("latest") -> 3
            else -> 2
        }
    }

    private fun tierRank(id: String, family: GoogleFamily): Int {
        val lower = id.lowercase()
        if (family == GoogleFamily.GEMINI_IMAGE || lower.contains("nano-banana")) return 0

        return when {
            lower.contains("-pro") -> 0
            lower.contains("-flash") && !lower.contains("lite") -> 1
            lower.contains("flash-lite") || lower.contains("-lite") -> 2
            else -> 3
        }
    }

    private fun variantRank(id: String, family: GoogleFamily): Int {
        val lower = id.lowercase()
        if (family == GoogleFamily.GEMINI_IMAGE) {
            return when {
                lower.contains("pro") -> 0
                else -> 1
            }
        }

        // Prefer base models, then specialized variants (audio/tts/computer-use/etc.)
        return when {
            lower.contains("computer-use") -> 3
            lower.contains("native-audio") -> 4
            lower.contains("tts") -> 5
            lower.contains("image") -> 6
            else -> 0
        }
    }

    private fun parseTrailingNumber(id: String): Int {
        val m = Regex("""-(\d{3,4})$""").find(id) ?: return 0
        return m.groupValues[1].toIntOrNull() ?: 0
    }

    private fun parseVersion(id: String, family: GoogleFamily): Triple<Int, Int, Int> {
        val lower = id.lowercase()

        // Special-case for Nano Banana: map to Gemini 3.0 family so it sorts near Gemini 3
        if (lower.contains("nano-banana")) {
            return Triple(3, 0, 0)
        }

        val prefix = when (family) {
            GoogleFamily.GEMINI_CHAT, GoogleFamily.GEMINI_IMAGE -> "gemini-"
            GoogleFamily.IMAGEN -> "imagen-"
            GoogleFamily.VEO -> "veo-"
            GoogleFamily.EMBEDDING -> "embedding-"
            else -> null
        }

        if (prefix != null && lower.startsWith(prefix)) {
            val after = lower.removePrefix(prefix)
            // after could be like: 3-pro-preview, 2.5-flash, 3.1-generate-preview, 4.0-fast-generate-001
            val ver = after.takeWhile { it.isDigit() || it == '.' }
            if (ver.isNotBlank()) {
                val parts = ver.split('.')
                val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
                val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
                val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
                return Triple(major, minor, patch)
            }
        }

        // Fallbacks for aliases (gemini-pro-latest, etc.)
        return Triple(0, 0, 0)
    }

    /**
     * Get all models including Omni Router
     */
    suspend fun getAllModels(): List<FetchedModel> {
        val providerConfig = ConfigManager.getProviderConfig()
        val archBaseUrl = ConfigManager.get(ConfigManager.Keys.LLM_ROUTER_ARCH_BASE_URL, "")

        val fetchedModels = fetchModels().getOrDefault(emptyList())

        return if (providerConfig.provider == ApiProvider.HUGGINGFACE && archBaseUrl.isNotBlank()) {
            listOf(createOmniRouterModel()) + fetchedModels
        } else {
            fetchedModels
        }
    }
}
