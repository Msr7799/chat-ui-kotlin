package com.example.chat_ui.utils

/**
 * Small Cloudinary URL helper used by Compose image grids.
 *
 * The app stores the original Cloudinary URL in Firebase. Loading the original file directly in
 * a Recycler/Grid UI can allocate large bitmaps and make low-RAM emulators kill the process.
 * These helpers insert server-side Cloudinary transformations so Coil receives a small image.
 */
object CloudinaryUrlUtils {
    private const val UPLOAD_MARKER = "/image/upload/"

    fun optimizedImageUrl(
        url: String,
        width: Int? = null,
        height: Int? = null,
        crop: String = "limit",
        quality: String = "auto:eco"
    ): String {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return cleanUrl
        if (!cleanUrl.contains("res.cloudinary.com") || !cleanUrl.contains(UPLOAD_MARKER)) {
            return cleanUrl
        }

        val transforms = buildList {
            add("f_auto")
            add("q_$quality")
            width?.takeIf { it > 0 }?.let { add("w_$it") }
            height?.takeIf { it > 0 }?.let { add("h_$it") }
            add("c_$crop")
        }.joinToString(",")

        // Avoid stacking the exact same transform if this helper is called more than once.
        val transformedMarker = "$UPLOAD_MARKER$transforms/"
        if (cleanUrl.contains(transformedMarker)) return cleanUrl

        return cleanUrl.replaceFirst(UPLOAD_MARKER, transformedMarker)
    }

    fun galleryThumbnailUrl(url: String): String = optimizedImageUrl(
        url = url,
        width = 360,
        height = 360,
        crop = "fill",
        quality = "auto:low"
    )

    fun previewUrl(url: String): String = optimizedImageUrl(
        url = url,
        width = 1200,
        crop = "limit",
        quality = "auto:eco"
    )
}
