import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Compose plugin + BOM land together (Phase 1 lesson: the Compose compiler
    // requires the Compose runtime on the classpath once Kotlin sources exist)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.justbnutz.dockorientationrotatorlator"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.justbnutz.dockorientationrotatorlator"
        // 31 (Android 12): sole active user is on Android 17 devices — this deletes the
        // pre-Oreo receiver path and every pre-31 version gate (overhaul Decision 1)
        minSdk = 31
        targetSdk = 35
        // versionCode: plain +1 per release. versionName: build timestamp to the
        // minute, vYYYYMMDD.HHMM — the only version label that matters here.
        versionCode = 7
        versionName = "v20260712.2201"
    }

    signingConfigs {
        create("release") {
            // Populated from CI env (see .gitea/workflows/release.yml). Left unset for
            // local debug builds so they don't require the keystore.
            val ksPath = System.getenv("KEYSTORE_PATH")
            if (ksPath != null) {
                storeFile = file(ksPath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Distinct appId so debug builds install ALONGSIDE the Play build instead of
            // fighting its signature. Note the debug app gets its own permission grants
            // (WRITE_SETTINGS, POST_NOTIFICATIONS) — expect the full first-run flow.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")

            // Only wire the signing config when the CI keystore env is present —
            // without it assembleRelease still runs (unsigned), so every debug CI
            // push also exercises R8 (see build.yml's release step)
            if (System.getenv("KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
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

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)

    // Compose (Material 3). NOTE: the 2018 AVDs render via AndroidView/ImageView —
    // compose animation-graphics couldn't play them faithfully (subset AVD support)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Architecture: DataStore + Flows + ViewModel
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
