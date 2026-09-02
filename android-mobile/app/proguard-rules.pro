# MuzziQ mobile — règles ProGuard/R8 minimales pour les libs à réflexion.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-keep class com.muzziq.mobile.data.model.** { *; }

# Moshi (réflexion sur les data class des contrats API)
-keepclassmembers class com.muzziq.mobile.data.model.** {
    <fields>;
    <init>(...);
}
-keep @com.squareup.moshi.JsonClass class * { *; }

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# OkHttp/Retrofit (platform reflection)
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keepattributes Exceptions
