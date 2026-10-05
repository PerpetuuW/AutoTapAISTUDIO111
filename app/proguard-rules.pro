# AutoTap Greenfield Architecture — R8/Proguard Rules (v34.0)

# Защита доменных моделей и перечислений для симметричной JSON сериализации
-keep class com.example.autotap.domain.model.** { *; }
-keep enum com.example.autotap.domain.model.** { *; }
-keep class com.example.autotap.domain.model.graph.** { *; }
-keep enum com.example.autotap.domain.model.graph.** { *; }

# Защита интерфейсов шлюзов и репозиториев
-keep interface com.example.autotap.domain.gateway.** { *; }
-keep interface com.example.autotap.domain.repository.** { *; }
-keep interface com.example.autotap.domain.engine.** { *; }

# Защита ML Kit Text Recognition
-keep class com.google.mlkit.vision.text.latin.TextRecognizerOptions {
    public static <fields>;
}
-keep interface com.google.mlkit.vision.text.TextRecognizerOptionsInterface { *; }
-dontwarn com.google.mlkit.vision.text.cyrillic.**

# [V120.0] Защита MediaPipe Tasks Vision от R8 (исключение сбоя JNI нативных вызовов в релизе)
-keep class com.google.mediapipe.tasks.vision.** { *; }
-keep class com.google.mediapipe.framework.** { *; }
-keep class com.google.mediapipe.components.** { *; }
-dontwarn com.google.mediapipe.**

# [V180.0] Защита Yandex Mobile Ads SDK для релизной сборки RuStore
-keep class com.yandex.mobile.ads.** { *; }
-dontwarn com.yandex.mobile.ads.**

# Защита Kotlin Coroutines и StateFlow
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }

# Сохранение атрибутов аннотаций и сигнатур типов
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
