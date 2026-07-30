package com.oneims.caremin

/**
 * 宿主内嵌 Shizuku MINI（CARE_MIN）契约常量。
 *
 * 邻仓试验田包名是 `com.onekuku.care`；融进 OneIMS 后 Manager/白名单必须是宿主
 * [HOST_APPLICATION_ID]，进程名用 [PROCESS_NICE_NAME] 避开 Plus/Care 的 `shizuku_plus_server`。
 */
object CareMinHostConstants {
    const val HOST_APPLICATION_ID: String = "com.oneims.app"

    /** app_process --nice-name */
    const val PROCESS_NICE_NAME: String = "onekuku_server"

    /** 与分割版 Lite 同构的 server 入口（类需打进宿主 APK / :care-min）。 */
    const val SERVER_ENTRY_CLASS: String = "rikka.shizuku.server.ShizukuService"

    const val PROVIDER_AUTHORITY_SUFFIX: String = ".shizuku"

    fun providerAuthority(applicationId: String = HOST_APPLICATION_ID): String =
        "$applicationId$PROVIDER_AUTHORITY_SUFFIX"
}
