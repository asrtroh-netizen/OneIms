package com.oneims.app.core.privilege

import com.oneims.app.BuildConfig

/**
 * OneKuku 内循环通道引擎选择点（单一真源）。
 *
 * - [ONEBRIDGE]：现网内嵌 `onebridge_server`（默认，迁移窗）
 * - [CARE_MIN]：宿主内嵌 Care/Shizuku server 最小面（P3 目标态；见
 *   `docs/architecture/2026-07-30-onekuku-care-home-fusion.md`）
 *
 * 默认必须为 [ONEBRIDGE]，直到 CARE_MIN 真机验收通过再切 BuildConfig。
 */
enum class ChannelEngine {
    ONEBRIDGE,
    CARE_MIN,
    ;

    companion object {
        const val BUILD_ONEBRIDGE: String = "ONEBRIDGE"
        const val BUILD_CARE_MIN: String = "CARE_MIN"

        /** 进程 nice-name：CARE_MIN 用独立名，避免与已装 Plus/Care 的 `shizuku_plus_server` 冲突。 */
        const val PROCESS_ONEBRIDGE: String = "onebridge_server"
        const val PROCESS_CARE_MIN: String = "onekuku_server"

        fun current(): ChannelEngine =
            when (BuildConfig.CHANNEL_ENGINE) {
                BUILD_CARE_MIN -> CARE_MIN
                else -> ONEBRIDGE
            }

        fun processNiceName(engine: ChannelEngine = current()): String =
            when (engine) {
                ONEBRIDGE -> PROCESS_ONEBRIDGE
                CARE_MIN -> PROCESS_CARE_MIN
            }
    }
}
