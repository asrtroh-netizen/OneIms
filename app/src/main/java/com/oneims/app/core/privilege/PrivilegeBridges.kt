package com.oneims.app.core.privilege

/**
 * 进程内特权桥单一真源。优先 OneBridge，回落 Shizuku。
 *
 * Phase2：产品面只认本对象；运行时回落由 [FallbackPrivilegeBridge] 完成。
 * 不另加 BuildConfig 开关——关回落等于拆掉安全网，等 Phase1 真机验收后再考虑。
 */
object PrivilegeBridges {
    @Volatile
    var current: PrivilegeBridge = FallbackPrivilegeBridge(
        primary = OneBridgePrivilegeBridge(),
        fallback = ShizukuPrivilegeBridge(),
    )
}
