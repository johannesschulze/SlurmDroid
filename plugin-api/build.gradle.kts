plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.parcelize")
}

android {
    namespace = "org.slurmdroid.plugin.api"
    compileSdk = 36

    defaultConfig {
        minSdk = 31
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    jvmToolchain(11)
}