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
    GOOGLE_VERTEX_AI(
        displayName = "Google Vertex AI (Backend Proxy)",
        defaultBaseUrl = "https://veo-backend-347302193342.us-central1.run.app/v1"
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
            ApiProvider.GOOGLE_VERTEX_AI -> {
                if (useGoogleAuth) {
                    // Token will be fetched dynamically from GoogleAuthManager
                    "Bearer {GOOGLE_TOKEN}"
                } else {
                    "Bearer $apiKey"
                }
            }
            ApiProvider.GOOGLE_AI_STUDIO -> {
                // Google AI Studio uses API Key in query param, not header
                // But we still return it for consistency
                "Bearer $apiKey"
            }
        }
    }
    
    /**
     * Get authentication method
     */
    fun getAuthMethod(): AuthMethod {
        return when {
            provider == ApiProvider.GOOGLE_VERTEX_AI && useGoogleAuth -> AuthMethod.GOOGLE_SIGN_IN
            provider == ApiProvider.GOOGLE_AI_STUDIO && useGoogleAuth -> AuthMethod.GOOGLE_SIGN_IN
            else -> AuthMethod.API_KEY
        }
    }
    
    /**
     * Get API key for query parameter (used by Google AI Studio)
     */
    fun getApiKeyForQueryParam(): String? {
        return when (provider) {
            ApiProvider.GOOGLE_AI_STUDIO -> apiKey
            else -> null
        }
    }
    
    /**
     * Check if configuration is valid
     */
    fun isValid(): Boolean {
        return when {
            // Google with Firebase Auth - no API key needed
            provider == ApiProvider.GOOGLE_VERTEX_AI && useGoogleAuth -> {
                baseUrl.isNotBlank()
            }
            // Any provider with API key
            else -> {
                baseUrl.isNotBlank() && apiKey.isNotBlank()
            }
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
