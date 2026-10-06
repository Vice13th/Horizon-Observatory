import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "horizon.observatory"
    compileSdk = 36

    defaultConfig {
        applicationId = "horizon.observatory"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0-observatory"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
                arguments["room.incremental"] = "true"
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get() }

    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // Installation-identity flavors ONLY -- both flavors compile the exact same source set,
    // same Kotlin code, same Compose UI, same dependencies, same behavior. The sole purpose is
    // producing a second Android Package Manager identity so the same app can be installed
    // twice on one device. "primary" is the existing, unchanged app: no applicationIdSuffix, so
    // its release build keeps applicationId exactly "horizon.observatory" as before this change.
    flavorDimensions += "installation"
    productFlavors {
        create("primary") {
            dimension = "installation"
            manifestPlaceholders["appLabel"] = "Horizon Observatory"
        }
        create("ts") {
            dimension = "installation"
            applicationIdSuffix = ".ts"
            manifestPlaceholders["appLabel"] = "Horizon Observatory TS"
        }
        create("t1") {
            dimension = "installation"
            applicationIdSuffix = ".t1"
            manifestPlaceholders["appLabel"] = "HORIZON T1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    sourceSets["main"].kotlin.srcDirs("src/main/kotlin")
    // Room exported schemas are served to instrumentation tests (MigrationTestHelper).
    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.4.0")
    implementation(project(":propagation-contract"))

    // OrbitCore bridge is intentionally T1-only; primary/ts remain untouched.
    add("t1Implementation", project(":orbitcore-bridge"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    // Local JVM tests execute outside Android's framework implementation. Provide a real JVM JSONObject implementation.
    testImplementation(libs.org.json)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}
