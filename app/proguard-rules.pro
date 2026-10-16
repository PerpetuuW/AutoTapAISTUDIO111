# AutoTap Architecture — R8/Proguard Shrinker Rules

# Защита доменных моделей и перечислений для JSON сериализации
-keep @androidx.annotation.Keep class com.example.autotap.domain.model.** { *; }
-keepclassmembers class com.example.autotap.domain.model.** {
    <fields>;
    <init>(...);
}
-keepclassmembers class com.example.autotap.domain.model.graph.** {
    <fields>;
    <init>(...);
}
-keepclassmembers enum com.example.autotap.domain.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Защита интерфейсов шлюзов и репозиториев
-keep interface com.example.autotap.domain.gateway.** { *; }
-keep interface com.example.autotap.domain.repository.** { *; }
-keep interface com.example.autotap.domain.engine.** { *; }

# Защита ML Kit Text Recognition
-dontwarn com.google.mlkit.**
-keep class com.google.mlkit.vision.text.** { *; }

# [V120.0] Защита MediaPipe Tasks Vision
-dontwarn com.google.mediapipe.**

# [V180.0] Защита Yandex Mobile Ads SDK
-dontwarn com.yandex.mobile.ads.**

# Защита ONNX Runtime JNI вызовов
-dontwarn ai.onnxruntime.**
-keep class ai.onnxruntime.** { *; }

# Защита RuStore Pay SDK
-dontwarn ru.rustore.sdk.**

# Защита Kotlin Coroutines и StateFlow
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }

# Сохранение атрибутов аннотаций и сигнатур типов
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
