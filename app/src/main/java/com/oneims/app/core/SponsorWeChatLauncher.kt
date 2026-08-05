package com.oneims.app.core

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.oneims.app.R

/**
 * 赞助页拉起微信。
 *
 * - 若 [R.string.sponsor_wechat_pay_url] 非空（`wxp://` / `https://` 等），优先直达该链。
 * - 否则只打开微信；个人赞赏码常无对外短链，由调用方落盘二维码作扫一扫兜底。
 */
object SponsorWeChatLauncher {
    private const val WECHAT_PACKAGE = "com.tencent.mm"

    fun isInstalled(context: Context): Boolean =
        runCatching {
            context.packageManager.getPackageInfo(WECHAT_PACKAGE, 0)
            true
        }.getOrDefault(false)

    fun configuredPayUrl(context: Context): String =
        context.getString(R.string.sponsor_wechat_pay_url).trim()

    /** @return true 已发出跳转 Intent */
    fun open(context: Context): Boolean {
        val payUrl = configuredPayUrl(context)
        val pm = context.packageManager
        val candidates = buildList {
            if (payUrl.isNotEmpty()) {
                val uri = Uri.parse(payUrl)
                add(Intent(Intent.ACTION_VIEW, uri).setPackage(WECHAT_PACKAGE))
                add(Intent(Intent.ACTION_VIEW, uri))
            }
            add(Intent(Intent.ACTION_VIEW, Uri.parse("weixin://")).setPackage(WECHAT_PACKAGE))
            add(pm.getLaunchIntentForPackage(WECHAT_PACKAGE))
        }
        for (base in candidates) {
            val intent = base ?: continue
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                // try next
            } catch (_: SecurityException) {
                // try next
            }
        }
        return false
    }
}
