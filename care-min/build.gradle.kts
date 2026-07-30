plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("dev.rikka.tools.refine")
}

android {
    namespace = "com.oneims.caremin"
    compileSdk = 36
    ndkVersion = "27.0.12077973"

    defaultConfig {
        minSdk = 31
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=none")
            }
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
        prefab = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    externalNativeBuild {
        cmake {
            path = file("vendor/rish/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
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
    // rish CMake: find_package(cxx REQUIRED CONFIG)
    implementation("org.lsposed.libcxx:libcxx:27.0.12077973")
}
