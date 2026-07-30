// 顶层构建脚本：仅声明插件版本，不在此处应用（apply false）
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    // Kotlin 2.0 起，Compose 编译器改由独立插件提供
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
    // CARE_MIN：把 hidden stub（ActivityManagerHidden 等）重写成框架真类
    id("dev.rikka.tools.refine") version "4.4.0" apply false
}
