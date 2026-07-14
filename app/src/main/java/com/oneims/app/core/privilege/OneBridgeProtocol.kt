package com.oneims.app.core.privilege

import android.os.IBinder

/** 与 bridge 模块 [com.oneims.bridge.server.BridgeProtocol] 保持字段同步。 */
object OneBridgeProtocol {
    const val DESCRIPTOR: String = "com.oneims.bridge.IOneBridge"
    const val PROVIDER_AUTHORITY: String = "com.oneims.app.onebridge"
    const val BRIDGE_PACKAGE: String = "com.oneims.bridge"

    const val TRANSACTION_PING: Int = IBinder.FIRST_CALL_TRANSACTION
    const val TRANSACTION_GET_UID: Int = IBinder.FIRST_CALL_TRANSACTION + 1
    const val TRANSACTION_CHECK_PERMISSION: Int = IBinder.FIRST_CALL_TRANSACTION + 2
    const val TRANSACTION_TRANSACT_REMOTE: Int = IBinder.FIRST_CALL_TRANSACTION + 3
}
