package com.oneims.app.core.privilege

/**
 * OneLink 桩：外置 Shizuku，不使用内循环引擎切换。
 * 与 onekuku 同包同名，仅保证 shared 引用可编译。
 */
enum class ChannelEngine {
    ONEBRIDGE,
    ;

    companion object {
        const val BUILD_ONEBRIDGE: String = "ONEBRIDGE"
        const val PROCESS_ONEBRIDGE: String = "onebridge_server"

        fun current(): ChannelEngine = ONEBRIDGE

        fun processNiceName(engine: ChannelEngine = current()): String =
            PROCESS_ONEBRIDGE
    }
}
