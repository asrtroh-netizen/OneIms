package com.oneims.app.core.privilege

import android.os.IBinder

/** 与 bridge 模块 [com.oneims.bridge.server.BridgeProtocol] 保持字段同步。 */
object OneBridgeProtocol {
    const val DESCRIPTOR: String = "com.oneims.bridge.IOneBridge"
    const val PROVIDER_AUTHORITY: String = "com.oneims.app.onebridge"
    /** Phase4：通道内嵌主包；旧独立桥包名仅作文档对照。 */
    const val BRIDGE_PACKAGE: String = "com.oneims.app"

    const val TRANSACTION_PING: Int = IBinder.FIRST_CALL_TRANSACTION
    const val TRANSACTION_GET_UID: Int = IBinder.FIRST_CALL_TRANSACTION + 1
    const val TRANSACTION_CHECK_PERMISSION: Int = IBinder.FIRST_CALL_TRANSACTION + 2
    const val TRANSACTION_TRANSACT_REMOTE: Int = IBinder.FIRST_CALL_TRANSACTION + 3
}
