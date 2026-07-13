package com.oneims.app.core

import android.content.Context
import androidx.annotation.StringRes
import com.oneims.app.R
import com.oneims.app.model.WfcMode

/**
 * 运营商画像：按 SIM 的 MCC/MNC 自动识别运营商，给出「该卡该开什么」的推荐配置。
 *
 * 目的：不同运营商、不同卡的 IMS 能力支持不一样，做到「不同卡不同机自动给对参数」，
 * 用户不用自己猜。识别不到时回退到通用推荐（保基本通信，稳妥开 VoLTE）。
 *
 * 名称/说明走字符串资源（[nameRes]/[noteRes]），展示时用 [name]/[note] 传 Context 解析，随系统语言切中/英。
 */
data class CarrierProfile(
    @StringRes val nameRes: Int,
    val nameArg: String?,
    val recommendVolte: Boolean,
    val recommendVowifi: Boolean,
    val recommendVonr: Boolean,
    val recommendWfcMode: WfcMode,
    @StringRes val noteRes: Int,
) {
    fun name(context: Context): String =
        if (nameArg != null) context.getString(nameRes, nameArg) else context.getString(nameRes)

    fun note(context: Context): String = context.getString(noteRes)
}

object CarrierProfiles {

    // 中国 MCC = 460
    private const val CN = "460"

    /** 三大运营商的 MNC 段（含虚拟运营商常见段）。 */
    private val CHINA_MOBILE = setOf("00", "02", "04", "07", "08", "13")   // 中国移动
    private val CHINA_UNICOM = setOf("01", "06", "09", "10")               // 中国联通
    private val CHINA_TELECOM = setOf("03", "05", "11", "12")              // 中国电信

    fun match(mcc: String, mnc: String): CarrierProfile {
        val mnc2 = mnc.padStart(2, '0')
        if (mcc == CN) {
            when (mnc2) {
                in CHINA_MOBILE -> return CarrierProfile(
                    nameRes = R.string.carrier_cmcc, nameArg = null,
                    recommendVolte = true, recommendVowifi = true, recommendVonr = true,
                    recommendWfcMode = WfcMode.CELLULAR_PREFERRED,
                    noteRes = R.string.carrier_note_cmcc,
                )
                in CHINA_UNICOM -> return CarrierProfile(
                    nameRes = R.string.carrier_cucc, nameArg = null,
                    recommendVolte = true, recommendVowifi = true, recommendVonr = true,
                    recommendWfcMode = WfcMode.CELLULAR_PREFERRED,
                    noteRes = R.string.carrier_note_cucc,
                )
                in CHINA_TELECOM -> return CarrierProfile(
                    nameRes = R.string.carrier_ctcc, nameArg = null,
                    recommendVolte = true, recommendVowifi = true, recommendVonr = true,
                    recommendWfcMode = WfcMode.CELLULAR_PREFERRED,
                    noteRes = R.string.carrier_note_ctcc,
                )
            }
            return CarrierProfile(
                nameRes = R.string.carrier_cn_unknown, nameArg = mnc2,
                recommendVolte = true, recommendVowifi = false, recommendVonr = false,
                recommendWfcMode = WfcMode.CELLULAR_PREFERRED,
                noteRes = R.string.carrier_note_cn_unknown,
            )
        }
        return CarrierProfile(
            nameRes = if (mcc.isBlank()) R.string.carrier_unknown else R.string.carrier_oversea,
            nameArg = if (mcc.isBlank()) null else mcc,
            recommendVolte = true, recommendVowifi = false, recommendVonr = false,
            recommendWfcMode = WfcMode.CELLULAR_PREFERRED,
            noteRes = R.string.carrier_note_oversea,
        )
    }
}
