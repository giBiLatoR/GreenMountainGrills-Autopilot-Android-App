// Top-level build file. Plugin versions live in gradle/libs.versions.toml.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// The repo lives on an SMB network drive (X:). Gradle's incremental file
// hashing/locking is unreliable on network shares, so all build OUTPUT is
// redirected to a local disk. Sources stay on X:; only build/ moves.
val localBuildBase = providers.gradleProperty("gmg.localBuildDir").orNull ?: "C:/gmg-build"
allprojects {
    layout.buildDirectory.set(file("$localBuildBase/${project.name}"))
}
