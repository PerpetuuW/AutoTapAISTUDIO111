plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.example.autotap"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.autotap"
        minSdk = 24
        targetSdk = 34
        versionCode = 34
        versionName = "34.9"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("debugConfig") {
            val rootDebugKeystore = file("${rootDir}/debug.keystore")
            if (rootDebugKeystore.exists()) {
                storeFile = rootDebugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        create("release") {
            val keyFile = file("release.keystore")
            if (keyFile.exists()) {
                storeFile = keyFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "AutoTapRuStore2026"
                keyAlias = System.getenv("KEY_ALIAS") ?: "autotap_release_key"
                keyPassword = System.getenv("KEY_PASSWORD") ?: "AutoTapRuStore2026"
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            val rootDebugKeystore = file("${rootDir}/debug.keystore")
            if (rootDebugKeystore.exists()) {
                signingConfig = signingConfigs.getByName("debugConfig")
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                "-opt-in=kotlin.RequiresOptIn",
                "-Xjvm-default=all"
            )
        }
    }


    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = false
            keepDebugSymbols += listOf("**/libmediapipe_tasks_vision_jni.so", "**/libonnxruntime.so", "**/libonnxruntime4j_jni.so")
        }
    }

    // [V28.1] Запрет сплита языковых ресурсов в Play App Bundle для корректной работы динамической смены языка
    bundle {
        language {
            enableSplit = false
        }
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        noCompress += listOf("tflite", "task")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.onnxruntime.android) // onnxruntime-android:1.30.0 (16 KB Aligned)
    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.yandex.mobileads)
    implementation(libs.rustore.pay)

    // Jetpack Compose & UI
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    
    // Lifecycle & StateFlow
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    
    // LeakCanary
    debugImplementation(libs.leakcanary.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.json)
}
