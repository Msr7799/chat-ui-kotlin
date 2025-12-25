package com.example.chat_ui.config

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.chat_ui.data.ApiProvider
import com.example.chat_ui.data.ProviderConfig
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
    private const val LEGACY_PREFS_NAME = "chat_ui_config"
    private const val ENCRYPTED_PREFS_NAME = "chat_ui_config_secure"

    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var encryptedPrefs: SharedPreferences
    private val defaultConfig = Properties()
    private val localConfig = Properties()
    private var isInitialized = false
    
    // Application context for API calls (safe to store as it's application-scoped)
    private lateinit var appContext: Context
    
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

        // Veo Backend Configuration
        const val VEO_BACKEND_BASE_URL = "VEO_BACKEND_BASE_URL"
        
        // Video Feature Flags
        const val VIDEO_PUBLIC_UPLOAD_ENABLED = "VIDEO_PUBLIC_UPLOAD_ENABLED"
        const val VIDEO_PRIVATE_UPLOAD_ENABLED = "VIDEO_PRIVATE_UPLOAD_ENABLED"
        const val VIDEO_IMAGE_TO_VIDEO_ENABLED = "VIDEO_IMAGE_TO_VIDEO_ENABLED"
        const val VIDEO_VIDEO_TO_VIDEO_ENABLED = "VIDEO_VIDEO_TO_VIDEO_ENABLED"

        // YouTube upload is handled by backend - no Android credentials needed
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
        } catch (_: Exception) {
            sharedPrefs
        }

        migrateSensitiveKeysIfNeeded()

        // Load default config from assets
        try {
            context.assets.open("config.properties").use { stream -> defaultConfig.load(stream) }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Load local overrides from assets (if exists)
        try {
            context.assets.open("local.properties").use { stream -> localConfig.load(stream) }
        } catch (e: Exception) {
            // local.properties is optional
        }

        isInitialized = true
    }

    private fun isSensitiveKey(key: String): Boolean {
        return key == Keys.OPENAI_API_KEY || key == Keys.CLOUDINARY_API_SECRET
    }

    private fun migrateSensitiveKeysIfNeeded() {
        if (!::sharedPrefs.isInitialized) return
        if (!::encryptedPrefs.isInitialized) return
        if (sharedPrefs === encryptedPrefs) return

        val sensitiveKeys = listOf(Keys.OPENAI_API_KEY, Keys.CLOUDINARY_API_SECRET)
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
     * Remove configuration value from SharedPreferences (will fall back to local/default config)
     */
    fun remove(key: String) {
        sharedPrefs.edit().remove(key).apply()
        if (::encryptedPrefs.isInitialized && encryptedPrefs !== sharedPrefs) {
            encryptedPrefs.edit().remove(key).apply()
        }
    }

    /** Clear all user-set configuration */
    fun clear() {
        sharedPrefs.edit().clear().apply()
        if (::encryptedPrefs.isInitialized && encryptedPrefs !== sharedPrefs) {
            encryptedPrefs.edit().clear().apply()
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

    // Veo Backend convenience properties
    val veoBackendBaseUrl: String
        get() = get(Keys.VEO_BACKEND_BASE_URL, "http://localhost:8080")
    
    // Video Feature Flags convenience properties
    val isVideoPublicUploadEnabled: Boolean
        get() = getBoolean(Keys.VIDEO_PUBLIC_UPLOAD_ENABLED, true)
    
    val isVideoPrivateUploadEnabled: Boolean
        get() = getBoolean(Keys.VIDEO_PRIVATE_UPLOAD_ENABLED, true)
    
    val isImageToVideoEnabled: Boolean
        get() = getBoolean(Keys.VIDEO_IMAGE_TO_VIDEO_ENABLED, true)
    
    val isVideoToVideoEnabled: Boolean
        get() = getBoolean(Keys.VIDEO_VIDEO_TO_VIDEO_ENABLED, true)

    // YouTube upload handled by backend - no client credentials needed
    
    /**
     * Get current provider configuration
     */
    fun getProviderConfig(): ProviderConfig {
        val providerName = get(Keys.API_PROVIDER, ApiProvider.HUGGINGFACE.name)
        val provider = ApiProvider.fromString(providerName)
        val baseUrl = get(Keys.OPENAI_BASE_URL, provider.defaultBaseUrl)
        val apiKey = get(Keys.OPENAI_API_KEY, "")
        
        // IMPORTANT: "Use Google Auth" only applies to Vertex AI via backend.
        // If we keep it global, switching providers can accidentally wipe the API key.
        val useGoogleAuth = if (provider == ApiProvider.GOOGLE_VERTEX_AI) {
            getBoolean(Keys.USE_GOOGLE_AUTH, false)
        } else {
            false
        }
        
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
        set(Keys.OPENAI_BASE_URL, config.baseUrl)
        set(Keys.OPENAI_API_KEY, config.apiKey)
        setBoolean(Keys.USE_GOOGLE_AUTH, config.useGoogleAuth)
    }
}
