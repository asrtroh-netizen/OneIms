package com.oneims.app.core.privilege

/**
 * OneLink 桩：外置 Shizuku，不使用内循环 [ChannelEngine] 切换。
 * 与 onekuku 同包同名，仅保证 shared 单测/引用可编译。
 */
enum class ChannelEngine {
    ONEBRIDGE,
    CARE_MIN,
    ;

    companion object {
        const val BUILD_ONEBRIDGE: String = "ONEBRIDGE"
        const val BUILD_CARE_MIN: String = "CARE_MIN"
        const val PROCESS_ONEBRIDGE: String = "onebridge_server"
        const val PROCESS_CARE_MIN: String = "onekuku_server"

        fun current(): ChannelEngine = ONEBRIDGE

        fun processNiceName(engine: ChannelEngine = current()): String =
            when (engine) {
                ONEBRIDGE -> PROCESS_ONEBRIDGE
                CARE_MIN -> PROCESS_CARE_MIN
            }
    }
}
