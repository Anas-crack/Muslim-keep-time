plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.dynamicisland.redminote8"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dynamicisland.redminote8"
        minSdk = 26
        targetSdk = 34
        versionCode = 13
        versionName = "1.1.2"
        setProperty("archivesBaseName", "Dynamic Island")
    }

    signingConfigs {
        create("permanent") {
            storeFile = file("dynamic-island.keystore")
            storePassword = "DynamicIsland2026!"
            keyAlias = "dynamicisland"
            keyPassword = "DynamicIsland2026!"
        }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("permanent")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("permanent")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
