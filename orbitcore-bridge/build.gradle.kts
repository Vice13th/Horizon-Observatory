import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "horizon.observatory.orbitcore"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
}

dependencies {
    implementation(project(":propagation-contract"))
    testImplementation(libs.junit)
    // Deliberately runtime-only: the bridge talks to OrbitCore through reflection so OrbitCore's
    // Kotlin metadata never enters this module's Kotlin compile classpath or the app's KAPT boundary.
    runtimeOnly("com.parodison:orbit-core:0.1.0")
    // OrbitCore 0.1.0 is published with Kotlin 2.4.0 and exposes kotlin.time.Instant.
    // Keep the runtime ABI required by OrbitCore instead of resolving its stdlib back to 1.9.24.
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.4.0")
}
