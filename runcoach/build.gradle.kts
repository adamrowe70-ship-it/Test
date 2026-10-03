// The Android Gradle plugin is declared in app/build.gradle.kts so that `:core`
// builds without access to Google's Maven repository.
plugins {
    kotlin("jvm") version "2.1.0" apply false
    kotlin("android") version "2.1.0" apply false
    kotlin("plugin.serialization") version "2.1.0" apply false
    kotlin("plugin.compose") version "2.1.0" apply false
}
