package com.example.chat_ui.ui.theme

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Language Manager for handling app language switching
 */
object LanguageManager {
    
    enum class Language(val code: String, val displayName: String, val nativeName: String) {
        ENGLISH("en", "English", "English"),
        ARABIC("ar", "Arabic", "العربية")
    }
    
    // Current language state - observable by Compose
    var currentLanguage by mutableStateOf(Language.ENGLISH)
        private set
    
    /**
     * Initialize language from system or saved preference
     */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences("chat_ui_prefs", Context.MODE_PRIVATE)
        val savedCode = prefs.getString("language", null)
        
        currentLanguage = if (savedCode != null) {
            Language.entries.find { it.code == savedCode } ?: Language.ENGLISH
        } else {
            // Use system language if available
            val systemLocale = Locale.getDefault().language
            if (systemLocale == "ar") Language.ARABIC else Language.ENGLISH
        }
    }
    
    /**
     * Set app language
     */
    fun setLanguage(context: Context, language: Language) {
        currentLanguage = language
        
        // Save preference
        context.getSharedPreferences("chat_ui_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("language", language.code)
            .apply()
        
        // Apply locale change
        applyLocale(context, language)
    }
    
    /**
     * Apply locale to the app
     */
    private fun applyLocale(context: Context, language: Language) {
        val locale = Locale(language.code)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ uses LocaleManager
            context.getSystemService(LocaleManager::class.java)
                ?.applicationLocales = LocaleList(locale)
        } else {
            // Older versions use AppCompatDelegate
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(language.code)
            )
        }
    }
    
    /**
     * Get display name for current language
     */
    fun getCurrentLanguageDisplayName(): String {
        return currentLanguage.nativeName
    }
    
    /**
     * Check if current language is RTL
     */
    fun isRtl(): Boolean {
        return currentLanguage == Language.ARABIC
    }
}
