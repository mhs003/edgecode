import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// The version lives outside this file so the release workflow can read it
// without parsing Gradle, and so bumping it is one command rather than two
// edits that can drift apart.
val appVersion = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}

// A release build is only truly releasable when it is signed. CI supplies a
// keystore through the environment; when there is none — a local build, or a
// fork without the secrets — the debug key is used instead, which still
// produces an installable APK rather than an unsigned one.
val releaseKeystore = System.getenv("KEYSTORE_PATH")
    ?.let { file(it) }
    ?.takeIf { it.exists() }

android {
    namespace = "dev.edgecode"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "dev.edgecode"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersion.getProperty("versionCode").toInt()
        versionName = appVersion.getProperty("versionName")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            releaseKeystore?.let { keystore ->
                storeFile = keystore
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 is what makes the release build actually fast: it removes the
            // debug instrumentation and shrinks the Compose call graph. Without
            // it the "release" build performs like debug.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (releaseKeystore != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

// Kotlin is compiled by AGP's built-in Kotlin support (AGP 9+); the JVM target
// defaults to android.compileOptions.targetCompatibility, so it is not set here.

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
