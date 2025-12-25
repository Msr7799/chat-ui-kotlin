package com.example.chat_ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.chat_ui.api.LlmRouter
import com.example.chat_ui.config.ConfigManager
import com.example.chat_ui.data.cloud.CloudinaryManager
import com.example.chat_ui.data.firebase.FirebaseManager
import com.example.chat_ui.mcp.MCPManager
import com.example.chat_ui.ui.splash.SplashScreen
import com.example.chat_ui.ui.theme.ChatUITheme
import com.example.chat_ui.ui.theme.LanguageManager
import com.example.chat_ui.ui.theme.ThemeManager

class MainActivity : ComponentActivity() {
    private var showSplash by mutableStateOf(true)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize ConfigManager with application context
        ConfigManager.init(applicationContext)

        // Initialize ThemeManager
        ThemeManager.init(applicationContext)

        // Initialize LanguageManager
        LanguageManager.init(applicationContext)

        // Initialize Firebase (Firestore + Realtime Database + Storage + Auth)
        FirebaseManager.init(applicationContext)

        // Initialize Cloudinary
        CloudinaryManager.init(applicationContext)

        // Load LLM Router routes
        LlmRouter.loadRoutes(applicationContext)
        
        // Initialize MCP Manager (servers stay disconnected until user enables them manually)
        MCPManager.init(applicationContext)

        enableEdgeToEdge()
        val startRouteFromIntent = intent?.getStringExtra("open_route")

        setContent {
            ChatUITheme {
                if (showSplash) {
                    SplashScreen(
                        onSplashComplete = { showSplash = false }
                    )
                } else {
                    Surface(modifier = Modifier.fillMaxSize()) { ChatApp(startRoute = startRouteFromIntent) }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cleanup MCP connections
        MCPManager.disconnectAll()
        // Firebase handles cleanup automatically
    }
    
    override fun onStop() {
        super.onStop()
        // Disconnect MCP when app goes to background
        MCPManager.disconnectAll()
    }
    
    override fun onRestart() {
        super.onRestart()
        // Don't auto-reconnect - user must manually enable servers
    }
}
