package com.example.chat_ui.mcp

import android.util.Log

/**
 * MCP Utility functions
 * Based on Chat UI's hf.ts helpers
 */
object MCPUtils {
    private const val TAG = "MCPUtils"
    
    /**
     * Check if headers contain Authorization header
     */
    fun hasAuthHeader(headers: Map<String, String>?): Boolean {
        if (headers == null) return false
        return headers.keys.any { it.equals("authorization", ignoreCase = true) }
    }
    
    /**
     * Check if URL is a HuggingFace MCP endpoint
     */
    fun isHfMcpEndpoint(urlString: String): Boolean {
        return try {
            val url = java.net.URL(urlString)
            val host = url.host.lowercase()
            host == "hf.co" || host == "huggingface.co" || host.endsWith(".huggingface.co")
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if token is non-empty string
     */
    fun hasNonEmptyToken(token: String?): Boolean {
        return !token.isNullOrBlank()
    }
    
    /**
     * Validate MCP server URL
     */
    fun validateMcpServerUrl(url: String): Boolean {
        if (url.isBlank()) return false
        
        return try {
            val urlObj = java.net.URL(url)
            urlObj.protocol == "https" || urlObj.protocol == "http"
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if header key is sensitive (should be hidden)
     */
    fun isSensitiveHeader(key: String): Boolean {
        val lowerKey = key.lowercase()
        return lowerKey.contains("auth") || 
               lowerKey.contains("token") || 
               lowerKey.contains("key") ||
               lowerKey.contains("secret")
    }
}
