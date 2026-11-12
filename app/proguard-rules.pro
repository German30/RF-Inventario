# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# --- Retrofit / OkHttp / Gson ---
# Retrofit y OkHttp traen sus propias reglas de consumidor; solo hace falta
# proteger los modelos que Gson (de)serializa por reflexión y silenciar
# referencias opcionales.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep class com.rf.inventario.data.remote.dto.** { *; }
-keepclassmembers,allowobfuscation class com.rf.inventario.data.remote.dto.** {
    <fields>;
}
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
