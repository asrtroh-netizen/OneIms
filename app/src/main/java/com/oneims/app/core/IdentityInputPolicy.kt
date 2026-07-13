package com.oneims.app.core

/** 运营商显示名属于系统状态栏短文案，限制长度与控制字符可避免不可见或截断值写入系统。 */
object IdentityInputPolicy {
    const val MAX_CARRIER_NAME_CODE_POINTS = 32
    const val MAX_IMS_USER_AGENT_CODE_POINTS = 128

    enum class Error {
        CONTROL_CHARACTER,
        TOO_LONG,
    }

    fun normalize(value: String): String = value.trim()

    fun carrierNameError(value: String): Error? =
        validate(normalize(value), MAX_CARRIER_NAME_CODE_POINTS)

    fun imsUserAgentError(value: String): Error? =
        validate(normalize(value), MAX_IMS_USER_AGENT_CODE_POINTS)

    private fun validate(value: String, maxCodePoints: Int): Error? = when {
        value.any(Char::isISOControl) -> Error.CONTROL_CHARACTER
        value.codePointCount(0, value.length) > maxCodePoints -> Error.TOO_LONG
        else -> null
    }
}
