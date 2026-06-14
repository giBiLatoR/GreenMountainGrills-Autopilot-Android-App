// Top-level build file. Plugin versions live in gradle/libs.versions.toml.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// Optional: redirect all build OUTPUT to a local disk. Useful when the repo
// lives on a network share (SMB/NFS), where Gradle's incremental file
// hashing/locking is unreliable. Opt in with:
//   ./gradlew -Pgmg.localBuildDir=C:/gmg-build assembleDebug
// Leaving it unset uses the normal per-module build/ dir (fine for local disks).
val localBuildBase = providers.gradleProperty("gmg.localBuildDir").orNull
if (!localBuildBase.isNullOrBlank()) {
    allprojects {
        layout.buildDirectory.set(file("$localBuildBase/${project.name}"))
    }
}
