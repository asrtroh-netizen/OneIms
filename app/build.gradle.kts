plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val oneImsVersionName = "2.3.0"

android {
    namespace = "com.oneims.app"
    // Android 16（API 36）。Android 17 / API 37 需先安装 platforms;android-37 后再升。
    compileSdk = 36

    defaultConfig {
        applicationId = "com.oneims.app"
        minSdk = 31          // Tensor Pixel（Pixel 6 起）最低 Android 12
        targetSdk = 36
        versionCode = 68
        versionName = oneImsVersionName
    }

    flavorDimensions += "channel"
    productFlavors {
        create("onekuku") {
            dimension = "channel"
            applicationId = "com.oneims.app"
            versionNameSuffix = "-onekuku"
            buildConfigField("String", "CHANNEL_LINE", "\"onekuku\"")
            buildConfigField("boolean", "CHANNEL_USES_EMBEDDED_BRIDGE", "true")
        }
        create("onelink") {
            dimension = "channel"
            applicationId = "com.oneims.onelink"
            versionNameSuffix = "-onelink"
            buildConfigField("String", "CHANNEL_LINE", "\"onelink\"")
            buildConfigField("boolean", "CHANNEL_USES_EMBEDDED_BRIDGE", "false")
        }
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
    // OneKuku 线专用：OneBridge starter + 内嵌 ADB（OneLink 不引入，见 src/onelink 桩）
    "onekukuImplementation"(project(":bridge"))
    "onekukuImplementation"("com.github.MuntashirAkon:libadb-android:3.1.1")
    "onekukuImplementation"("org.conscrypt:conscrypt-android:2.5.3")
    "onekukuImplementation"("com.github.MuntashirAkon:sun-security-android:1.1")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // OneLink 线专用：官方 Shizuku 客户端（onekuku 线不引入）
    "onelinkImplementation"("dev.rikka.shizuku:api:13.1.5")
    "onelinkImplementation"("dev.rikka.shizuku:provider:13.1.5")

    // 访问 hidden API（绕过 Android 隐藏 API 反射限制；6.x 起明确覆盖 Android 17）
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")

    // 协程（IO 线程执行阻塞的 provisioning / 网络自检）
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
}

/**
 * 单线命名包：assemble{Flavor}Debug 后复制到仓库根，便于真机辨认。
 * 默认不执行；需用户明确下令打包后再跑。
 */
fun registerNamedFlavorApk(flavor: String, brand: String) {
    val cap = flavor.replaceFirstChar { it.uppercase() }
    tasks.register("packageNamed${cap}DebugApk") {
        dependsOn("assemble${cap}Debug")
        val sourceApk = layout.buildDirectory.file("outputs/apk/$flavor/debug/app-$flavor-debug.apk")
        val namedApk = rootProject.layout.projectDirectory.file(
            "OneIms-$brand-$oneImsVersionName-debug.apk",
        )
        inputs.file(sourceApk)
        outputs.file(namedApk)
        doLast {
            val src = sourceApk.get().asFile
            src.copyTo(namedApk.asFile, overwrite = true)
            // Release 上传用名（无 -debug 后缀）
            val releaseApk = rootProject.layout.projectDirectory.file(
                "OneIms-$brand-$oneImsVersionName.apk",
            )
            src.copyTo(releaseApk.asFile, overwrite = true)
        }
    }
}

registerNamedFlavorApk("onekuku", "OneKuku-standalone")
registerNamedFlavorApk("onelink", "Lite-Shizuku")

/**
 * 双包生成入口（debug）。发版时 OneKuku + OneLink 必须一起打、一起上传 Release。
 * 见 scripts/publish-dual-readme-release.ps1
 */
tasks.register("packageDualDebugApks") {
    dependsOn("packageNamedOnekukuDebugApk", "packageNamedOnelinkDebugApk")
    description = "Build both OneKuku and OneLink named debug APKs (run only when packaging is authorized)."
}

/**
 * 兼容旧任务名：默认打 OneKuku 线 debug 命名包。
 */
tasks.register("packageNamedDebugApk") {
    dependsOn("packageNamedOnekukuDebugApk")
}

