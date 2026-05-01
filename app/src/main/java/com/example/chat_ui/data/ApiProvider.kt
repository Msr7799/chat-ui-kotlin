package com.example.chat_ui.data

import kotlinx.serialization.Serializable

/**
 * API Provider types
 * Supports multiple OpenAI-compatible and Google AI Studio providers
 */
@Serializable
enum class ApiProvider(val displayName: String, val defaultBaseUrl: String) {
    HUGGINGFACE(
        displayName = "HuggingFace Router",
        defaultBaseUrl = "https://router.huggingface.co/v1"
    ),
    GOOGLE_AI_STUDIO(
        displayName = "Google AI Studio API (Direct)",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta"
    );

    companion object {
        fun fromString(value: String): ApiProvider {
            return entries.find { it.name == value } ?: HUGGINGFACE
        }
    }
}

/**
 * Authentication method for providers
 */
enum class AuthMethod {
    API_KEY,           // Direct API key/token
    GOOGLE_SIGN_IN     // Google OAuth 2.0 Sign-In (auto-refresh)
}

/**
 * Provider configuration
 */
@Serializable
data class ProviderConfig(
    val provider: ApiProvider = ApiProvider.HUGGINGFACE,
    val baseUrl: String = provider.defaultBaseUrl,
    val apiKey: String = "",
    val customHeaders: Map<String, String> = emptyMap(),
    val useGoogleAuth: Boolean = false  // Use Google Sign-In for auth
) {
    /**
     * Get full endpoint URL
     */
    fun getChatCompletionsUrl(): String {
        return "${baseUrl.trimEnd('/')}/chat/completions"
    }

    /**
     * Get authorization header value
     * For Google with Sign-In, use GoogleAuthManager.getAccessToken()
     */
    fun getAuthHeader(): String {
        return when (provider) {
            ApiProvider.HUGGINGFACE -> "Bearer $apiKey"
            ApiProvider.GOOGLE_AI_STUDIO -> {
                // Gemini / Google AI Studio uses x-goog-api-key header (not Authorization: Bearer)
                // Keep empty here to avoid accidental misuse of Authorization header.
                ""
            }
        }
    }
    
    /**
     * Get authentication method
     */
    fun getAuthMethod(): AuthMethod {
        return when {
            provider == ApiProvider.GOOGLE_AI_STUDIO && useGoogleAuth -> AuthMethod.GOOGLE_SIGN_IN
            else -> AuthMethod.API_KEY
        }
    }
    
    /**
     * Get API key for query parameter (legacy).
     * Prefer using x-goog-api-key header instead.
     */
    fun getApiKeyForQueryParam(): String? {
        return when (provider) {
            ApiProvider.GOOGLE_AI_STUDIO -> apiKey
            else -> null
        }
    }

    /**
     * Get API key header pair for providers that expect x-goog-api-key.
     */
    fun getApiKeyHeaderPair(): Pair<String, String>? {
        return when (provider) {
            ApiProvider.GOOGLE_AI_STUDIO -> "x-goog-api-key" to apiKey
            else -> null
        }
    }

    /**
     * Check if configuration is valid
     */
    fun isValid(): Boolean {
        return when (provider) {
            // HuggingFace يمر الآن عبر Go backend، لذلك لا نحتاج HF API key داخل Android.
            ApiProvider.HUGGINGFACE -> baseUrl.isNotBlank()
            ApiProvider.GOOGLE_AI_STUDIO -> baseUrl.isNotBlank() && (apiKey.isNotBlank() || useGoogleAuth || baseUrl.contains("/v1/google"))
        }
    }

    /**
     * Get all headers for HTTP request
     */
    fun getHeaders(): Map<String, String> {
        val headers = mutableMapOf(
            "Content-Type" to "application/json",
            "Authorization" to getAuthHeader()
        )
        
        // Add custom headers
        headers.putAll(customHeaders)
        
        return headers
    }
}
