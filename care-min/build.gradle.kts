plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.oneims.caremin"
    compileSdk = 36

    defaultConfig {
        minSdk = 31
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        aidl = true
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("main") {
            java.srcDirs(
                "src/main/java",
                "vendor/server/java",
                "vendor/common/java",
                "vendor/starter/java",
                "vendor/shared/java",
                "vendor/server-shared/java",
                "vendor/rish/java",
                "vendor/stubs/java",
            )
            aidl.srcDirs("vendor/aidl")
        }
    }
}

configurations.configureEach {
    resolutionStrategy {
        force("androidx.core:core:1.13.1")
        force("androidx.core:core-ktx:1.13.1")
    }
}

dependencies {
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.annotation:annotation:1.8.2")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("dev.rikka.rikkax.parcelablelist:parcelablelist:2.0.1")
    implementation("dev.rikka.rikkax.core:core-ktx:1.4.1") {
        exclude(group = "androidx.core")
    }
    implementation("dev.rikka.hidden:compat:4.4.0")
    compileOnly("dev.rikka.hidden:stub:4.4.0")
    implementation("dev.rikka.tools.refine:runtime:4.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
