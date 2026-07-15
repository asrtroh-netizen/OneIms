plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val oneImsVersionName = "2.0.23"

android {
    namespace = "com.oneims.app"
    // Android 16（API 36）。Android 17 / API 37 需先安装 platforms;android-37 后再升。
    compileSdk = 36

    defaultConfig {
        applicationId = "com.oneims.app"
        minSdk = 31          // Tensor Pixel（Pixel 6 起）最低 Android 12
        targetSdk = 36
        versionCode = 32
        versionName = oneImsVersionName
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    // HiddenApiBypass 会触发 Play 依赖元数据审查；本应用不走商店分发，直接关掉上报。
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    // Phase4：OneBridge starter 以 library 打进主包，不再要求安装 com.oneims.bridge
    implementation(project(":bridge"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // 访问 hidden API（绕过 Android 隐藏 API 反射限制；6.x 起明确覆盖 Android 17）
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")

    // 协程（IO 线程执行阻塞的 provisioning / 网络自检）
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // 原生内嵌 ADB（libadb-android：无线配对 + shell；Java 库，兼容 Kotlin 2.0.21）
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    implementation("org.conscrypt:conscrypt-android:2.5.3")
    implementation("com.github.MuntashirAkon:sun-security-android:1.1")

    testImplementation("junit:junit:4.13.2")
}

/**
 * 保留标准 app-debug.apk 供 Android 工具链消费，同时在项目根目录生成辨识度高的真机测试包。
 * 输入/输出精确到单个文件，避免宽泛的 Copy 目录输出与 Lint 中间产物产生隐式依赖。
 */
tasks.register("packageNamedDebugApk") {
    dependsOn("assembleDebug")
    val sourceApk = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk")
    val namedApk = rootProject.layout.projectDirectory.file(
        "OneIms-$oneImsVersionName.apk",
    )
    inputs.file(sourceApk)
    outputs.file(namedApk)
    doLast {
        sourceApk.get().asFile.copyTo(namedApk.asFile, overwrite = true)
    }
}
