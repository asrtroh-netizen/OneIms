package com.oneims.app.core.privilege

/**
 * OneKuku 内循环通道引擎（单一真源：OneBridge）。
 *
 * 迷你版 CARE_MIN / `onekuku_server` 融合线已清除；勿再引入第二套引擎开关。
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
