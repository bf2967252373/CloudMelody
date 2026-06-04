plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.cloudmelody"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cloudmelody"
        minSdk = 21
        targetSdk = 34
        versionCode = 4
        versionName = "1.0.4"
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sign with debug key for CI builds
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/NOTICE",
                "DebugProbesKt.bin"
            )
        }
    }
}

dependencies {
    val okhttp    = "4.12.0"
    val coil      = "2.6.0"
    val coroutines = "1.8.0"
    val lifecycle  = "2.8.0"

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    // Networking
    implementation("com.squareup.okhttp3:okhttp:$okhttp")
    // Image loading
    implementation("io.coil-kt:coil:$coil")
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutines")
    // Lifecycle / ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycle")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycle")
    // Media notification
    implementation("androidx.media:media:1.7.0")
}
