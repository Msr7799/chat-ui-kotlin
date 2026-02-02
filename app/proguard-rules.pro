# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep Kotlinx Serialization (avoid stripping metadata needed at runtime)
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# Coroutines (avoid warnings/errors)
-dontwarn kotlinx.coroutines.**

# Glide (modules and internals)
-keep class com.bumptech.glide.** { *; }
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.AppGlideModule
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** { *; }
-dontwarn com.bumptech.glide.**

# Firebase (avoid warnings; Firestore/Storage/Auth)
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Google Play Services tasks
-dontwarn com.google.android.gms.tasks.**

# OkHttp/Okio
-dontwarn okhttp3.**
-dontwarn okio.**

# Serializable classes (used in Intent extras like VideoGenerationResult)
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private *;
    public *;
}

# Keep Kotlinx Serialization (avoid stripping metadata needed at runtime)
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# Coroutines (avoid warnings/errors)
-dontwarn kotlinx.coroutines.**

# Glide (modules and internals)
-keep class com.bumptech.glide.** { *; }
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.AppGlideModule
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** { *; }
-dontwarn com.bumptech.glide.**

# Firebase (avoid warnings; Firestore/Storage/Auth)
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Google Play Services tasks
-dontwarn com.google.android.gms.tasks.**

# OkHttp/Okio
-dontwarn okhttp3.**
-dontwarn okio.**

# Serializable classes (used in Intent extras like VideoGenerationResult)
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private *;
    public *;
}

# Keep VeoVideoClient and inner classes for Firestore deserialization
-keep class com.example.chat_ui.api.VeoVideoClient { *; }
-keep class com.example.chat_ui.api.VeoVideoClient$* { *; }
-keepclassmembers class com.example.chat_ui.api.VeoVideoClient$* {
    <init>();
    <init>(...);
    *;
}