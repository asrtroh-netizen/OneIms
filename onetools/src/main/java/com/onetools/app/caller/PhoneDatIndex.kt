package com.onetools.app.caller

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Binary index in the common phone.dat layout (7-digit prefix → province/city/carrier).
 * Used with MIT-licensed dataset shipped as assets/caller/geo.dat.
 */
internal object PhoneDatIndex {
    private const val INDEX_LEN = 9
    private const val INT_LEN = 4

    fun lookup(data: ByteArray, phone11: String): CnMobileGeo.Hit? {
        if (data.size < 8 || phone11.length < 7) return null
        val firstOffset = leInt(data, INT_LEN)
        if (firstOffset <= 0 || firstOffset >= data.size) return null
        val seven = phone11.substring(0, 7).toIntOrNull() ?: return null
        var left = 0
        var right = (data.size - firstOffset) / INDEX_LEN - 1
        while (left <= right) {
            val mid = (left + right) ushr 1
            val offset = firstOffset + mid * INDEX_LEN
            if (offset + INDEX_LEN > data.size) break
            val cur = leInt(data, offset)
            when {
                cur > seven -> right = mid - 1
                cur < seven -> left = mid + 1
                else -> {
                    val recordOffset = leInt(data, offset + INT_LEN)
                    val cardType = data[offset + INT_LEN * 2].toInt() and 0xff
                    if (recordOffset < 0 || recordOffset >= data.size) return null
                    var end = recordOffset
                    while (end < data.size && data[end] != 0.toByte()) end++
                    val record = String(data, recordOffset, (end - recordOffset).coerceAtLeast(0), Charsets.UTF_8)
                    val parts = record.split('|')
                    if (parts.size < 2) return null
                    return CnMobileGeo.Hit(
                        province = parts.getOrElse(0) { "" },
                        city = parts.getOrElse(1) { "" },
                        carrier = cardTypeName(cardType),
                    )
                }
            }
        }
        return null
    }

    fun versionLabel(data: ByteArray): String =
        if (data.size >= 4) String(data, 0, 4, Charsets.US_ASCII) else ""

    private fun leInt(data: ByteArray, at: Int): Int =
        ByteBuffer.wrap(data, at, 4).order(ByteOrder.LITTLE_ENDIAN).int

    private fun cardTypeName(type: Int): String = when (type) {
        0x01 -> "中国移动"
        0x02 -> "中国联通"
        0x03 -> "中国电信"
        0x04 -> "中国电信虚拟运营商"
        0x05 -> "中国联通虚拟运营商"
        0x06 -> "中国移动虚拟运营商"
        0x07 -> "中国广电"
        0x08 -> "中国广电虚拟运营商"
        else -> "未知运营商"
    }
}
