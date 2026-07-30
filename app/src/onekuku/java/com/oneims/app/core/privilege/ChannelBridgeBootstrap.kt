package com.oneims.app.core.privilege

/**
 * OneKuku 产品线：内循环通道注入点。
 *
 * 默认 [ChannelEngine.ONEBRIDGE]；[ChannelEngine.CARE_MIN] 走宿主侧
 * [ShizukuPrivilegeBridge]（与 onelink 同构客户端；server 最小面进 APK 见 P3a 白名单）。
 */
object ChannelBridgeBootstrap {
    fun create(): PrivilegeBridge =
        when (ChannelEngine.current()) {
            ChannelEngine.ONEBRIDGE -> OneBridgePrivilegeBridge()
            ChannelEngine.CARE_MIN -> ShizukuPrivilegeBridge()
        }
}
