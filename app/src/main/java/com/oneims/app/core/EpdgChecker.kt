package com.oneims.app.core

import android.content.Context
import com.oneims.app.R
import com.oneims.app.model.EpdgResult
import java.net.InetAddress

/**
 * ePDG（VoWiFi 网关）连通性自检。
 *
 * VoWiFi 实际经 ePDG 走 IKEv2/IPSec 隧道。即便 CarrierConfig 开关全开，能否真正建立
 * 还取决于运营商是否为该 SIM 放通 ePDG。本自检把这条边界可视化。
 *
 * ePDG FQDN 依 3GPP TS 23.003 标准：epdg.epc.mnc{MNC}.mcc{MCC}.pub.3gppnetwork.org
 * （MNC 不足 3 位左补 0）。UDP 500/4500 无连接握手难以百分百确认，故以「DNS 解析 + 主机可达性」作尽力探测。
 */
object EpdgChecker {

    fun buildFqdn(mcc: String, mnc: String): String? {
        if (mcc.length != 3 || mnc.isEmpty()) return null
        val mnc3 = mnc.padStart(3, '0')
        return "epdg.epc.mnc$mnc3.mcc$mcc.pub.3gppnetwork.org"
    }

    /** 阻塞式自检，请在 IO 线程调用。 */
    fun check(context: Context, mcc: String, mnc: String, timeoutMs: Int = 3000): EpdgResult {
        val host = buildFqdn(mcc, mnc)
            ?: return EpdgResult.Unavailable(context.getString(R.string.epdg_no_mccmnc))
        val addr: InetAddress = try {
            InetAddress.getByName(host)
        } catch (e: Exception) {
            return EpdgResult.DnsFail(host)
        }
        return try {
            if (addr.isReachable(timeoutMs)) {
                EpdgResult.Reachable(host, addr.hostAddress ?: "")
            } else {
                EpdgResult.PortUnreachable(host, addr.hostAddress ?: "")
            }
        } catch (e: Exception) {
            EpdgResult.PortUnreachable(host, addr.hostAddress ?: "")
        }
    }
}
