package com.example.chat_ui

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.chat_ui.config.ConfigManager
import com.example.chat_ui.data.firebase.FirebaseManager

/**
 * Application class for ChatUI.
 *
 * Also provides a conservative global Coil image loader so image grids do not keep too many
 * large decoded bitmaps in RAM on emulators or low-memory devices.
 */
class ChatUIApplication : Application(), ImageLoaderFactory {

    companion object {
        private const val TAG = "ChatUIApplication"
    }

    override fun onCreate() {
        super.onCreate()

        try {
            ConfigManager.init(this)
            Log.i(TAG, "Config initialized in Application.onCreate()")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize config: ${e.message}", e)
        }

        try {
            FirebaseManager.init(this)
            Log.i(TAG, "Firebase initialized in Application.onCreate()")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase: ${e.message}", e)
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.10)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .allowHardware(true)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .crossfade(false)
            .respectCacheHeaders(false)
            .build()
    }
}
