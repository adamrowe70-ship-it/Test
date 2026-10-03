import java.util.Properties

plugins {
    id("com.android.application") version "8.7.3"
    kotlin("android")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
}

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "runcoach.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "runcoach.app"
        minSdk = 28 // Health Connect requires Android 9+
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
        // Your app's Client ID from developer.spotify.com/dashboard, set in local.properties.
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"${localProps.getProperty("spotify.clientId", "")}\"")
        manifestPlaceholders["redirectScheme"] = "runcoach"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core"))

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.browser:browser:1.8.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
