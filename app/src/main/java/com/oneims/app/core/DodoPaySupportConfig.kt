package com.oneims.app.core

/**
 * DodoPay「支持作者」公开配置。
 *
 * 只放可公开的链接模板 / 验证基址 / app_id，**绝不**放 API Key 或商户私钥。
 * 未配置时 UI 显示友好提示，不崩溃、不打开空链。
 */
object DodoPaySupportConfig {
    /**
     * 公开支付页模板。可为完整 URL，也可带 query 占位；客户端会追加公开参数。
     * 例：`https://pay.example.com/oneims` —— 先留空，部署时填入真实 DodoPay 链接。
     */
    const val SUPPORT_URL_TEMPLATE: String = ""

    /**
     * 公开 proof 验证基址（不含 path）。最终请求：
     * `{BASE}/api/public/payment-proofs/{payment_proof}`
     */
    const val PROOF_VERIFY_BASE_URL: String = ""

    /** 可选公开留言墙 JSON feed；空则隐藏支持记录区。 */
    const val SUPPORT_FEED_URL: String = ""

    /** 验证响应中的 app_id 期望值。 */
    const val APP_ID: String = "oneims"

    const val PROOF_KEY: String = "support_unlock"
    const val SOURCE: String = "oneims_android"
    const val CHANNEL: String = "android"
    const val RETURN_MODE: String = "close"

    /** 最低可记支持金额（元）。 */
    const val MIN_AMOUNT: Double = 1.0

    /** 金额上限（元）。 */
    const val MAX_AMOUNT: Double = 999.0

    /** Deep link：支付页关闭后可回跳本 App。 */
    const val CALLBACK_URI: String = "oneims://support/callback"

    fun isSupportUrlConfigured(): Boolean = SUPPORT_URL_TEMPLATE.isNotBlank()

    fun isVerifyConfigured(): Boolean = PROOF_VERIFY_BASE_URL.isNotBlank()

    fun isFeedConfigured(): Boolean = SUPPORT_FEED_URL.isNotBlank()
}
