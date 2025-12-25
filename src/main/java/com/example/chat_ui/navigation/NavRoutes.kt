package com.example.chat_ui.navigation

sealed class NavRoutes(val route: String) {
    object Chat : NavRoutes("chat")
    object Settings : NavRoutes("settings")
    object ApiSettings : NavRoutes("api_settings")
    object Models : NavRoutes("models")
    object Gallery : NavRoutes("gallery")
    object ImageGeneration : NavRoutes("image_generation?model={model}") {
        fun createRoute(model: String): String {
            val encodedModel = java.net.URLEncoder.encode(model, "UTF-8")
            return "image_generation?model=$encodedModel"
        }
    }
    object ImageGallery : NavRoutes("image_gallery")
    object VideoGeneration : NavRoutes("video_generation")
    object VideoGallery : NavRoutes("video_gallery")
    object Account : NavRoutes("account")
    object Profile : NavRoutes("profile")
    object About : NavRoutes("about")
    object MCPSettings : NavRoutes("mcp_settings")
    object Debug : NavRoutes("debug")
}
