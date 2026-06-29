plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services) // Plugin per Firebase
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.esempio.serra"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.esempio.serra"
        minSdk = 23
        targetSdk = 35
        versionCode = 16
        versionName = "5.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        jvmToolchain(11)
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.0"
    }
}

dependencies {
    // Core AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx.v182) // Per ActivityResultContracts
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.material3)         // material 3

    // Jetpack Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Firebase BOM
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.database.ktx) // Realtime Database
    implementation(libs.firebase.crashlytics.ktx) // Crashlytics
    implementation(libs.firebase.analytics.ktx) // Analytics
    implementation(libs.firebase.auth.ktx) // Firebase Auth per autenticazione
    implementation(libs.firebase.auth)

    // Networking
    implementation(libs.okhttp)  // Versione OkHttp per le richieste
    implementation(libs.json)    // Libreria JSON per parsing

    // Glide
    implementation(libs.glide)
    annotationProcessor(libs.glide.compiler)

    // Jetpack Navigation Compose
    implementation(libs.androidx.navigation.compose)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    // Debugging
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.kotlinx.serialization.json)
}
