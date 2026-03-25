# =============================================
# ProGuard / R8 rules para Tenaza
# =============================================

# --- kotlinx.serialization ---
# Mantener las clases serializables y sus compañeros
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.tenaza.**$$serializer { *; }
-keepclassmembers class com.tenaza.** {
    *** Companion;
}
-keepclasseswithmembers class com.tenaza.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- BouncyCastle Ed25519 ---
-keep class org.bouncycastle.crypto.** { *; }
-keep class org.bouncycastle.jcajce.** { *; }
-keep class org.bouncycastle.jce.** { *; }
-dontwarn org.bouncycastle.**

# --- OkHttp ---
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# --- Koin ---
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# --- Tink (cifrado DataStore) ---
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# --- Reglas generales ---
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
