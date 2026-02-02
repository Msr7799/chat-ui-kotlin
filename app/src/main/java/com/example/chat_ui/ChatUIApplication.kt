package com.example.chat_ui

import android.app.Application
import android.util.Log
import com.example.chat_ui.data.firebase.FirebaseManager

/**
 * Application class for ChatUI
 * 
 * Handles global initialization including Firebase
 */
class ChatUIApplication : Application() {
    
    companion object {
        private const val TAG = "ChatUIApplication"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase globally - this ensures it's available for all Activities
        try {
            FirebaseManager.init(this)
            Log.i(TAG, "Firebase initialized in Application.onCreate()")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase: ${e.message}", e)
        }
    }
}
