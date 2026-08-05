package com.oneims.app.core

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 赞助页拉起微信。个人赞赏码通常解不出可直达收款页的 `wxp://`，
 * 因此以打开微信客户端为主；调用方宜先落盘二维码到相册作扫一扫兜底。
 */
object SponsorWeChatLauncher {
    private const val WECHAT_PACKAGE = "com.tencent.mm"

    fun isInstalled(context: Context): Boolean =
        runCatching {
            context.packageManager.getPackageInfo(WECHAT_PACKAGE, 0)
            true
        }.getOrDefault(false)

    /** @return true 已发出跳转 Intent */
    fun open(context: Context): Boolean {
        val pm = context.packageManager
        val candidates = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("weixin://")).setPackage(WECHAT_PACKAGE),
            pm.getLaunchIntentForPackage(WECHAT_PACKAGE),
        )
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
