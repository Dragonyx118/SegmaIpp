pluginManagement {
    repositories {
        gradlePluginPortal()  // Messo per primo per assicurare che i plugin vengano risolti correttamente
        google()              // Repository di Google
        mavenCentral()        // Repository Maven Central
    }
    plugins {
        id("com.android.application") version "8.13.0" apply false
        id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Serra"
include(":app")
