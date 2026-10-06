// Root build file. Per-module configuration lives in app/build.gradle.kts.
// Plugin versions are declared once via the version catalog (gradle/libs.versions.toml)
// and applied with apply false here so app/build.gradle.kts can `alias(...)` them.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
}

subprojects {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin" && requested.name == "kotlin-stdlib") {
                useVersion(libs.versions.kotlin.get())
                because("Keep Kotlin stdlib aligned with the pinned Kotlin compiler")
            }
        }
    }
}
