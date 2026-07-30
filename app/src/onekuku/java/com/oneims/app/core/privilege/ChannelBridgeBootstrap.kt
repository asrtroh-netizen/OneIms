package com.oneims.app.core.privilege

import android.util.Log

/**
 * OneKuku 产品线：内循环通道注入点。
 *
 * 默认 [ChannelEngine.ONEBRIDGE]；P3b 将 [ChannelEngine.CARE_MIN] 接到宿主内嵌
 * Shizuku 最小 server（内置 Shizuku MINI / OneKuku 增强），业务门面仍是 [PrivilegeBridge]。
 */
object ChannelBridgeBootstrap {
    private const val TAG = "ChannelBridgeBootstrap"

    fun create(): PrivilegeBridge =
        when (ChannelEngine.current()) {
            ChannelEngine.ONEBRIDGE -> OneBridgePrivilegeBridge()
            ChannelEngine.CARE_MIN -> createCareMinBridge()
        }

    /**
     * CARE_MIN 客户端尚未接线（需宿主 Provider + server 最小面）。
     * 旗标误开时回落 OneBridge，避免半截引擎把主路径打挂。
     */
    private fun createCareMinBridge(): PrivilegeBridge {
        // P3b：改为返回宿主侧 ShizukuPrivilegeBridge（或同构适配），并停用本回落。
        Log.w(TAG, "CHANNEL_ENGINE=CARE_MIN not wired yet; falling back to OneBridge")
        return OneBridgePrivilegeBridge()
    }
}
