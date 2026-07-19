# ------------------------------------------------------------------
# GitFlowMobile ULTRA HARD R8 RULES
# Maximum shrinking / obfuscation / optimization
# ------------------------------------------------------------------

# Aggressive optimization
-optimizationpasses 10
-allowaccessmodification
-overloadaggressively
-repackageclasses ''

# Remove unused code
-dontpreverify

# Keep only required metadata
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes AnnotationDefault


# --------------------------------------------------
# Android components
# --------------------------------------------------

# Required Android framework entry points
-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider


# --------------------------------------------------
# Room - minimum required
# --------------------------------------------------

-keep @androidx.room.Database class *
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *


# --------------------------------------------------
# JGit - only runtime discovery
# --------------------------------------------------

-keep class org.eclipse.jgit.transport.** {
    public *;
}

-keep class org.eclipse.jgit.api.** {
    public *;
}

-dontwarn org.eclipse.jgit.**
-dontwarn org.slf4j.**
-dontwarn com.jcraft.jsch.**
-dontwarn com.googlecode.javaewah.**


# --------------------------------------------------
# Kotlin
# --------------------------------------------------

-keep class kotlin.Metadata

-dontwarn kotlin.**
-dontwarn kotlinx.coroutines.**


# --------------------------------------------------
# Crypto
# --------------------------------------------------

-keep class willykez.gitflowmobile.data.repository.TokenCrypto {
    public *;
}

-dontwarn javax.crypto.**


# --------------------------------------------------
# Gson (only if used)
# --------------------------------------------------

-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}


# --------------------------------------------------
# Remove logs
# --------------------------------------------------

-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
}


# --------------------------------------------------
# Crash mapping
# --------------------------------------------------

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
