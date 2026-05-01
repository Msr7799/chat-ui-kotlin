package com.example.chat_ui.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    secondary = PrimaryIndigo,
    tertiary = PrimaryPurple,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onPrimary = Color(0xFF0E1116),
    onSecondary = TextPrimary,
    onTertiary = Color(0xFF1C1200),
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = DarkBorder,
    error = AccentRed
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    secondary = PrimaryIndigo,
    tertiary = PrimaryPurple,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onPrimary = Color.White,
    onSecondary = Color(0xFF1E1533),
    onTertiary = Color(0xFF231800),
    onBackground = LightTextPrimary,
    onSurface = LightTextPrimary,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    error = AccentRed
)

// Custom selected color
@Composable
fun selectedColor(): Color {
    return if (ThemeManager.isDarkMode) SelectedDark else SelectedLight
}

private fun createColorScheme(colors: ThemeColors) = if (colors.isDark) {
    darkColorScheme(
        primary = colors.primary,
        secondary = PrimaryIndigo,
        tertiary = PrimaryPurple,
        background = colors.background,
        surface = colors.surface,
        surfaceVariant = colors.surfaceVariant,
        onPrimary = Color(0xFF0E1116),
        onSecondary = colors.textPrimary,
        onTertiary = Color(0xFF1C1200),
        onBackground = colors.textPrimary,
        onSurface = colors.textPrimary,
        onSurfaceVariant = colors.textSecondary,
        outline = colors.border,
        error = AccentRed
    )
} else {
    lightColorScheme(
        primary = colors.primary,
        secondary = PrimaryIndigo,
        tertiary = PrimaryPurple,
        background = colors.background,
        surface = colors.surface,
        surfaceVariant = colors.surfaceVariant,
        onPrimary = Color.White,
        onSecondary = Color(0xFF1E1533),
        onTertiary = Color(0xFF231800),
        onBackground = colors.textPrimary,
        onSurface = colors.textPrimary,
        onSurfaceVariant = colors.textSecondary,
        outline = colors.border,
        error = AccentRed
    )
}

@Composable
fun ChatUITheme(
    themePreference: ThemePreference = ThemeManager.currentPreference,
    content: @Composable () -> Unit
) {
    val systemIsDark = isSystemInDarkTheme()
    val themeColors = ThemeManager.getThemeColors(themePreference, systemIsDark)
    val colorScheme = createColorScheme(themeColors)
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = themeColors.background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = themeColors.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !themeColors.isDark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !themeColors.isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Legacy function for backward compatibility
@Composable
fun ChatUIThemeLegacy(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
