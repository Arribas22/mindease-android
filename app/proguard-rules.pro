# Reglas ProGuard para MindEase

# MediaPipe Tasks
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.**

# Google Play Billing
-keep class com.android.billingclient.** { *; }

# AdMob
-keep public class com.google.android.gms.ads.** { public *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
