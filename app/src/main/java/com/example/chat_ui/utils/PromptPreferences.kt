package com.example.chat_ui.utils

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/**
 * PromptPreferences - Handles prompt persistence and history
 * 
 * Features:
 * - Auto-save draft prompts (survives app restarts)
 * - Store last 4 prompts history for quick access (video/image)
 * - Store last 10 prompts history for chat
 */
object PromptPreferences {
    
    private const val PREFS_NAME = "prompt_preferences"
    private const val MAX_HISTORY_SIZE = 4
    private const val MAX_CHAT_HISTORY_SIZE = 10
    
    // Keys for draft prompts (auto-saved current text)
    private const val KEY_CHAT_DRAFT = "chat_draft"
    private const val KEY_VIDEO_DRAFT = "video_draft"
    private const val KEY_IMAGE_DRAFT = "image_draft"
    
    // Keys for prompt history (last 4 successful prompts for video/image, last 10 for chat)
    private const val KEY_CHAT_HISTORY = "chat_history"
    private const val KEY_VIDEO_HISTORY = "video_history"
    private const val KEY_IMAGE_HISTORY = "image_history"
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    // ========== DRAFT PROMPTS (Auto-save) ==========
    
    /** Save chat draft prompt */
    fun saveChatDraft(context: Context, prompt: String) {
        getPrefs(context).edit().putString(KEY_CHAT_DRAFT, prompt).apply()
    }
    
    /** Get chat draft prompt */
    fun getChatDraft(context: Context): String {
        return getPrefs(context).getString(KEY_CHAT_DRAFT, "") ?: ""
    }
    
    /** Clear chat draft prompt */
    fun clearChatDraft(context: Context) {
        getPrefs(context).edit().remove(KEY_CHAT_DRAFT).apply()
    }
    
    /** Save video draft prompt */
    fun saveVideoDraft(context: Context, prompt: String) {
        getPrefs(context).edit().putString(KEY_VIDEO_DRAFT, prompt).apply()
    }
    
    /** Get video draft prompt */
    fun getVideoDraft(context: Context): String {
        return getPrefs(context).getString(KEY_VIDEO_DRAFT, "") ?: ""
    }
    
    /** Clear video draft prompt */
    fun clearVideoDraft(context: Context) {
        getPrefs(context).edit().remove(KEY_VIDEO_DRAFT).apply()
    }
    
    /** Save image draft prompt */
    fun saveImageDraft(context: Context, prompt: String) {
        getPrefs(context).edit().putString(KEY_IMAGE_DRAFT, prompt).apply()
    }
    
    /** Get image draft prompt */
    fun getImageDraft(context: Context): String {
        return getPrefs(context).getString(KEY_IMAGE_DRAFT, "") ?: ""
    }
    
    /** Clear image draft prompt */
    fun clearImageDraft(context: Context) {
        getPrefs(context).edit().remove(KEY_IMAGE_DRAFT).apply()
    }
    
    // ========== PROMPT HISTORY (Last 4) ==========
    
    private fun getHistoryList(context: Context, key: String): MutableList<String> {
        val json = getPrefs(context).getString(key, "[]") ?: "[]"
        val list = mutableListOf<String>()
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
        } catch (e: Exception) {
            // Ignore parse errors, return empty list
        }
        return list
    }
    
    private fun saveHistoryList(context: Context, key: String, list: List<String>) {
        val jsonArray = JSONArray()
        list.forEach { jsonArray.put(it) }
        getPrefs(context).edit().putString(key, jsonArray.toString()).apply()
    }
    
    /** Add prompt to video history (keeps last 4) */
    fun addToVideoHistory(context: Context, prompt: String) {
        if (prompt.isBlank()) return
        val list = getHistoryList(context, KEY_VIDEO_HISTORY)
        // Remove if already exists (to avoid duplicates)
        list.remove(prompt)
        // Add to beginning
        list.add(0, prompt)
        // Keep only last 4
        val trimmed = list.take(MAX_HISTORY_SIZE)
        saveHistoryList(context, KEY_VIDEO_HISTORY, trimmed)
    }
    
    /** Get video prompt history (last 4) */
    fun getVideoHistory(context: Context): List<String> {
        return getHistoryList(context, KEY_VIDEO_HISTORY)
    }
    
    /** Add prompt to image history (keeps last 4) */
    fun addToImageHistory(context: Context, prompt: String) {
        if (prompt.isBlank()) return
        val list = getHistoryList(context, KEY_IMAGE_HISTORY)
        list.remove(prompt)
        list.add(0, prompt)
        val trimmed = list.take(MAX_HISTORY_SIZE)
        saveHistoryList(context, KEY_IMAGE_HISTORY, trimmed)
    }
    
    /** Get image prompt history (last 4) */
    fun getImageHistory(context: Context): List<String> {
        return getHistoryList(context, KEY_IMAGE_HISTORY)
    }
    
    /** Add prompt to chat history (keeps last 10) */
    fun addToChatHistory(context: Context, prompt: String) {
        if (prompt.isBlank()) return
        val list = getHistoryList(context, KEY_CHAT_HISTORY)
        // Remove if already exists (to avoid duplicates)
        list.remove(prompt)
        // Add to beginning
        list.add(0, prompt)
        // Keep only last 10
        val trimmed = list.take(MAX_CHAT_HISTORY_SIZE)
        saveHistoryList(context, KEY_CHAT_HISTORY, trimmed)
    }
    
    /** Get chat prompt history (last 10) */
    fun getChatHistory(context: Context): List<String> {
        return getHistoryList(context, KEY_CHAT_HISTORY)
    }
    
    /** Clear all prompt data */
    fun clearAll(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
