plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "kr.local.galaxybattery"
    compileSdk = 36
    defaultConfig {
        applicationId = "kr.local.galaxybattery"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "0.5.2"
    }
    // The tiny generated Binder interface is checked in: native aidl cannot handle
    // all Unicode Windows paths. Keep its source .aidl as the contract.
    buildFeatures { aidl = false }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
    signingConfigs {
        getByName("debug") {
            val localKey = rootProject.file(".tools/diagnostic.keystore")
            if (localKey.exists()) {
                storeFile = localKey
                storePassword = "android"
                keyAlias = "diagnostic"
                keyPassword = "android"
            }
        }
    }
}

dependencies {
    implementation(fileTree("libs") { include("*.jar") })
    implementation(kotlin("stdlib", "2.1.21"))
}
