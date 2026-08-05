package com.oneims.app.core

import android.content.Context
import android.util.Log
import com.oneims.app.R

/**
 * 一键临时 Root 成功后的配置收尾：
 * - 参考最小 Carrier XML（教程键集，受「持久化改运营商」开关约束，除非 [forceReferenceXml]）
 * - 用户自己的核心 + 高级选项（[ReapplyManager]，有重放源才写）
 *
 * 两者同一流程，避免只落「他的/参考」XML 而丢掉「我的」已存配置。
 */
object TempRootPostSuccessActions {
    private const val TAG = "OneIMS-TempRootPost"

    data class Result(
        val xmlAttempted: Boolean,
        val xmlOk: Boolean,
        val xmlMessage: String,
        val reapplyAttempted: Boolean,
        val reapplyOk: Boolean,
        val reapplyMessage: String,
    ) {
        fun summary(context: Context): String {
            val parts = mutableListOf<String>()
            if (xmlAttempted) {
                parts += when {
                    xmlOk && xmlMessage.startsWith("xml_already_ok") ->
                        context.getString(R.string.temp_root_post_xml_already)
                    xmlOk ->
                        context.getString(R.string.temp_root_post_xml_ok)
                    xmlMessage == "no_carrierconfig_xml" ->
                        context.getString(R.string.temp_root_post_xml_fail_no_file)
                    xmlMessage == "su_unavailable" || xmlMessage == "no_root" ->
                        context.getString(R.string.temp_root_post_xml_fail_no_root)
                    else ->
                        context.getString(R.string.temp_root_post_xml_fail)
                }
            } else {
                parts += context.getString(R.string.temp_root_post_xml_skipped)
            }
            if (reapplyAttempted) {
                parts += if (reapplyOk) {
                    context.getString(R.string.temp_root_post_reapply_ok)
                } else {
                    context.getString(R.string.temp_root_post_reapply_fail)
                }
            } else {
                parts += context.getString(R.string.temp_root_post_reapply_none)
            }
            return parts.joinToString("，")
        }
    }

    fun run(
        context: Context,
        displayCarrierName: String? = null,
        forceReferenceXml: Boolean = false,
    ): Result {
        val app = context.applicationContext
        val xml = when {
            forceReferenceXml || ConfigStore.isRootPersistEnhance(app) ->
                TempRootCarrierXmlPersist.applyMinimalNetwork(
                    context = app,
                    restartPhone = true,
                    displayCarrierName = displayCarrierName,
                )
            else ->
                TempRootCarrierXmlPersist.ApplyResult(
                    attempted = false,
                    success = false,
                    patchedCount = 0,
                    message = "switch_off",
                )
        }
        Log.i(TAG, "xml attempted=${xml.attempted} ok=${xml.success} msg=${xml.message}")

        val reapply = ReapplyManager.reapply(
            context = app,
            trigger = ReapplyTrigger.TEMP_ROOT,
            targetSubId = null,
        )
        val reapplyAttempted = reapply.message != app.getString(R.string.msg_no_history)
        Log.i(TAG, "reapply attempted=$reapplyAttempted ok=${reapply.success}")

        return Result(
            xmlAttempted = xml.attempted,
            xmlOk = xml.success,
            xmlMessage = xml.message,
            reapplyAttempted = reapplyAttempted,
            reapplyOk = reapply.success,
            reapplyMessage = reapply.message,
        )
    }
}
