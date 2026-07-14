package com.oneims.bridge.server

import android.os.IBinder

/**
 * OneBridge 手写 Binder 契约（与 OneIMS [com.oneims.app.core.privilege.OneBridgeProtocol] 必须同步）。
 */
object BridgeProtocol {
    const val DESCRIPTOR: String = "com.oneims.bridge.IOneBridge"
    const val CLIENT_PACKAGE: String = "com.oneims.app"
    const val CLIENT_PROVIDER_AUTHORITY: String = "com.oneims.app.onebridge"

    const val TRANSACTION_PING: Int = IBinder.FIRST_CALL_TRANSACTION
    const val TRANSACTION_GET_UID: Int = IBinder.FIRST_CALL_TRANSACTION + 1
    const val TRANSACTION_CHECK_PERMISSION: Int = IBinder.FIRST_CALL_TRANSACTION + 2
    const val TRANSACTION_TRANSACT_REMOTE: Int = IBinder.FIRST_CALL_TRANSACTION + 3

    val MVP_SERVICES: Set<String> = setOf(
        "activity",
        "carrier_config",
        "isub",
        "phone",
    )
}
