package com.example.chat_ui.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.example.chat_ui.ui.theme.BubleTextPrimary

/** Theme preference types - Light, Dark, or System */
enum class ThemePreference {
    LIGHT,
    DARK,
    SYSTEM
}

/** Theme configuration data */
data class ThemeColors(
        val background: Color,
        val surface: Color,
        val surfaceVariant: Color,
        val border: Color,
        val primary: Color,
        val textPrimary: Color,
        val textSecondary: Color,
        val textMuted: Color,


        // NEW: explicit text colors for bubbles
        val userBubbleText: Color,
        val assistantBubbleText: Color,
        val userBubble: Color,
        val assistantBubble: Color,
        
        // Provider text color for light theme
        val providerTextLight: Color,
        
        // Selected color
        val selected: Color,

        val isDark: Boolean
)

/**
 * Theme Manager - manages theme preferences and colors Similar to src/lib/switchTheme.ts in
 * JavaScript
 */
object ThemeManager {
    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_THEME = "theme"

    private lateinit var sharedPrefs: SharedPreferences
    private var isInitialized = false

    var currentPreference by mutableStateOf(ThemePreference.SYSTEM)
        private set

    var isDarkMode by mutableStateOf(true)
        private set

    fun init(context: Context) {
        if (isInitialized) return
        sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        currentPreference = loadPreference()
        applyNightMode(currentPreference)
        isInitialized = true
    }

    private fun applyNightMode(preference: ThemePreference) {
        val mode = when (preference) {
            ThemePreference.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemePreference.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemePreference.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun loadPreference(): ThemePreference {
        val raw = sharedPrefs.getString(KEY_THEME, "system") ?: "system"
        return when (raw) {
            "light" -> ThemePreference.LIGHT
            "dark" -> ThemePreference.DARK
            else -> ThemePreference.SYSTEM
        }
    }

    fun setTheme(preference: ThemePreference) {
        currentPreference = preference
        val value =
                when (preference) {
                    ThemePreference.LIGHT -> "light"
                    ThemePreference.DARK -> "dark"
                    ThemePreference.SYSTEM -> "system"
                }
        sharedPrefs.edit().putString(KEY_THEME, value).apply()
        applyNightMode(preference)
    }

    /** Toggle between themes: light -> dark -> light */
    fun switchTheme() {
        val next =
                when (currentPreference) {
                    ThemePreference.LIGHT -> ThemePreference.DARK
                    ThemePreference.DARK -> ThemePreference.LIGHT
                    ThemePreference.SYSTEM -> ThemePreference.LIGHT
                }
        setTheme(next)
    }

    fun getThemeColors(preference: ThemePreference, systemIsDark: Boolean): ThemeColors {
        val effectivePreference =
                if (preference == ThemePreference.SYSTEM) {
                    if (systemIsDark) ThemePreference.DARK else ThemePreference.LIGHT
                } else preference

        isDarkMode = effectivePreference != ThemePreference.LIGHT

        return when (effectivePreference) {
            ThemePreference.LIGHT ->
                    ThemeColors(
                            background = LightBackground,
                            surface = LightSurface,
                            surfaceVariant = LightSurfaceVariant,
                            border = LightBorder,
                            primary = PrimaryBlue,
                            textPrimary = LightTextPrimary,
                            textSecondary = LightTextPrimary,
                            textMuted = Color(0xFF4B5563),
                            userBubble = LightUserBubble,
                            userBubbleText = Color.White,
                            assistantBubble = LightSurfaceVariant,
                            assistantBubbleText = BubleTextPrimary,
                            providerTextLight = ProviderTextLight,
                            selected = SelectedLight,
                            isDark = false
                    )
            ThemePreference.DARK, ThemePreference.SYSTEM ->
                    ThemeColors(
                            background = DarkBackground,
                            surface = DarkSurface,
                            surfaceVariant = DarkSurfaceVariant,
                            border = DarkBorder,
                            primary = PrimaryBlue,
                            textPrimary = TextPrimary,
                            textSecondary = TextSecondary,
                            textMuted = TextMuted,
                            userBubble = UserBubble,
                            userBubbleText = Color.White,
                            assistantBubble = AssistantBubble,
                            assistantBubbleText = TextPrimary,
                            providerTextLight = TextSecondary,
                            selected = SelectedDark,
                            isDark = true
                    )
        }
    }

    fun getThemeName(preference: ThemePreference): String {
        return when (preference) {
            ThemePreference.LIGHT -> "Light"
            ThemePreference.DARK -> "Dark"
            ThemePreference.SYSTEM -> "System"
        }
    }

    fun getThemeIcon(preference: ThemePreference): String {
        return when (preference) {
            ThemePreference.LIGHT -> "☀️"
            ThemePreference.DARK -> "🌙"
            ThemePreference.SYSTEM -> "⚙️"
        }
    }
}
