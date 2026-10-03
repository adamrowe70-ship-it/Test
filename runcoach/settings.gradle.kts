pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    // Versions live here; each module applies only the plugins it needs, so `:core`
    // never has to download the Android Gradle plugin.
    plugins {
        kotlin("jvm") version "2.1.0"
        kotlin("android") version "2.1.0"
        kotlin("plugin.serialization") version "2.1.0"
        kotlin("plugin.compose") version "2.1.0"
        id("com.android.application") version "8.13.0"
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "runcoach"
include(":core")

// The Android app needs the Android SDK. Include it only when one is configured
// (Android Studio writes local.properties), so `:core` can build and test anywhere.
val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
    file("local.properties").let { it.exists() && it.readText().contains("sdk.dir") }
if (hasAndroidSdk) include(":app")
