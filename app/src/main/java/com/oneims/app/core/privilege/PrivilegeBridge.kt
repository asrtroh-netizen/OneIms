package com.oneims.app.core.privilege

import android.os.IBinder

/**
 * OneIMS 特权通道唯一客户端契约。
 *
 * 业务与 [com.oneims.app.core.SystemApiBroker] 只认本接口；底层可切换
 * Shizuku 回落 / 自研 OneBridge，而不扩散第三方 API。
 *
 * MVP 能力面冻结见 `docs/design/2026-07-15-onebridge-privilege-min.md` §0。
 */
interface PrivilegeBridge {
    fun isRunning(): Boolean

    fun isGranted(): Boolean

    fun isReady(): Boolean = isRunning() && isGranted()

    fun requestPermission(requestCode: Int = DEFAULT_REQUEST_CODE)

    /** shell/root 通道 uid；不可用时返回 -1。 */
    fun getUid(): Int

    /**
     * 以特权进程身份包装指定 system service binder。
     * MVP 仅允许：activity / carrier_config / isub / phone。
     */
    fun wrapSystemService(name: String): IBinder

    companion object {
        const val DEFAULT_REQUEST_CODE: Int = 4370

        /** MVP 允许包装的 system service 名（膨胀闸门）。 */
        val MVP_SYSTEM_SERVICES: Set<String> = setOf(
            "activity",
            "carrier_config",
            "isub",
            "phone",
        )
    }
}
