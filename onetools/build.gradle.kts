plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.onetools.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.onetools.app"
        minSdk = 31
        targetSdk = 36
        versionCode = 3
        versionName = "0.2.1"
        buildConfigField(
            "String",
            "ONE_CDN_INDEX_URL",
            "\"https://cdn.oneims.app/onetools/one-update.json\"",
        )
        buildConfigField(
            "String",
            "ONE_BLOCKLIST_URL",
            "\"https://raw.githubusercontent.com/asrtroh-netizen/OneBlock/main/phone/one-blocklist.json\"",
        )
        // Telo-shaped spam pack manifest (has_update/download_url/checksum). Empty CDN → UI can
        // still install from OneBlock JSON into onespam.db.
        buildConfigField(
            "String",
            "ONE_SPAM_SYNC_MANIFEST_URL",
            "\"https://github.com/asrtroh-netizen/OneBlock/releases/download/onetools-cdn-assets/spam-sync.json\"",
        )
        // Optional live query endpoint; blank disables network spam lookup.
        buildConfigField("String", "ONE_CALLER_QUERY_URL", "\"\"")
        buildConfigField("boolean", "ONE_INDEX_REQUIRE_SIGNATURE", "true")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
