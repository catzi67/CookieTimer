// app/build.gradle.kts

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Explicitly apply KSP plugin with a compatible version for Kotlin 1.9.20
    id("com.google.devtools.ksp") version "1.9.20-1.0.14"
}

android {
    namespace = "com.catto.cookietimer"
    compileSdk = libs.versions.compileSdk.get().toInt() // Reference compileSdk from libs.versions.toml

    defaultConfig {
        applicationId = "com.catto.cookietimer"
        minSdk = libs.versions.minSdk.get().toInt()     // Reference minSdk from libs.versions.toml
        targetSdk = libs.versions.targetSdk.get().toInt() // Reference targetSdk from libs.versions.toml
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11 // Updated from VERSION_1_8 to VERSION_11
        targetCompatibility = JavaVersion.VERSION_11 // Updated from VERSION_1_8 to VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11" // Updated from "1.8" to "11"
    }
}

dependencies {
    // AndroidX Core and AppCompat
    implementation(libs.androidx.core.ktx)
    implementation(libs.appcompat)
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Material Design
    implementation(libs.material)

    // RecyclerView dependency
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.cardview)

    // Room components
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
