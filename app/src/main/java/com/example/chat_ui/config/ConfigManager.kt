package com.example.chat_ui.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.chat_ui.data.ApiProvider
import com.example.chat_ui.data.ProviderConfig
import com.example.chat_ui.data.GoogleGeminiConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.util.Properties

/**
 * Configuration Manager - Similar to Svelte's config.ts
 *
 * Priority order:
 * 1. SharedPreferences (user settings from app)
 * 2. Local overrides (local.properties from assets)
 * 3. Default config (config.properties from assets)
 */
object ConfigManager {
    private const val TAG = "ConfigManager"
    private const val LEGACY_PREFS_NAME = "chat_ui_config"
    private const val ENCRYPTED_PREFS_NAME = "chat_ui_config_secure"

    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var encryptedPrefs: SharedPreferences
    private val defaultConfig = Properties()
    private val localConfig = Properties()
    private var isInitialized = false
    
    // Application context for API calls (safe to store as it's application-scoped)
    private lateinit var appContext: Context
    
    // JSON serializer for complex objects
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    /**
     * Get application context (for API calls like VertexAI)
     */
    fun getAppContext(): Context? = if (::appContext.isInitialized) appContext else null

    // Configuration Keys
    object Keys {
        // API Provider
        const val API_PROVIDER = "API_PROVIDER"
        
        // API Configuration
        const val OPENAI_BASE_URL = "OPENAI_BASE_URL"
        const val OPENAI_API_KEY = "OPENAI_API_KEY"
        const val GOOGLE_STUDIO_API_KEY = "GOOGLE_STUDIO_API_KEY"
        const val USE_GOOGLE_AUTH = "USE_GOOGLE_AUTH"

        // App Configuration
        const val PUBLIC_APP_NAME = "PUBLIC_APP_NAME"
        const val PUBLIC_APP_DESCRIPTION = "PUBLIC_APP_DESCRIPTION"

        // Model Configuration
        const val DEFAULT_MODEL = "DEFAULT_MODEL"
        const val TASK_MODEL = "TASK_MODEL"

        // LLM Router
        const val LLM_ROUTER_ARCH_BASE_URL = "LLM_ROUTER_ARCH_BASE_URL"
        const val LLM_ROUTER_ARCH_MODEL = "LLM_ROUTER_ARCH_MODEL"
        const val LLM_ROUTER_FALLBACK_MODEL = "LLM_ROUTER_FALLBACK_MODEL"
        const val LLM_ROUTER_OTHER_ROUTE = "LLM_ROUTER_OTHER_ROUTE"
        const val LLM_ROUTER_ARCH_TIMEOUT_MS = "LLM_ROUTER_ARCH_TIMEOUT_MS"
        const val LLM_ROUTER_MAX_ASSISTANT_LENGTH = "LLM_ROUTER_MAX_ASSISTANT_LENGTH"
        const val LLM_ROUTER_MAX_PREV_USER_LENGTH = "LLM_ROUTER_MAX_PREV_USER_LENGTH"
        const val LLM_ROUTER_ENABLE_MULTIMODAL = "LLM_ROUTER_ENABLE_MULTIMODAL"
        const val LLM_ROUTER_ENABLE_TOOLS = "LLM_ROUTER_ENABLE_TOOLS"

        // Router UI
        const val PUBLIC_LLM_ROUTER_DISPLAY_NAME = "PUBLIC_LLM_ROUTER_DISPLAY_NAME"
        const val PUBLIC_LLM_ROUTER_ALIAS_ID = "PUBLIC_LLM_ROUTER_ALIAS_ID"

        // Feature Flags
        const val LLM_SUMMARIZATION = "LLM_SUMMARIZATION"
        const val ENABLE_DARK_MODE = "ENABLE_DARK_MODE"

        // Rate Limits
        const val MESSAGES_PER_MINUTE = "MESSAGES_PER_MINUTE"
        const val MAX_MESSAGE_LENGTH = "MAX_MESSAGE_LENGTH"

        // Cloudinary Configuration
        const val CLOUDINARY_CLOUD_NAME = "CLOUDINARY_CLOUD_NAME"
        const val CLOUDINARY_API_KEY = "CLOUDINARY_API_KEY"
        const val CLOUDINARY_API_SECRET = "CLOUDINARY_API_SECRET"
        const val CLOUDINARY_UPLOAD_FOLDER = "CLOUDINARY_UPLOAD_FOLDER"


        // Video Feature Flags
        const val VIDEO_PUBLIC_UPLOAD_ENABLED = "VIDEO_PUBLIC_UPLOAD_ENABLED"
        const val VIDEO_PRIVATE_UPLOAD_ENABLED = "VIDEO_PRIVATE_UPLOAD_ENABLED"
        const val VIDEO_IMAGE_TO_VIDEO_ENABLED = "VIDEO_IMAGE_TO_VIDEO_ENABLED"
        const val VIDEO_VIDEO_TO_VIDEO_ENABLED = "VIDEO_VIDEO_TO_VIDEO_ENABLED"
        
        // Google Gemini Configuration
        const val GOOGLE_GEMINI_CONFIG = "GOOGLE_GEMINI_CONFIG"
    }

    /** Initialize the ConfigManager with application context */
    fun init(context: Context) {
        if (isInitialized) return
        
        // Store application context for later use (safe - application context doesn't leak)
        appContext = context.applicationContext

        sharedPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)

        encryptedPrefs = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create encrypted preferences, using standard", e)
            sharedPrefs
        }

        migrateSensitiveKeysIfNeeded()

        // Load default config from assets
        try {
            context.assets.open("config.properties").use { stream -> 
                defaultConfig.load(stream) 
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load config.properties", e)
        }

        // Load local overrides from assets (if exists)
        try {
            context.assets.open("local.properties").use { stream -> 
                localConfig.load(stream) 
            }
        } catch (e: Exception) {
            // local.properties is optional
        }

        isInitialized = true
    }

    private fun isSensitiveKey(key: String): Boolean {
        return key == Keys.OPENAI_API_KEY || 
               key == Keys.GOOGLE_STUDIO_API_KEY || 
               key == Keys.CLOUDINARY_API_SECRET
    }

    private fun migrateSensitiveKeysIfNeeded() {
        if (!::sharedPrefs.isInitialized) return
        if (!::encryptedPrefs.isInitialized) return
        if (sharedPrefs === encryptedPrefs) return

        val sensitiveKeys = listOf(
            Keys.OPENAI_API_KEY, 
            Keys.GOOGLE_STUDIO_API_KEY, 
            Keys.CLOUDINARY_API_SECRET
        )
        
        sensitiveKeys.forEach { key ->
            if (!encryptedPrefs.contains(key) && sharedPrefs.contains(key)) {
                val legacyValue = sharedPrefs.getString(key, null)
                if (legacyValue != null) {
                    encryptedPrefs.edit().putString(key, legacyValue).apply()
                    sharedPrefs.edit().remove(key).apply()
                }
            }
        }
    }

    /**
     * Get configuration value with priority:
     * 1. SharedPreferences (user settings)
     * 2. Local overrides
     * 3. Default config
     */
    fun get(key: String, default: String = ""): String {
        val prefs = if (isSensitiveKey(key)) encryptedPrefs else sharedPrefs
        if (prefs.contains(key)) {
            return prefs.getString(key, default) ?: default
        }

        // Priority 2: Local overrides
        if (localConfig.containsKey(key)) {
            return localConfig.getProperty(key, default)
        }

        // Priority 3: Default config
        return defaultConfig.getProperty(key, default)
    }

    /** Get boolean configuration value */
    fun getBoolean(key: String, default: Boolean = false): Boolean {
        val value = get(key, default.toString())
        return value.equals("true", ignoreCase = true)
    }

    /** Get integer configuration value */
    fun getInt(key: String, default: Int = 0): Int {
        return try {
            get(key, default.toString()).toInt()
        } catch (e: NumberFormatException) {
            default
        }
    }

    /** Set configuration value (saved to SharedPreferences) */
    fun set(key: String, value: String) {
        val prefs = if (isSensitiveKey(key)) encryptedPrefs else sharedPrefs
        prefs.edit().putString(key, value).apply()
        if (prefs !== sharedPrefs && sharedPrefs.contains(key)) {
            sharedPrefs.edit().remove(key).apply()
        }
    }

    /** Set boolean configuration value */
    fun setBoolean(key: String, value: Boolean) {
        set(key, value.toString())
    }

    /** Set integer configuration value */
    fun setInt(key: String, value: Int) {
        set(key, value.toString())
    }

    /**
     * Remove configuration value from SharedPreferences
     */
    fun remove(key: String) {
        sharedPrefs.edit().remove(key).apply()
        if (::encryptedPrefs.isInitialized && encryptedPrefs !== sharedPrefs) {
            encryptedPrefs.edit().remove(key).apply()
        }
    }

    /** Check if a configuration key has a user-set value */
    fun hasUserValue(key: String): Boolean {
        if (sharedPrefs.contains(key)) return true
        if (::encryptedPrefs.isInitialized && encryptedPrefs !== sharedPrefs) {
            return encryptedPrefs.contains(key)
        }
        return false
    }

    /** Get all public configuration (keys starting with PUBLIC_) */
    fun getPublicConfig(): Map<String, String> {
        val config = mutableMapOf<String, String>()

        // Add defaults
        defaultConfig.stringPropertyNames().filter { it.startsWith("PUBLIC_") }.forEach { key ->
            config[key] = defaultConfig.getProperty(key, "")
        }

        // Override with local
        localConfig.stringPropertyNames().filter { it.startsWith("PUBLIC_") }.forEach { key ->
            config[key] = localConfig.getProperty(key, "")
        }

        // Override with user settings
        sharedPrefs.all.filter { it.key.startsWith("PUBLIC_") }.forEach { (key, value) ->
            config[key] = value?.toString() ?: ""
        }

        return config
    }

    // Convenience properties
    val openAiBaseUrl: String
        get() = get(Keys.OPENAI_BASE_URL, "https://router.huggingface.co/v1")

    val openAiApiKey: String
        get() = get(Keys.OPENAI_API_KEY, "")

    val googleStudioApiKey: String
        get() = get(Keys.GOOGLE_STUDIO_API_KEY, "")

    val appName: String
        get() = get(Keys.PUBLIC_APP_NAME, "Chat UI")

    val defaultModel: String
        get() = get(Keys.DEFAULT_MODEL, "gpt-4")

    val isDarkModeEnabled: Boolean
        get() = getBoolean(Keys.ENABLE_DARK_MODE, true)

    val isMultimodalEnabled: Boolean
        get() = getBoolean(Keys.LLM_ROUTER_ENABLE_MULTIMODAL, true)

    val messagesPerMinute: Int
        get() = getInt(Keys.MESSAGES_PER_MINUTE, 60)

    val maxMessageLength: Int
        get() = getInt(Keys.MAX_MESSAGE_LENGTH, 4096)

    // Cloudinary convenience properties
    val cloudinaryCloudName: String
        get() = get(Keys.CLOUDINARY_CLOUD_NAME, "")

    val cloudinaryApiKey: String
        get() = get(Keys.CLOUDINARY_API_KEY, "")

    val cloudinaryApiSecret: String
        get() = get(Keys.CLOUDINARY_API_SECRET, "")

    val cloudinaryUploadFolder: String
        get() = get(Keys.CLOUDINARY_UPLOAD_FOLDER, "chat-ui/kotlin")
    
    // Video Feature Flags convenience properties
    val isVideoPublicUploadEnabled: Boolean
        get() = getBoolean(Keys.VIDEO_PUBLIC_UPLOAD_ENABLED, true)
    
    val isVideoPrivateUploadEnabled: Boolean
        get() = getBoolean(Keys.VIDEO_PRIVATE_UPLOAD_ENABLED, true)
    
    val isImageToVideoEnabled: Boolean
        get() = getBoolean(Keys.VIDEO_IMAGE_TO_VIDEO_ENABLED, true)
    
    val isVideoToVideoEnabled: Boolean
        get() = getBoolean(Keys.VIDEO_VIDEO_TO_VIDEO_ENABLED, true)
    
    // Public getter for Veo backend base URL deleted

        
    /**
     * Get the API Key associated with a specific provider from storage.
     */
    fun getApiKeyForProvider(provider: ApiProvider): String {
        val key = when (provider) {
            ApiProvider.GOOGLE_AI_STUDIO -> Keys.GOOGLE_STUDIO_API_KEY
            // HuggingFace's key is stored in the general OPENAI_API_KEY
            ApiProvider.HUGGINGFACE -> Keys.OPENAI_API_KEY
        }
        return get(key, "")
    }

    /**
     * Get the base URL that would be used for a given provider based on persistence logic.
     * Note: This function respects the global OPENAI_BASE_URL override if present.
     */
    fun getBaseUrlForProvider(provider: ApiProvider): String {
        val baseOverride = get(Keys.OPENAI_BASE_URL, "")
        // If there's a global override, use it, otherwise use the provider's default URL
        return if (baseOverride.isNotBlank()) baseOverride else provider.defaultBaseUrl
    }
    
    /**
     * Get the default model name associated with a specific provider from storage.
     * Since DEFAULT_MODEL is a single key, we fetch the saved value, or the provider's default model hint.
     */
    fun getDefaultModelForProvider(provider: ApiProvider): String {
        val globalDefault = get(Keys.DEFAULT_MODEL, "")
        if (globalDefault.isNotBlank()) {
            return globalDefault
        }
        
        return when(provider) {
            ApiProvider.HUGGINGFACE -> "omni"
            ApiProvider.GOOGLE_AI_STUDIO -> "gemini-2.5-flash"
        }
    }


    /**
     * Build ProviderConfig from persisted settings
     */
    fun getProviderConfig(): ProviderConfig {
        val providerStr = get(Keys.API_PROVIDER, ApiProvider.HUGGINGFACE.name)
        val provider = ApiProvider.fromString(providerStr)

        val baseUrl = getBaseUrlForProvider(provider)
        val apiKey = getApiKeyForProvider(provider)
        
        val useGoogleAuth = getBoolean(Keys.USE_GOOGLE_AUTH, false)

        return ProviderConfig(
            provider = provider,
            baseUrl = baseUrl,
            apiKey = apiKey,
            useGoogleAuth = useGoogleAuth
        )
    }
    
    /**
     * Save provider configuration
     */
    fun saveProviderConfig(config: ProviderConfig) {
        set(Keys.API_PROVIDER, config.provider.name)
        // OPENAI_BASE_URL is a global override field, saved regardless of provider
        set(Keys.OPENAI_BASE_URL, config.baseUrl) 
        
        if (config.provider == ApiProvider.GOOGLE_AI_STUDIO) {
            set(Keys.GOOGLE_STUDIO_API_KEY, config.apiKey)
            // Clear the HuggingFace field when switching to Google, to keep things clean
            remove(Keys.OPENAI_API_KEY)
        } else {
            set(Keys.OPENAI_API_KEY, config.apiKey)
            // Clear the Google key field when switching away
            remove(Keys.GOOGLE_STUDIO_API_KEY)
        }
        setBoolean(Keys.USE_GOOGLE_AUTH, config.useGoogleAuth)
    }

    // ========== NEW: Google AI Studio API Key from config.properties ==========

    /**
     * Get Google AI Studio API Key from config.properties file
     * This reads directly from assets/config.properties
     */
    fun getGoogleStudioApiKeyFromFile(context: Context): String {
        return try {
            val properties = Properties()
            context.assets.open("config.properties").use { inputStream ->
                properties.load(inputStream)
            }
            val key = properties.getProperty("GOOGLE_STUDIO_API_KEY", "")
            if (key.isNotBlank()) {
                Log.i(TAG, "Successfully loaded GOOGLE_STUDIO_API_KEY from config.properties (length: ${key.length})")
            } else {
                Log.w(TAG, "GOOGLE_STUDIO_API_KEY not found or empty in config.properties")
            }
            key
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read GOOGLE_STUDIO_API_KEY from config.properties", e)
            ""
        }
    }
    
    /**
     * Check if Google AI Studio API key is configured in config.properties
     */
    fun hasGoogleStudioApiKeyInFile(context: Context): Boolean {
        return getGoogleStudioApiKeyFromFile(context).isNotBlank()
    }
    
    /**
     * Get provider config with API key automatically loaded from config.properties if needed
     * 
     * Priority:
     * 1. User-set API key (SharedPreferences)
     * 2. config.properties file
     */
    fun getProviderConfigWithApiKey(context: Context): ProviderConfig {
        val baseConfig = getProviderConfig()
        
        // If using Google AI Studio and no API key is set by user
        if (baseConfig.provider == ApiProvider.GOOGLE_AI_STUDIO && 
            baseConfig.apiKey.isBlank()) {
            
            // Try to load from config.properties
            val fileApiKey = getGoogleStudioApiKeyFromFile(context)
            if (fileApiKey.isNotBlank()) {
                Log.i(TAG, "Using Google AI Studio API key from config.properties")
                return baseConfig.copy(apiKey = fileApiKey)
            } else {
                Log.w(TAG, "No Google AI Studio API key found in SharedPreferences or config.properties")
            }
        }
        
        return baseConfig
    }
    
    // ========== Google Gemini Configuration ==========
    
    /**
     * Get Google Gemini configuration
     */
    fun getGoogleGeminiConfig(): GoogleGeminiConfig {
        return try {
            val configJson = get(Keys.GOOGLE_GEMINI_CONFIG, "")
            if (configJson.isBlank()) {
                GoogleGeminiConfig.default()
            } else {
                json.decodeFromString<GoogleGeminiConfig>(configJson)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Google Gemini config, using defaults", e)
            GoogleGeminiConfig.default()
        }
    }
    
    /**
     * Save Google Gemini configuration
     */
    fun saveGoogleGeminiConfig(config: GoogleGeminiConfig) {
        try {
            val validatedConfig = with(GoogleGeminiConfig) { config.validate() }
            val configJson = json.encodeToString(validatedConfig)
            set(Keys.GOOGLE_GEMINI_CONFIG, configJson)
            Log.d(TAG, "Google Gemini config saved successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save Google Gemini config", e)
        }
    }
    
    /**
     * Reset Google Gemini configuration to defaults
     */
    fun resetGoogleGeminiConfig() {
        remove(Keys.GOOGLE_GEMINI_CONFIG)
        Log.d(TAG, "Google Gemini config reset to defaults")
    }
}