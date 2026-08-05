plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("dev.rikka.tools.refine")
}

val oneImsVersionName = "3.2.0"

android {
    namespace = "com.oneims.app"
    // Android 16（API 36）。Android 17 / API 37 需先安装 platforms;android-37 后再升。
    compileSdk = 36

    defaultConfig {
        applicationId = "com.oneims.app"
        minSdk = 31          // Tensor Pixel（Pixel 6 起）最低 Android 12
        targetSdk = 36
        versionCode = 90
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
            // 内循环固定 OneBridge（onebridge_server）；迷你版 CARE_MIN 已清除。
            buildConfigField("String", "CHANNEL_ENGINE", "\"ONEBRIDGE\"")
        }
        create("onelink") {
            dimension = "channel"
            applicationId = "com.oneims.onelink"
            versionNameSuffix = "-onelink"
            buildConfigField("String", "CHANNEL_LINE", "\"onelink\"")
            buildConfigField("boolean", "CHANNEL_USES_EMBEDDED_BRIDGE", "false")
            // onelink 走外置 Shizuku；此字段仅占位
            buildConfigField("String", "CHANNEL_ENGINE", "\"EXTERNAL_SHIZUKU\"")
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
        jniLibs {
            useLegacyPackaging = true
        }
    }

    // HiddenApiBypass 会触发 Play 依赖元数据审查；本应用不走商店分发，直接关掉上报。
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

configurations.configureEach {
    resolutionStrategy {
        force("androidx.core:core:1.13.1")
        force("androidx.core:core-ktx:1.13.1")
    }
}

dependencies {
    // OneKuku ????????OneBridge starter + ????? ADB??OneLink ??????????src/onelink ???
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

    // Shizuku API：onelink 外置通道；onekuku 因 main 源码仍含 ShizukuPrivilegeBridge 类需编译依赖（运行时 OneBridge 不走它）。
    "onelinkImplementation"("dev.rikka.shizuku:api:13.1.5")
    "onelinkImplementation"("dev.rikka.shizuku:provider:13.1.5")
    "onekukuImplementation"("dev.rikka.shizuku:api:13.1.5")
    "onekukuImplementation"("dev.rikka.shizuku:provider:13.1.5")

    // ???? hidden API??????Android ?????? API ???????????.x ??????????Android 17??
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")

    // ?????IO ????????????????provisioning / ???????????
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
}

/**
 * ????????????assemble{Flavor}Debug ??????????????????????????????
 * ???????????????????????????????????????????
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
            // Release ???????????? -debug ??????
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
 * ?????????????????debug????????????? OneKuku + OneLink ???????????????????Release???
 * ??scripts/publish-dual-readme-release.ps1
 */
tasks.register("packageDualDebugApks") {
    dependsOn("packageNamedOnekukuDebugApk", "packageNamedOnelinkDebugApk")
    description = "Build both OneKuku and OneLink named debug APKs (run only when packaging is authorized)."
}

/**
 * ???????????????????? OneKuku ??debug ??????????
 */
tasks.register("packageNamedDebugApk") {
    dependsOn("packageNamedOnekukuDebugApk")
}

