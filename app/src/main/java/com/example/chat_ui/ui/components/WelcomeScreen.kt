package com.example.chat_ui.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat_ui.R
import com.example.chat_ui.config.ConfigManager

/**
 * Welcome screen shown when no conversation is selected
 * Similar to ChatIntroduction.svelte in the Svelte app
 */
@Composable
fun WelcomeScreen(
    modifier: Modifier = Modifier
) {
    val isDarkTheme = isSystemInDarkTheme()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo with text - white for dark theme, black for light theme
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(
                    id = if (isDarkTheme) R.drawable.chatui_logo else R.drawable.chatui_logo_black
                ),
                contentDescription = "ChatUI Logo",
                modifier = Modifier.size(72.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = ConfigManager.appName,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
