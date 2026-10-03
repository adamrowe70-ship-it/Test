pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
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
